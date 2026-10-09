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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillasMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 2 (sección 14, E1 a E18) contra MySQL 8 real: con la clave de {@code cc_app} (la que un empleado con
 * acceso al servidor podría sacar) no se crea una cuenta, no se dan roles, no se abre una sesión, no se firma como sistema
 * ni se aprueba a nombre de otra persona. Con la de {@code cc_sistema}, los triggers siguen frenando lo que no corresponde.
 * Corre después de la fase 2 del job mysql, con CC_PRUEBA_MYSQL=true. Usa nombres únicos y no limpia.
 * <p>
 * E7, E8 y E9 (objetos debilitados, privilegios de más, mismo usuario y TLS) los cubren el paso «M3» del CI y
 * {@code VerificadorPermisosBaseDatosTest}; E12, {@code AlertasCajaTest} y {@code DerivadorSecretoTest}.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint7MySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

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

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private SemillasMuestreo semillas;

	@Autowired
	private PlatformTransactionManager transacciones;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	/** La conexión de cc_sistema (los procesos y la identidad). */
	private JdbcTemplate sistema() {
		assertThat(fuenteDatos).isInstanceOf(FuenteDatosEnrutada.class);
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).as("cc_app y cc_sistema son usuarios distintos").isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	@Test
	void lasDosConexionesSonCcAppYCcSistema() {
		assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("cc_app@");
		assertThat(sistema().queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("cc_sistema@");
	}

	/** E1: con cc_app nadie escribe como un actor de sistema (patrón B y trg_evento_auditoria_actor). */
	@Test
	void e1ConCcAppNadieFirmaComoSistema() {
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO evento_auditoria (secuencia, ocurrido_en, nombre_usuario, "
				+ "accion, hash) VALUES (0, NOW(6), 'sistema.auditoria', 'HUELLA_ENVIADA', NULL)"))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
				+ "comprobante_id, medio, total, recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, 0, 0, 'sistema.pasarela', CURRENT_DATE, 0, 'YAPE', 1, 1, 0, 'PASARELA', ?, "
				+ "'VIGENTE', NOW(6), 'sistema.pasarela', NOW(6))", "e1-" + sufijo))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, origen, "
				+ "creado_en, creado_por, actualizado_en) VALUES (1, 0, 'CONFIRMADO', 'AUTOMATICA', NOW(6), "
				+ "'sistema.conciliacion', NOW(6))"))).isEqualTo(1644);
	}

	/** E2: con cc_app, aprobar a nombre de Dirección con un UPDATE directo (sin la firma de su sesión) falla. */
	@Test
	void e2AprobarANombreDeOtraPersonaSinFirmaFalla() {
		Usuario promotora = guardar("promo.e2." + sufijo, Rol.PROMOTOR);
		Usuario director = guardar("dir.e2." + sufijo, Rol.DIRECTOR);
		Usuario docente = guardar("doc.e2." + sufijo, Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(servicioUsuarios.cambiarRoles(docente.getId(), new CambiarRolesRequest(
				EnumSet.of(Rol.DOCENTE, Rol.PROMOTOR), "Socia nueva del colegio"))).isTrue();
		Long solicitud = solicitudDe(docente);

		assertThat(codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', resuelto_por = ?, "
				+ "resuelto_en = NOW(6) WHERE id = ?", director.getNombreUsuario(), solicitud))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
	}

	/**
	 * E3 y E18: una firma vale solo con el secreto de la sesión ABIERTA de esa persona y una sola vez. Al cerrar sesión (o
	 * a las 10 horas, vence_en), el secreto ya no firma. El secreto no queda en la base (el trigger lo borra).
	 */
	@Test
	void e3FirmaReusadaDeOtraPersonaODeSesionCerradaFalla() {
		Usuario directora = guardar("dir.e3." + sufijo, Rol.DIRECTOR);
		Usuario otra = guardar("adm.e3." + sufijo, Rol.ADMINISTRACION);
		SesionAbierta sesion = sesiones.abrir(1L, directora.getId(), "127.0.0.1");
		String clave = "verificador:e3" + sufijo;

		assertThat(codigoAl(() -> firmar(sesion.sesionId(), otra.getId(), clave, sesion.token())))
				.as("con la sesión de otra persona").isEqualTo(1644);
		assertThat(codigoAl(() -> firmar(sesion.sesionId(), directora.getId(), clave, "0".repeat(64))))
				.as("con un secreto inventado").isEqualTo(1644);
		assertThat(codigoAl(() -> firmar(sesion.sesionId(), directora.getId(), clave, sesion.token()))).as("la legítima")
				.isNull();
		assertThat(jdbc.queryForMap("SELECT token, firmada_bd FROM firma_operacion WHERE clave = ?", clave))
				.containsEntry("token", null).extractingByKey("firmada_bd").isNotNull();
		assertThat(codigoAl(() -> firmar(sesion.sesionId(), directora.getId(), clave, sesion.token())))
				.as("la misma clave otra vez").isEqualTo(1062);

		sesiones.cerrar(sesion, MotivoCierreSesion.SALIO);
		assertThat(codigoAl(() -> firmar(sesion.sesionId(), directora.getId(), clave + "b", sesion.token())))
				.as("E18: después de salir").isEqualTo(1644);
	}

	/** E4: con cc_app no se crea una cuenta, no se cambia una clave ni se dan roles (1142: sin GRANT). */
	@Test
	void e4CcAppNoCreaCuentasNiCambiaClaves() {
		Usuario directora = guardar("dir.e4." + sufijo, Rol.DIRECTOR);

		assertThat(codigoAl(() -> jdbc.update("UPDATE usuario SET clave_hash = 'x' WHERE id = ?", directora.getId())))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, "
				+ "clave_hash, debe_cambiar_clave, creado_en, creado_por, actualizado_en) VALUES (1, ?, 'Intrusa', 'x', TRUE, "
				+ "NOW(6), 'intrusa', NOW(6))", "intrusa." + sufijo))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'PROMOTOR')",
				directora.getId()))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", directora.getId())))
				.isEqualTo(1142);
	}

	/** E4 y E5: con cc_sistema, una cuenta nace con la clave por cambiar y una sesión, abierta ahora y de una cuenta activa. */
	@Test
	void e5CcAppNoAbreSesionesYCcSistemaSoloLasLegitimas() {
		Usuario caja = guardar("caja.e5." + sufijo, Rol.CAJA);
		String sesion = "INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, abierta_en, vence_en, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, REPEAT('a', 64), %s, %s, NOW(6), 'e5', NOW(6))";
		String ahora = "UTC_TIMESTAMP(6) - INTERVAL 5 HOUR";

		assertThat(codigoAl(() -> jdbc.update(String.format(sesion, ahora, ahora + " + INTERVAL 1 HOUR"), caja.getId())))
				.as("cc_app").isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update(String.format(sesion, ahora, ahora + " + INTERVAL 13 HOUR"),
				caja.getId()))).as("13 horas").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(String.format(sesion, ahora + " - INTERVAL 1 DAY", ahora),
				caja.getId()))).as("abierta ayer").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, "
				+ "clave_hash, debe_cambiar_clave, creado_en, creado_por, actualizado_en) VALUES (1, ?, 'Sin cambio', 'x', "
				+ "FALSE, NOW(6), 'e5', NOW(6))", "sin.cambio." + sufijo))).as("cuenta con clave definitiva").isEqualTo(1644);
		// La sesión de una cuenta desactivada no se abre.
		UsuariosDePrueba.iniciarSesion(guardar("promo.e5." + sufijo, Rol.PROMOTOR));
		servicioUsuarios.desactivar(caja.getId(), "Prueba de la sesion de una cuenta inactiva");
		assertThat(codigoAl(() -> sistema().update(String.format(sesion, ahora, ahora + " + INTERVAL 1 HOUR"),
				caja.getId()))).as("cuenta inactiva").isEqualTo(1644);
	}

	/** E6: Promotoría y Dirección solo con su solicitud aprobada y firmada; nunca CAJA con DIRECTOR. */
	@Test
	void e6RolesDeAprobadorSoloConSolicitudYSinCombinacionesProhibidas() {
		Usuario docente = guardar("doc.e6." + sufijo, Rol.DOCENTE);
		Usuario caja = guardar("caja.e6." + sufijo, Rol.CAJA);
		Usuario promotora = guardar("promo.e6." + sufijo, Rol.PROMOTOR);

		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'PROMOTOR')",
				docente.getId()))).as("PROMOTOR sin solicitud, aun con cc_sistema").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'DIRECTOR')",
				caja.getId()))).as("CAJA y DIRECTOR").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'ADMINISTRACION')",
				caja.getId()))).as("CAJA y ADMINISTRACION").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM usuario_rol WHERE usuario_id = ? AND rol = 'PROMOTOR'",
				promotora.getId()))).as("quitar PROMOTOR sin solicitud").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE usuario SET roles_solicitud_id = 999999999 WHERE id = ?",
				docente.getId()))).as("enlazar una solicitud que no es suya").isEqualTo(1644);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, docente.getId()))
				.containsExactly("DOCENTE");
	}

	/**
	 * El camino legítimo con los permisos mínimos: Promotoría crea la cuenta (por la ruta de identidad), pide Dirección
	 * para ella y otra persona la aprueba con la firma de su sesión; los triggers de identidad, roles y firmas lo aceptan.
	 */
	@Test
	void flujoIdentidadConPermisosMinimos() {
		Usuario promotora = guardar("promo.id." + sufijo, Rol.PROMOTOR);
		Usuario otraPromotora = guardar("promo2.id." + sufijo, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		String nombre = "nueva.dir." + sufijo;
		Long id = servicioUsuarios.crear(new CrearUsuarioRequest("Directora nueva", nombre, null,
				UsuariosDePrueba.celular(nombre).substring(3), Set.of(Rol.DOCENTE))).id();
		assertThat(jdbc.queryForMap("SELECT debe_cambiar_clave, creado_por FROM usuario WHERE id = ?", id))
				.containsEntry("debe_cambiar_clave", true).containsEntry("creado_por", promotora.getNombreUsuario());

		assertThat(servicioUsuarios.cambiarRoles(id, new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				"Asume la direccion del colegio"))).isTrue();
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, id);
		UsuariosDePrueba.iniciarSesion(otraPromotora);
		bandeja.aprobar(solicitud, "Confirmado en la reunion de directorio");

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ? ORDER BY rol", String.class, id))
				.containsExactly("DIRECTOR", "DOCENTE");
		assertThat(jdbc.queryForObject("SELECT roles_solicitud_id FROM usuario WHERE id = ?", Long.class, id))
				.isEqualTo(solicitud);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion f JOIN usuario u ON u.id = f.usuario_id "
				+ "WHERE f.clave = ? AND u.nombre_usuario = ?", Long.class, "solicitud_cambio:" + solicitud + ":APROBADA",
				otraPromotora.getNombreUsuario())).isEqualTo(1);
		// La solicitud ya aplicada no se reusa para dar Promotoría (no la incluye).
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'PROMOTOR')",
				id))).isEqualTo(1644);
	}

	/** E10 y E11: la muestra y la semilla las escribe solo el sistema, de esta semana o de hoy. */
	@Test
	void e10E11LaMuestraYLaSemillaSoloLasEscribeElSistema() {
		LocalDate hoy = LocalDate.now(LIMA);
		LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		String muestra = "INSERT INTO muestra_llamada (colegio_id, semana, familia_id, motivo, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, 1, 'EFECTIVO', NOW(6), ?, NOW(6))";
		String semilla = "INSERT INTO semilla_muestreo (colegio_id, ambito, fecha, semilla, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, ?, ?, 42, NOW(6), ?, NOW(6))";

		assertThat(codigoAl(() -> jdbc.update(muestra, lunes, "sistema.panel"))).as("E10 con cc_app").isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update(muestra, lunes, "promotora"))).as("E10, una persona como autora")
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(semilla, "CAJA", hoy, "sistema.muestreo"))).as("E11 con cc_app")
				.isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update(semilla, "LLAMADA_CONTROL", lunes.plusWeeks(1), "sistema.muestreo")))
				.as("E11, la del próximo lunes").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(semilla, "CAJA", hoy.plusDays(1), "sistema.muestreo")))
				.as("E11, la de mañana").isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(semilla, "CAJA", hoy, "promotora"))).as("E11, otra autora")
				.isEqualTo(1644);
	}

	/** E14 y E15: la foto del resumen, el envío al OSE y la entrega de los mensajes los escribe solo el sistema. */
	@Test
	void e14E15LaFotoElEnvioYLaEntregaSoloLasEscribeElSistema() {
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, "
				+ "pagos_cantidad, cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, "
				+ "cajas_sin_cerrar, cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, "
				+ "avisos_entregados, parametros, creado_en, creado_por, actualizado_en) VALUES (1, CURRENT_DATE + INTERVAL "
				+ "1 DAY, NOW(6), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '', NOW(6), 'sistema.panel', NOW(6))")))
				.as("E14").isEqualTo(1142);
		for (String sql : new String[] { "UPDATE comprobante SET estado_envio = 'ACEPTADO' WHERE 1 = 0",
				"UPDATE mensaje SET estado = 'ENTREGADO' WHERE 1 = 0", "UPDATE evento_pasarela SET estado = estado WHERE 1 = 0" }) {
			assertThat(codigoAl(() -> jdbc.update(sql))).as(sql).isIn(1142, 1143);
		}
	}

	/** E16: un correo externo que no es el configurado para ESTE colegio no recibe su huella. */
	@Test
	void e16LaHuellaVaSoloAlCorreoDeSuColegio() {
		assertThat(codigoAl(() -> sistema().update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
				+ "destino, plantilla, parametros, entidad, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
				+ "'HUELLA_BITACORA', 'CORREO', 'EXTERNO', 'contador@otro-colegio.pe', 'HUELLA_BITACORA', 'x', "
				+ "'huella_bitacora', 'PENDIENTE', NOW(6), 'sistema.auditoria', NOW(6))", "e16-" + sufijo)))
				.isEqualTo(1644);
	}

	/**
	 * E17 (H3): un proceso del sistema llamado DENTRO de la transacción de una persona no se une a ella: abre la suya con
	 * la conexión de cc_sistema (con la de cc_app, la semilla daría 1142).
	 */
	@Test
	void e17UnProcesoDentroDeLaTransaccionDeUnaPersonaUsaLaRutaDeSistema() {
		UsuariosDePrueba.iniciarSesion(guardar("promo.e17." + sufijo, Rol.PROMOTOR));
		LocalDate hoy = LocalDate.now(LIMA);

		Long semilla = ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).execute(t -> {
			assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("cc_app@");
			return semillas.de(SemillaMuestreo.Ambito.CAJA, hoy);
		}));

		assertThat(semilla).isNotNull();
		assertThat(jdbc.queryForObject("SELECT creado_por FROM semilla_muestreo WHERE colegio_id = 1 AND ambito = 'CAJA' "
				+ "AND fecha = ?", String.class, hoy)).isEqualTo("sistema.muestreo");
	}

	private int firmar(Long sesionId, Long usuarioId, String clave, String token) {
		return jdbc.update("INSERT INTO firma_operacion (colegio_id, sesion_id, usuario_id, clave, token, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, NOW(6), 'e3', NOW(6))", sesionId, usuarioId, clave, token);
	}

	private Long solicitudDe(Usuario titular) {
		return jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' AND entidad_id = ? "
				+ "AND estado = 'PENDIENTE'", Long.class, titular.getId());
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
