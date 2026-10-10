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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 7 contra MySQL 8 real (después de la fase 2 del job mysql, con CC_PRUEBA_MYSQL=true). Usa
 * nombres únicos y no limpia.
 * <ul>
 *   <li>Observación de QA: una cuenta de Dirección no se desactiva ni se reactiva sin SU solicitud ESTADO_CUENTA aprobada
 *       y firmada por otra persona (con cc_sistema, 1644); el camino legítimo pasa con los permisos mínimos.</li>
 *   <li>S7-A1: con cc_app no se inserta una solicitud ya APROBADA (1644) y una aprobada no se reusa para otra operación de
 *       dinero (la anulación de una cuota con la solicitud de otra).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class CorreccionesSprint7MySqlTest {

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
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private BandejaAprobaciones bandeja;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	/** Con cc_sistema, desactivar o reactivar una Dirección sin solicitud: 1644; con su solicitud aprobada y firmada, pasa. */
	@Test
	void flujoEstadoCuentaDeDireccionConPermisosMinimos() {
		Usuario promotora = guardar("promo.estado." + sufijo, Rol.PROMOTOR);
		Usuario otraPromotora = guardar("promo2.estado." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.estado." + sufijo, Rol.DIRECTOR);

		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET activo = FALSE, desactivado_en = NOW(6), "
				+ "desactivado_por = 'atacante' WHERE id = ?", directora.getId()))).as("sin solicitud").isEqualTo(1644);

		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(servicioUsuarios.desactivar(directora.getId(), "Dejó de trabajar en el colegio")).isTrue();
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'ESTADO_CUENTA' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, directora.getId());
		UsuariosDePrueba.iniciarSesion(otraPromotora);
		bandeja.aprobar(solicitud, "Confirmado en persona con la promotora");

		assertThat(jdbc.queryForMap("SELECT activo, estado_solicitud_id FROM usuario WHERE id = ?", directora.getId()))
				.containsEntry("activo", false).containsEntry("estado_solicitud_id", solicitud);
		// La solicitud ya usada no sirve para reactivarla (dice «desactivar» y no es más nueva): 1644.
		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET activo = TRUE, desactivado_en = NULL, "
				+ "desactivado_por = NULL WHERE id = ?", directora.getId()))).as("reactivar sin solicitud").isEqualTo(1644);
	}

	/**
	 * Hallado al reproducir el job mysql: pedir que una Promotoría deje de serlo cuenta las Promotorías activas con bloqueo
	 * (SELECT ... FOR UPDATE), que con cc_app daba 1142. Ahora el pedido va por la ruta de identidad y otra persona lo
	 * aprueba.
	 */
	@Test
	void quitarPromotoriaAUnaDeDosSePideYSeApruebaConPermisosMinimos() {
		Usuario promotora = guardar("promo.quita." + sufijo, Rol.PROMOTOR);
		Usuario otra = guardar("promo2.quita." + sufijo, Rol.PROMOTOR);
		Usuario directora = guardar("dir.quita." + sufijo, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(promotora);

		assertThat(servicioUsuarios.cambiarRoles(otra.getId(), new pe.edu.virgenmaria.cuentasclaras.seguridad.dto
				.CambiarRolesRequest(java.util.EnumSet.of(Rol.DOCENTE), "Deja la promotoria del colegio"))).isTrue();
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, otra.getId());
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitud, "Confirmado en persona con las dos promotoras");

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, otra.getId()))
				.containsExactly("DOCENTE");
	}

	/** S7-A1: una solicitud no nace APROBADA, ni con cc_app ni con cc_sistema. */
	@Test
	void unaSolicitudNoNaceAprobada() {
		String sql = "INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, "
				+ "pendiente, solicitado_por, resuelto_por, resuelto_en, creado_en, creado_por, actualizado_en) VALUES (1, "
				+ "'ANULACION_CUOTA', 'cuota', 1, 'x', '{}', 'reproduccion S7-A1', 'APROBADA', NULL, 'x.pide', 'y.aprueba', "
				+ "NOW(6), NOW(6), 'x.pide', NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(sql))).as("cc_app").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(sql))).as("cc_sistema").isEqualTo(1644);
	}

	/**
	 * S7-A1 (otras operaciones de dinero): una cuota se anula solo con SU solicitud aprobada y firmada; la solicitud
	 * aprobada de otra operación (aunque esté firmada) no sirve, ni una de otra cuota.
	 */
	@Test
	void unaCuotaNoSeAnulaConLaSolicitudAprobadaDeOtraOperacion() {
		Long cuota = jdbc.queryForObject("SELECT MIN(id) FROM cuota WHERE colegio_id = 1 AND estado = 'PENDIENTE'",
				Long.class);
		Long ajena = jdbc.queryForObject("SELECT MAX(s.id) FROM solicitud_cambio s WHERE s.colegio_id = 1 "
				+ "AND s.estado = 'APROBADA' AND NOT (s.tipo = 'ANULACION_CUOTA' AND s.entidad_id = ?)", Long.class, cuota);
		org.junit.jupiter.api.Assumptions.assumeTrue(cuota != null && ajena != null, "falta una cuota o una solicitud");
		String quien = jdbc.queryForObject("SELECT resuelto_por FROM solicitud_cambio WHERE id = ?", String.class, ajena);
		String pidio = jdbc.queryForObject("SELECT solicitado_por FROM solicitud_cambio WHERE id = ?", String.class, ajena);

		assertThat(codigoAl(() -> jdbc.update("UPDATE cuota SET estado = 'ANULADA', anulacion_motivo = 'reproduccion S7-A1', "
				+ "anulacion_solicitada_por = ?, anulacion_aprobada_por = ?, anulada_en = NOW(6), anulacion_solicitud_id = ?, "
				+ "actualizado_en = NOW(6) WHERE id = ?", pidio, quien, ajena, cuota))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, cuota)).isEqualTo("PENDIENTE");
	}

	private JdbcTemplate sistema() {
		assertThat(fuenteDatos).isInstanceOf(FuenteDatosEnrutada.class);
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).as("cc_app y cc_sistema son usuarios distintos").isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private Usuario guardar(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
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
