package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 7 contra MySQL 8 real (firma de sesión, sección 3.4; E3 y E18), después de la fase 2 del job mysql y con
 * CC_PRUEBA_MYSQL=true. Usa nombres únicos y no limpia.
 * <ul>
 *   <li>Una firma con el secreto de una sesión VENCIDA (vence_en pasado), CERRADA por un segundo ingreso o de OTRA persona
 *       no sirve.</li>
 *   <li>Una sesión cerrada no se reabre ni se alarga: ni con cc_app (1142) ni con cc_sistema (1143 o 1644).</li>
 *   <li>La firma de una directora no aprueba a nombre de otra.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class QaSprint7MySqlTest {

	private static final String AHORA = "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Autowired
	private SesionesFirmadas sesiones;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void unaFirmaConElSecretoDeUnaSesionVencidaNoSirve() throws Exception {
		Usuario directora = guardar("dir.vencida." + sufijo, Rol.DIRECTOR);
		byte[] bytes = new byte[32];
		new java.security.SecureRandom().nextBytes(bytes);
		String token = HexFormat.of().formatHex(bytes);
		String hash = sha256(token);
		// cc_sistema abre una sesión que vence en 2 segundos (la regla de la base: nace ahora, vence después).
		sistema().update("INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, abierta_en, vence_en, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, ?, " + AHORA + ", " + AHORA + " + INTERVAL 2 SECOND, NOW(6), "
				+ "?, NOW(6))", directora.getId(), hash, directora.getNombreUsuario());
		Long sesion = jdbc.queryForObject("SELECT id FROM sesion_usuario WHERE hash_token = ?", Long.class, hash);
		Thread.sleep(3_000);
		String clave = "verificador:vencida" + sufijo;
		String secreto = token;

		assertThat(codigoAl(() -> firmar(sesion, directora.getId(), clave, secreto))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class, clave)).isZero();
	}

	@Test
	void unaFirmaConLaSesionAnteriorAUnSegundoIngresoNoSirve() {
		Usuario directora = guardar("dir.otra." + sufijo, Rol.DIRECTOR);
		SesionAbierta primera = sesiones.abrir(1L, directora.getId(), "192.0.2.1");
		SesionAbierta segunda = sesiones.abrir(1L, directora.getId(), "192.0.2.2");

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario WHERE id = ?", String.class,
				primera.sesionId())).isEqualTo("OTRA_SESION");
		assertThat(codigoAl(() -> firmar(primera.sesionId(), directora.getId(), "verificador:p" + sufijo,
				primera.token()))).as("la del celular olvidado").isEqualTo(1644);
		assertThat(codigoAl(() -> firmar(segunda.sesionId(), directora.getId(), "verificador:s" + sufijo,
				segunda.token()))).as("la vigente").isNull();
	}

	@Test
	void unaSesionCerradaNoSeReabreNiSeAlarga() {
		Usuario directora = guardar("dir.cerrada." + sufijo, Rol.DIRECTOR);
		SesionAbierta sesion = sesiones.abrir(1L, directora.getId(), "192.0.2.3");
		sesiones.cerrar(sesion, MotivoCierreSesion.SALIO);

		assertThat(codigoAl(() -> jdbc.update("UPDATE sesion_usuario SET cerrada_en = NULL, motivo_cierre = NULL "
				+ "WHERE id = ?", sesion.sesionId()))).as("cc_app la reabre").isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("UPDATE sesion_usuario SET cerrada_en = NULL, motivo_cierre = NULL "
				+ "WHERE id = ?", sesion.sesionId()))).as("cc_sistema la reabre").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE sesion_usuario SET vence_en = vence_en + INTERVAL 1 DAY "
				+ "WHERE id = ?", sesion.sesionId()))).as("cc_sistema la alarga").isIn(1142, 1143);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM sesion_usuario WHERE id = ?", sesion.sesionId())))
				.as("cc_sistema la borra").isEqualTo(1142);
		assertThat(codigoAl(() -> firmar(sesion.sesionId(), directora.getId(), "verificador:c" + sufijo,
				sesion.token()))).isEqualTo(1644);
	}

	@Test
	void laFirmaDeUnaDirectoraNoApruebaANombreDeOtra() {
		Usuario promotora = guardar("promo.firma." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.firma." + sufijo, Rol.DIRECTOR);
		Usuario otraDirectora = guardar("dir2.firma." + sufijo, Rol.DIRECTOR);
		Usuario docente = guardar("doc.firma." + sufijo, Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(servicioUsuarios.cambiarRoles(docente.getId(), new CambiarRolesRequest(
				EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR), "Asume la direccion de secundaria"))).isTrue();
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' AND "
				+ "entidad_id = ? AND estado = 'PENDIENTE'", Long.class, docente.getId());
		SesionAbierta deLaDirectora = sesiones.abrir(1L, directora.getId(), "192.0.2.4");
		assertThat(codigoAl(() -> firmar(deLaDirectora.sesionId(), directora.getId(), "solicitud_cambio:" + solicitud
				+ ":APROBADA", deLaDirectora.token()))).as("la directora firma").isNull();

		assertThat(codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
				+ "resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?", otraDirectora.getNombreUsuario(), solicitud)))
				.as("a nombre de la otra directora").isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
	}

	@Test
	void conCcAppUnaPersonaNoCierraNiAbreLaSesionDeOtra() {
		Usuario directora = guardar("dir.ajena." + sufijo, Rol.DIRECTOR);
		SesionAbierta sesion = sesiones.abrir(1L, directora.getId(), "192.0.2.5");

		assertThat(codigoAl(() -> jdbc.update("UPDATE sesion_usuario SET cerrada_en = NOW(6), motivo_cierre = 'SALIO' "
				+ "WHERE id = ?", sesion.sesionId()))).isEqualTo(1142);
		assertThat(jdbc.queryForObject("SELECT cerrada_en FROM sesion_usuario WHERE id = ?", java.sql.Timestamp.class,
				sesion.sesionId())).as("sigue abierta").isNull();
	}

	private JdbcTemplate sistema() {
		assertThat(fuenteDatos).isInstanceOf(FuenteDatosEnrutada.class);
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private int firmar(Long sesionId, Long usuarioId, String clave, String token) {
		return jdbc.update("INSERT INTO firma_operacion (colegio_id, sesion_id, usuario_id, clave, token, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, NOW(6), 'qa', NOW(6))", sesionId, usuarioId, clave, token);
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private static String sha256(String texto) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
	}

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			return -1;
		}
	}
}
