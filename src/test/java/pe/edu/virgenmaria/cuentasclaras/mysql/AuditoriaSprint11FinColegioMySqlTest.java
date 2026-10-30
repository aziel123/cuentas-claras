package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.DbaMySql;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.EntregaPublica;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.TablasExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.VerificadorSupresion;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.AplicacionCambiosEstado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.CambiosEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FinDeColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (secciones 3.9 a 3.11 y 3.17) contra MySQL 8 real, con los permisos de 02 y los triggers de 03: la
 * suspensión aplicada genera la exportación (sistema.exportacion), el titular la recibe, el cierre deja
 * {@code conservar_hasta}, el acta la proponen y aprueban dos operadores y el DBA corre {@code suprimir.sql}: borra y TACHA
 * solo lo de ese colegio (otro colegio queda igual fila por fila y la cadena sigue verificando). Y lo que la base rechaza
 * (1644 salvo aviso). Los plazos legales (30 días) se simulan como el DBA de las pruebas: moviendo la fecha efectiva de un
 * cambio antes de aplicarlo, o, para el plazo de supresión, el {@code aplicado_en} con su trigger retirado un momento y
 * vuelto a crear desde 03 (es lo único que el DBA no puede mover: AP2-1). Usa nombres únicos y no limpia.
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=120")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint11FinColegioMySqlTest {

	private static final String MOTIVO_ACTA = "Venció el plazo de conservación del colegio cerrado: se suprimen sus datos.";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private AltaColegio alta;

	@Autowired
	private CambiosEstadoColegio cambios;

	@Autowired
	private AplicacionCambiosEstado aplicacion;

	@Autowired
	private FinDeColegio fin;

	@Autowired
	private EntregaPublica entrega;

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private VerificadorSupresion verificadorSupresion;

	@Autowired
	private DerivadorSecreto derivador;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	// --------------------------------------------------------------------------------------------- el camino completo

	/** Del alta a la supresión: exportación, entrega, cierre, acta, suprimir.sql y la cadena que sigue verificando. */
	@Test
	void suprimirSqlDeUnColegioNoTocaAOtro() throws Exception {
		Colegio a = cerrado("20600000501", "987681001");
		Colegio b = suspendido("20600000502", "987681002");
		// conservarHastaExacto (sin autorización): el cierre + 30 días.
		assertThat(jdbc.queryForObject("SELECT conservar_hasta = DATE(cerrado_en) + INTERVAL 30 DAY AND cerrado_en = "
				+ "estado_desde FROM colegio WHERE id = ?", Boolean.class, a.id())).isTrue();

		envejecer(a.id(), 45);
		Long acta = proponerYAprobar(a.id());
		// actaSinCadenaVerificadaFalla: sin la verificación completa de la cadena, el DBA no la ejecuta.
		assertThat(codigoEjecutar(acta)).isEqualTo(1644);
		como(operadorB(), () -> fin.verificarCadena(a.id()));

		Map<String, String> antesB = huellaDeTablas(b.id());
		List<Map<String, Object>> eventosB = jdbc.queryForList("SELECT id, hash, nombre_usuario, detalle FROM evento_auditoria "
				+ "WHERE colegio_id = ? ORDER BY secuencia", b.id());
		long plataforma = EscenarioPlataforma.plataformaId(colegios);

		List<Integer> codigosDuranteLaEjecucion = new ArrayList<>();
		List<String> sentencias = sentencias(Files.readString(Path.of("scripts", "fin-de-colegio", "suprimir.sql"),
				StandardCharsets.UTF_8));
		DbaMySql.exigir().execute((ConnectionCallback<Void>) con -> {
			ejecutar(con, "SET @cc_acta = " + acta);
			for (String s : sentencias) {
				ejecutar(con, s);
				if (s.contains("'supresion en curso'")) {
					// tachadoDeEventoDePlataformaFalla: con el acta en ejecución, los eventos de la plataforma no se tachan.
					codigosDuranteLaEjecucion.add(codigo(con, "UPDATE evento_auditoria SET nombre_usuario = 'suprimido', "
							+ "usuario_id = NULL, roles = NULL, valor_anterior = NULL, valor_nuevo = NULL, detalle = NULL, "
							+ "ip = NULL WHERE colegio_id = " + plataforma + " ORDER BY secuencia DESC LIMIT 1"));
					// ...ni los de otro colegio,
					codigosDuranteLaEjecucion.add(codigo(con, "UPDATE evento_auditoria SET nombre_usuario = 'suprimido', "
							+ "detalle = NULL WHERE colegio_id = " + b.id() + " ORDER BY secuencia LIMIT 1"));
					// tachadoDeOtraColumnaFalla: ...y del propio colegio solo el tachado exacto (la acción no cambia).
					codigosDuranteLaEjecucion.add(codigo(con, "UPDATE evento_auditoria SET accion = 'USUARIO_CREADO' WHERE "
							+ "colegio_id = " + a.id() + " ORDER BY secuencia LIMIT 1"));
					// borradoDeBitacoraFalla: ni el DBA borra un evento, ni con el acta.
					codigosDuranteLaEjecucion.add(codigo(con, "DELETE FROM evento_auditoria WHERE colegio_id = " + a.id()
							+ " ORDER BY secuencia LIMIT 1"));
					// actaDeOtraConexionFalla: otra conexión del DBA con el mismo @cc_acta no tacha.
					codigosDuranteLaEjecucion.add(DbaMySql.exigir().execute((ConnectionCallback<Integer>) otra -> {
						ejecutar(otra, "SET @cc_acta = " + acta);
						return codigo(otra, "UPDATE evento_auditoria SET nombre_usuario = 'suprimido', usuario_id = NULL, "
								+ "roles = NULL, valor_anterior = NULL, valor_nuevo = NULL, detalle = NULL, ip = NULL "
								+ "WHERE colegio_id = " + a.id() + " ORDER BY secuencia LIMIT 1");
					}));
				}
			}
			return null;
		});
		assertThat(codigosDuranteLaEjecucion).containsExactly(1644, 1644, 1644, 1644, 1644);

		// El colegio A: SUPRIMIDO con la ficha mínima, el acta EJECUTADA con sus conteos y sin filas en el inventario.
		Map<String, Object> suprimido = jdbc.queryForMap("SELECT * FROM colegio WHERE id = ?", a.id());
		assertThat(suprimido).containsEntry("estado", "SUPRIMIDO").containsEntry("acta_supresion_id", acta);
		assertThat(suprimido.get("titular_documento")).isNull();
		assertThat(suprimido.get("titular_nombre")).isNull();
		assertThat(suprimido.get("correo_institucional")).isNull();
		assertThat(suprimido.get("nombre")).isNotNull();
		assertThat((String) suprimido.get("ruc")).startsWith("2060000050");
		Map<String, Object> ejecutada = jdbc.queryForMap("SELECT * FROM acta_supresion WHERE id = ?", acta);
		assertThat(ejecutada).containsEntry("estado", "EJECUTADA");
		assertThat((String) ejecutada.get("filas_suprimidas")).contains("\"alumno\"").contains("\"usuario\"");
		// huellaEventosLaCalculaLaBase: la huella encadenada de los eventos del colegio, desde 64 ceros, por secuencia.
		String h = VerificadorIntegridadAuditoria.HASH_INICIAL;
		for (Map<String, Object> e : jdbc.queryForList("SELECT id, hash FROM evento_auditoria WHERE colegio_id = ? AND "
				+ "secuencia <= ? ORDER BY secuencia", a.id(), ejecutada.get("ultima_secuencia"))) {
			h = Sha256.hex(h + "|" + e.get("id") + "|" + e.get("hash"));
		}
		assertThat(ejecutada.get("huella_eventos")).isEqualTo(h);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND secuencia <= ? AND "
				+ "(nombre_usuario <> 'suprimido' OR detalle IS NOT NULL)", Long.class, a.id(), ejecutada.get("ultima_secuencia")))
				.isZero();
		for (TablasExportacion.Tabla t : TablasExportacion.cargar()) {
			if (List.of("evento_auditoria", "huella_bitacora", "huella_hora", "cambio_estado_colegio").contains(t.nombre())) {
				continue;
			}
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + t.nombre() + " WHERE " + t.where(), Long.class, a.id()))
					.as(t.nombre()).isZero();
		}
		// El colegio B quedó igual, fila por fila, y sus eventos también.
		assertThat(huellaDeTablas(b.id())).isEqualTo(antesB);
		assertThat(jdbc.queryForList("SELECT id, hash, nombre_usuario, detalle FROM evento_auditoria WHERE colegio_id = ? "
				+ "AND id <= ? ORDER BY secuencia", b.id(), eventosB.getLast().get("id"))).isEqualTo(eventosB);
		// La cadena de todos sigue verificando (con los tachados del acta) y el arranque no encuentra nada.
		ResultadoVerificacion resultado = EjecucionComoSistema.como(ActorSistema.AUDITORIA, b.id(),
				verificador::verificarComoSistema);
		assertThat(resultado.integra()).as(resultado.detalle()).isTrue();
		assertThat(verificadorSupresion.problemas()).isEmpty();
		// Un acta ejecutada no vuelve atrás, ni con el DBA; el colegio suprimido tampoco cambia su ficha mínima.
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE acta_supresion SET estado = 'EJECUTANDO' WHERE id = ?",
				acta))).isEqualTo(1644);
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE colegio SET titular_documento = '45678900' WHERE id = ?",
				a.id()))).isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------- la exportación y el cierre

	@Test
	void exportacionHuellaNoCambiaYEntregadaSinDescargaFalla() {
		Colegio c = suspendido("20600000503", "987681003");
		Long exportacion = exportacion(c.id());
		assertThat(jdbc.queryForObject("SELECT estado FROM exportacion_colegio WHERE id = ?", String.class, exportacion))
				.isEqualTo("LISTA");
		// cc_app no escribe la exportación (1142); cc_sistema no cambia sus huellas (1644); el DBA tampoco.
		assertThat(codigoAl(() -> jdbc.update("UPDATE exportacion_colegio SET sha256_zip = REPEAT('a', 64) WHERE id = ?",
				exportacion))).isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("UPDATE exportacion_colegio SET sha256_zip = REPEAT('a', 64) WHERE id = ?",
				exportacion))).isEqualTo(1644);
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE exportacion_colegio SET sha256_manifiesto = REPEAT('a', 64) "
				+ "WHERE id = ?", exportacion))).isEqualTo(1644);
		// entregadaSinDescargaFalla (AP2-13).
		assertThat(codigoAl(() -> sistema().update("UPDATE exportacion_colegio SET estado = 'ENTREGADA' WHERE id = ?",
				exportacion))).isEqualTo(1644);
		// cierreSinExportacionFalla / cierreConExportacionAnteriorFalla: LISTA de hoy no habilita el cierre.
		como(operadorA(), () -> assertThat(codigoAl(() -> cambios.proponer(c.id(), TipoCambioEstado.CIERRE,
				MotivoCambioEstado.FALTA_PAGO, "El colegio no pagó y recibió su exportación: se cierra.", "Carta 2027-1")))
				.isNotZero());
		assertThat(codigoAl(() -> sistema().update(insertarCierre(c.id(), null)))).isIn(1644, 3819);
		// cierreConExportacionDeOtroColegioFalla.
		Colegio otro = suspendido("20600000504", "987681004");
		assertThat(codigoAl(() -> sistema().update(insertarCierre(c.id(), exportacion(otro.id()))))).isIn(1644, 1452);
	}

	@Test
	void descargaCuartaFallaYConservarHastaManipuladoFalla() {
		Colegio c = cerrado("20600000505", "987681005");
		Long exportacion = exportacion(c.id());
		assertThat(jdbc.queryForObject("SELECT descargas FROM exportacion_colegio WHERE id = ?", Integer.class, exportacion))
				.isEqualTo(1);
		String token = token(c.id());
		String codigo = codigo(c.id());
		entrega.descargar(token, codigo, "203.0.113.9");
		entrega.descargar(token, codigo, "203.0.113.9");
		// descargaCuartaFalla: la cuarta, ni por la aplicación ni a mano (CHECK del número o trigger).
		assertThat(codigoAl(() -> sistema().update("INSERT INTO descarga_exportacion (colegio_id, exportacion_id, numero, ip, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, 4, '203.0.113.9', NOW(6), 'sistema.exportacion', "
				+ "NOW(6))", c.id(), exportacion))).isIn(3819, 1644);
		// conservarHastaManipuladoFalla: columnas generadas: cc_app y cc_sistema sin GRANT (1143), el DBA 3105.
		for (String columna : List.of("conservar_hasta", "cerrado_en")) {
			String sql = "UPDATE colegio SET " + columna + " = NULL WHERE id = ?";
			assertThat(codigoAl(() -> jdbc.update(sql, c.id()))).as("cc_app " + columna).isEqualTo(1143);
			assertThat(codigoAl(() -> sistema().update(sql, c.id()))).as("cc_sistema " + columna).isEqualTo(1143);
			assertThat(codigoAl(() -> DbaMySql.exigir().update(sql, c.id()))).as("DBA " + columna).isEqualTo(3105);
		}
		// suspendidoEnComoDbaFalla (AP2-1): ni las fechas que cuentan el plazo.
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE colegio SET suspendido_en = suspendido_en - INTERVAL 40 DAY "
				+ "WHERE id = ?", c.id()))).isEqualTo(1644);
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE colegio SET estado_desde = estado_desde - INTERVAL 40 DAY "
				+ "WHERE id = ?", c.id()))).isEqualTo(1644);
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE cambio_estado_colegio SET aplicado_en = aplicado_en - "
				+ "INTERVAL 40 DAY WHERE colegio_id = ? AND estado = 'APLICADO'", c.id()))).isEqualTo(1644);
		// sesionEnCerradoFalla: el personal no abre sesión en CERRADO (la familia, solo hasta conservar_hasta: H2).
		assertThat(sesion(c.id(), c.titular().getId())).isEqualTo(1644);
	}

	/**
	 * QA del sprint 11 (QA-S11-01): el enlace nuevo que manda el operador tras 5 intentos fallidos empieza con 5 intentos
	 * (antes nacía con 0: el trigger no dejaba bajar el contador). Sin un enlace nuevo, cc_sistema no baja los intentos.
	 */
	@Test
	void entregaNuevaTrasCincoIntentosEmpiezaEnCeroYSinEnlaceNuevoNoBaja() {
		Colegio c = suspendido("20600000507", "987681007");
		Long exportacion = exportacion(c.id());
		String token = token(c.id());
		for (int i = 0; i < 5; i++) {
			assertThat(codigoAl(() -> entrega.confirmarDni(token, "11111111"))).isEqualTo(-2);
		}
		assertThat(intentos(exportacion)).isEqualTo(5);
		String bajar = "UPDATE exportacion_colegio SET intentos_fallidos = 0 WHERE id = ?";
		assertThat(codigoAl(() -> sistema().update(bajar, exportacion))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("UPDATE exportacion_colegio SET intentos_fallidos = 0, codigo_hash = "
				+ "REPEAT('c', 64), token_hash = REPEAT('b', 64) WHERE id = ?", exportacion))).isEqualTo(1644);

		como(operadorA(), () -> fin.reenviarEntrega(c.id(), exportacion));
		assertThat(intentos(exportacion)).isZero();
		String nuevo = token(c.id());
		assertThat(entrega.ver(nuevo).intentosRestantes()).isEqualTo(5);
		assertThat(codigoAl(() -> entrega.confirmarDni(nuevo, "11111111"))).isEqualTo(-2);
		assertThat(intentos(exportacion)).isEqualTo(1);
		// Con el mismo enlace, los intentos no vuelven a 0.
		assertThat(codigoAl(() -> sistema().update(bajar, exportacion))).isEqualTo(1644);
		entrega.confirmarDni(nuevo, c.dni());
		assertThat(entrega.confirmarCodigo(nuevo, codigo(c.id()))).isNotBlank();
	}

	private int intentos(Long exportacion) {
		return jdbc.queryForObject("SELECT intentos_fallidos FROM exportacion_colegio WHERE id = ?", Integer.class,
				exportacion);
	}

	// ---------------------------------------------------------------------------------------------------- el acta

	@Test
	void elActaSeRechazaFueraDeSuCamino() {
		Colegio c = cerrado("20600000506", "987681006");
		Long acta = como(operadorA(), () -> fin.proponerActa(c.id(), MOTIVO_ACTA, LocalDate.now(
				Clock.system(ConfiguracionTiempo.ZONA_LIMA)).plusMonths(12)));
		Map<String, Object> a = jdbc.queryForMap("SELECT * FROM acta_supresion WHERE id = ?", acta);
		// actaAprobadaPorQuienLaPropusoFalla.
		assertThat(codigoAl(() -> sistema().update("UPDATE acta_supresion SET estado = 'APROBADA', aprobado_por = "
				+ "propuesto_por, aprobador_id = operador_id, aprobado_en = NOW(6) WHERE id = ?", acta))).isIn(1644, 3819);
		// actaConRetencionLegalFalla: con la retención legal, ni aprobar.
		como(operadorA(), () -> fin.ponerRetencion(c.id(), "Oficio 245-2027 del Juzgado Civil de Lima: conservar."));
		Usuario b = operadorB();
		assertThat(codigoAl(() -> sistema().update("UPDATE acta_supresion SET estado = 'APROBADA', aprobado_por = ?, "
				+ "aprobador_id = ?, aprobado_en = NOW(6) WHERE id = ?", b.getNombreUsuario(), b.getId(), acta)))
				.isEqualTo(1644);
		como(b, () -> fin.quitarRetencion(c.id()));
		como(b, () -> fin.aprobarActa(c.id(), acta));
		como(b, () -> fin.verificarCadena(c.id()));
		// supresionConPlazoSinVencerFalla: con la cadena verificada pero sin el plazo vencido, el DBA no la ejecuta.
		assertThat(codigoEjecutar(acta)).isEqualTo(1644);
		// tachadoSinActaFalla: sin acta en ejecución no se tacha nada.
		assertThat(codigoAl(() -> DbaMySql.exigir().update("UPDATE evento_auditoria SET nombre_usuario = 'suprimido', "
				+ "detalle = NULL WHERE colegio_id = ? ORDER BY secuencia LIMIT 1", c.id()))).isEqualTo(1644);
		// borradoDeBitacoraFalla: cc_app sin GRANT de DELETE (1142); el DBA, el trigger (1644).
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM evento_auditoria WHERE colegio_id = ? ORDER BY secuencia LIMIT 1",
				c.id()))).isEqualTo(1142);
		assertThat(codigoAl(() -> DbaMySql.exigir().update("DELETE FROM evento_auditoria WHERE colegio_id = ? ORDER BY "
				+ "secuencia LIMIT 1", c.id()))).isEqualTo(1644);
		// respaldosMasDe24MesesFalla: el CHECK de 24 meses (o el trigger, que exige la firma antes).
		assertThat(codigoAl(() -> sistema().update("INSERT INTO acta_supresion (colegio_id, plataforma_id, tipo, estado, motivo, "
				+ "propuesto_por, operador_id, inicio_plazo, respaldos_vencen_el, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, ?, 'CIERRE', 'PROPUESTA', ?, ?, ?, NOW(6), CURDATE() + INTERVAL 30 MONTH, NOW(6), ?, NOW(6))",
				c.id(), a.get("plataforma_id"), MOTIVO_ACTA, a.get("propuesto_por"), a.get("operador_id"),
				a.get("propuesto_por")))).isIn(3819, 1644, 1062);
	}

	// ------------------------------------------------------------------------------------------ la suspensión (1c)

	/** f1SuspensionAntesDe30DiasFalla: la espera de 30 días la pone la base; antes, ni sistema.plataforma la aplica. */
	@Test
	void f1SuspensionAntesDe30DiasFalla() {
		Colegio c = enProduccion("20600000507", "987681007");
		Long id = suspensionAprobada(c.id());
		assertThat(aplicacion.enColegio(c.id())).isZero();
		assertThat(codigoAl(() -> sistema().update("UPDATE colegio SET estado = 'SUSPENDIDO', cambio_estado_id = ? WHERE id = ?",
				id, c.id()))).isEqualTo(1644);
		java.sql.Timestamp efectiva = jdbc.queryForObject("SELECT efectiva_desde FROM cambio_estado_colegio WHERE id = ?",
				java.sql.Timestamp.class, id);
		assertThat(efectiva.toLocalDateTime()).isAfter(LocalDateTime.now(Clock.system(ConfiguracionTiempo.ZONA_LIMA))
				.plusDays(29));
	}

	/** a4SuspensionConCierrePorRevisarFalla, f10 y a5: la base exige la caja revisada; el personal no abre sesión. */
	@Test
	void laSuspensionAplicadaEnMySql() {
		Colegio c = suspendido("20600000508", "987681008");
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, c.id())).isEqualTo("SUSPENDIDO");
		// f10SesionDelPersonalEnSuspendidoFalla.
		assertThat(sesion(c.id(), c.titular().getId())).isEqualTo(1644);
		// La exportación se generó al aplicarse (sistema.exportacion) y su huella llegó al correo de control.
		Long exportacion = exportacion(c.id());
		assertThat(jdbc.queryForObject("SELECT creado_por FROM exportacion_colegio WHERE id = ?", String.class, exportacion))
				.isEqualTo("sistema.exportacion");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND plantilla = 'EXPORTACION_LISTA' "
				+ "AND destinatario_tipo = 'EXTERNO'", Long.class, c.id())).isPositive();
	}

	/** La sesión de esa cuenta, como la abriría cc_sistema al ingresar (0 si la base la deja nacer). */
	private int sesion(long colegio, long usuario) {
		LocalDateTime ahora = DbaMySql.exigir().queryForObject("SELECT cc_ahora_lima_real()", java.sql.Timestamp.class)
				.toLocalDateTime();
		return codigoAl(() -> sistema().update("INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, abierta_en, "
				+ "vence_en, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, NOW(6), 'sistema.identidad', NOW(6))",
				colegio, usuario, Sha256.hex(sufijo + colegio + System.nanoTime()), ahora, ahora.plusHours(1)));
	}

	// ------------------------------------------------------------------------------------------------------ apoyo

	private record Colegio(long id, Usuario titular, String dni) {
	}

	private Colegio enProduccion(String rucBase, String celular) {
		// El dígito verificador del RUC (módulo 11 de SUNAT), sobre los 10 primeros dígitos.
		String ruc = java.util.stream.IntStream.rangeClosed(0, 9).mapToObj(d -> rucBase.substring(0, 10) + d)
				.filter(pe.edu.virgenmaria.cuentasclaras.plataforma.service.ValidadorRuc::valido).findFirst().orElseThrow();
		ingresar(operadorA());
		String dni = "4" + celular.substring(2);
		String titular = "t" + celular.substring(5) + "." + sufijo;
		ColegioCreado creado = alta.crear(new AltaColegioRequest("Colegio S11f " + sufijo + " " + celular,
				"Asociación S11f " + celular + " S.A.C.", ruc, "Av. Los Educadores 123, Lima", "contacto." + celular
						+ "@colegio.edu.pe", EnumSet.of(Nivel.PRIMARIA), "CT-" + celular, "Titular " + celular, dni,
				titular, celular, titular.replace('.', '-') + "@correo.pe"), null, null);
		alta.registrarCorreoControl(creado.colegioId(), "contador." + sufijo + "." + celular + "@estudio.pe");
		SecurityContextHolder.clearContext();
		Usuario t = cuenta(creado.colegioId(), creado.titularUsuario());
		DbaMySql.exigir().update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = NOW(6), produccion_por = ? "
				+ "WHERE id = ?", t.getNombreUsuario(), creado.colegioId());
		return new Colegio(creado.colegioId(), t, dni);
	}

	private Long suspensionAprobada(long colegio) {
		Long id = como(operadorA(), () -> cambios.proponer(colegio, TipoCambioEstado.SUSPENSION, MotivoCambioEstado.FALTA_PAGO,
				"Tres meses sin pagar la suscripción, con aviso escrito.", "Carta notarial 2027-" + colegio));
		como(operadorB(), () -> cambios.aprobar(colegio, id));
		return id;
	}

	/** Suspendido por dos operadores, con la fecha efectiva movida por el DBA; la exportación queda LISTA. */
	private Colegio suspendido(String ruc, String celular) {
		Colegio c = enProduccion(ruc, celular);
		Long id = suspensionAprobada(c.id());
		DbaMySql.exigir().update("UPDATE cambio_estado_colegio SET efectiva_desde = cc_ahora_lima_real() - INTERVAL 1 MINUTE WHERE id = ?",
				id);
		assertThat(aplicacion.enColegio(c.id())).isEqualTo(1);
		return c;
	}

	/** Suspendido, la exportación entregada al titular (DNI, código y una descarga) y cerrado por dos operadores. */
	private Colegio cerrado(String ruc, String celular) {
		Colegio c = suspendido(ruc, celular);
		String token = token(c.id());
		entrega.confirmarDni(token, c.dni());
		String codigo = codigo(c.id());
		assertThat(entrega.confirmarCodigo(token, codigo)).isNotBlank();
		entrega.descargar(token, codigo, "203.0.113.9");
		Long cierre = como(operadorA(), () -> cambios.proponer(c.id(), TipoCambioEstado.CIERRE, MotivoCambioEstado.FALTA_PAGO,
				"El colegio no pagó y recibió su exportación: se cierra.", "Carta 2027-" + c.id()));
		como(operadorB(), () -> cambios.aprobar(c.id(), cierre));
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, c.id())).isEqualTo("CERRADO");
		return c;
	}

	private Long proponerYAprobar(long colegio) {
		Long acta = como(operadorA(), () -> fin.proponerActa(colegio, MOTIVO_ACTA, LocalDate.now(
				Clock.system(ConfiguracionTiempo.ZONA_LIMA)).plusMonths(12)));
		como(operadorB(), () -> fin.aprobarActa(colegio, acta));
		return acta;
	}

	/**
	 * Como el DBA de las pruebas: los cambios aplicados del colegio, {@code dias} atrás. El trigger que lo impide (AP2-1) se
	 * retira un momento y se vuelve a crear con su texto de 03 (su huella no cambia).
	 */
	private void envejecer(long colegio, int dias) throws Exception {
		String cuerpo = trigger("trg_cambio_estado_colegio_estado");
		JdbcTemplate dba = DbaMySql.exigir();
		dba.execute("DROP TRIGGER IF EXISTS trg_cambio_estado_colegio_estado");
		try {
			dba.update("UPDATE cambio_estado_colegio SET aplicado_en = aplicado_en - INTERVAL " + dias + " DAY WHERE "
					+ "colegio_id = ? AND estado = 'APLICADO'", colegio);
		}
		finally {
			dba.execute(cuerpo);
		}
	}

	private static String trigger(String nombre) throws Exception {
		String texto = Files.readString(Path.of("scripts", "mysql", "03-triggers.sql"), StandardCharsets.UTF_8);
		int desde = texto.indexOf("CREATE TRIGGER " + nombre + " ");
		int hasta = texto.indexOf("END$$", desde);
		return texto.substring(desde, hasta + 3);
	}

	/** El código de error al pasar el acta a EJECUTANDO como el DBA (0 si pasó). */
	private int codigoEjecutar(Long acta) {
		return DbaMySql.exigir().execute((ConnectionCallback<Integer>) con -> {
			ejecutar(con, "SET @cc_acta = " + acta);
			return codigo(con, "UPDATE acta_supresion SET estado = 'EJECUTANDO', ejecutado_por = "
					+ "SUBSTRING_INDEX(SESSION_USER(), '@', 1) WHERE id = " + acta + " AND estado = 'APROBADA'");
		});
	}

	/** Las sentencias de suprimir.sql, sin los comentarios. */
	private static List<String> sentencias(String sql) {
		StringBuilder limpio = new StringBuilder();
		for (String linea : sql.split("\\R")) {
			if (!linea.strip().startsWith("--")) {
				limpio.append(linea).append('\n');
			}
		}
		List<String> sentencias = new ArrayList<>();
		for (String s : limpio.toString().split(";\\s*\\n")) {
			if (!s.isBlank()) {
				sentencias.add(s.strip());
			}
		}
		return sentencias;
	}

	private static void ejecutar(Connection con, String sql) throws SQLException {
		try (Statement st = con.createStatement()) {
			st.execute(sql);
		}
	}

	private static int codigo(Connection con, String sql) {
		try (Statement st = con.createStatement()) {
			st.executeUpdate(sql);
			return 0;
		}
		catch (SQLException e) {
			return e.getErrorCode();
		}
	}

	/** Por tabla de la exportación: cuántas filas del colegio y la suma de sus ids (para «quedó igual fila por fila»). */
	private Map<String, String> huellaDeTablas(long colegio) {
		Map<String, String> huellas = new LinkedHashMap<>();
		for (TablasExportacion.Tabla t : TablasExportacion.cargar()) {
			List<String> columnas = jdbc.query("SELECT * FROM " + t.nombre() + " WHERE 1 = 0", r -> {
				List<String> c = new ArrayList<>();
				for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
					c.add(r.getMetaData().getColumnLabel(i));
				}
				return c;
			});
			String filas = "COUNT(*), " + (columnas.contains("id") ? "COALESCE(SUM(id), 0)" : "0") + ", "
					+ (columnas.contains("actualizado_en") ? "COALESCE(MAX(actualizado_en), 0)" : "0")
					+ (columnas.contains("hash") ? ", COALESCE(MAX(hash), '')" : "");
			huellas.put(t.nombre(), jdbc.queryForObject("SELECT CONCAT_WS('|', " + filas + ") FROM " + t.nombre() + " WHERE "
					+ t.where(), String.class, colegio));
		}
		return huellas;
	}

	private String insertarCierre(long colegio, Long exportacion) {
		Usuario a = operadorA();
		return "INSERT INTO cambio_estado_colegio (colegio_id, plataforma_id, tipo, ambito, motivo_tipo, motivo, "
				+ "referencia_aviso, estado_origen, estado_destino, estado, pendiente, propuesto_por, operador_id, exportacion_id, "
				+ "creado_en, creado_por, actualizado_en) VALUES (" + colegio + ", " + a.getColegioId() + ", 'CIERRE', 'ESTADO', "
				+ "'FALTA_PAGO', 'El colegio no pagó y recibió su exportación: se cierra.', 'Carta 2027-9', 'SUSPENDIDO', "
				+ "'CERRADO', 'PROPUESTO', TRUE, '" + a.getNombreUsuario() + "', " + a.getId() + ", " + exportacion
				+ ", NOW(6), '" + a.getNombreUsuario() + "', NOW(6))";
	}

	private Long exportacion(long colegio) {
		return jdbc.queryForObject("SELECT MAX(id) FROM exportacion_colegio WHERE colegio_id = ?", Long.class, colegio);
	}

	private String token(long colegio) {
		String clave = jdbc.queryForObject("SELECT clave FROM mensaje WHERE colegio_id = ? AND tipo = 'EXPORTACION_ENLACE' "
				+ "ORDER BY id DESC LIMIT 1", String.class, colegio);
		return derivador.tokenExportacion(colegio, clave);
	}

	private String codigo(long colegio) {
		String clave = jdbc.queryForObject("SELECT clave FROM mensaje WHERE colegio_id = ? AND tipo = 'CODIGO_EXPORTACION' "
				+ "ORDER BY id DESC LIMIT 1", String.class, colegio);
		return derivador.codigoExportacion(colegio, clave);
	}

	private Usuario operadorA() {
		return EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s11f.a");
	}

	private Usuario operadorB() {
		return EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.s11f.b");
	}

	private void ingresar(Usuario usuario) {
		UsuariosDePrueba.iniciarSesion(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
	}

	private <T> T como(Usuario quien, java.util.function.Supplier<T> accion) {
		ingresar(quien);
		try {
			return accion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void como(Usuario quien, Runnable accion) {
		como(quien, () -> {
			accion.run();
			return null;
		});
	}

	private Usuario cuenta(long colegio, String nombre) {
		Usuario u = ContextoColegio.en(colegio, () -> usuarios.findByNombreUsuario(nombre)).orElseThrow();
		RutaConexion.identidad(() -> ContextoColegio.en(colegio, () -> {
			Usuario x = usuarios.findById(u.getId()).orElseThrow();
			x.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), LocalDateTime.now(Clock.system(
					ConfiguracionTiempo.ZONA_LIMA)), false);
			return usuarios.saveAndFlush(x);
		}));
		return ContextoColegio.en(colegio, () -> usuarios.findById(u.getId())).orElseThrow();
	}

	private JdbcTemplate sistema() {
		return new JdbcTemplate(((FuenteDatosEnrutada) fuenteDatos).sistema());
	}

	private static int codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return 0;
		}
		catch (DataAccessException e) {
			Throwable causa = e;
			while (causa != null && !(causa instanceof SQLException)) {
				causa = causa.getCause();
			}
			return causa instanceof SQLException sql ? sql.getErrorCode() : -1;
		}
		catch (RuntimeException e) {
			return -2;
		}
	}
}
