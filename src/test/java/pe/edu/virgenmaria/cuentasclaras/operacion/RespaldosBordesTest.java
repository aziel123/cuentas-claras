package pe.edu.virgenmaria.cuentasclaras.operacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EstadoCadena;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.Latidos;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.AlertasRespaldo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 7 (respaldos, sección 9; E30, E32 y E34). El registro {@code respaldo} con sus estados (PRIMERO, IGUAL y
 * FALTAN_FILAS), lo que ve Promotoría en {@code /panel/sistema}, lo que recibe el vigilante en {@code /salud/respaldo} y
 * las reglas de la tabla (CHECK de V24) sobre H2. El reloj está en el viernes 2 de octubre de 2026, 08:00 en Lima.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RespaldosBordesTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private RespaldoRepository respaldos;

	@Autowired
	private Latidos latidos;

	@Autowired
	private ContadorErrores errores;

	@Autowired
	private EstadoCadena cadena;

	@Autowired
	private DataSource fuenteDatos;

	@Autowired
	private ObjectProvider<ScheduledTaskHolder> tareas;

	@Autowired
	private ObjectProvider<JavaMailSender> correo;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		jdbc.update("DELETE FROM respaldo");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		jdbc.update("DELETE FROM respaldo");
	}

	// ------------------------------------------------------------------ faltan filas

	/**
	 * QA-S7-1. Dado que el respaldo de las 02:30 encontró que faltan filas (un DBA borró un pago), cuando el mismo operador
	 * corre otro respaldo a las 02:45 (respaldar.sh compara contra el manifiesto que acaba de subir, que ya no tiene el
	 * pago, y registra IGUAL), entonces la alerta debe seguir: Promotoría no la vio y el vigilante debe seguir recibiendo
	 * REVISAR. Hoy el estado sale SOLO del último registro: un segundo respaldo borra la evidencia del panel, de
	 * «Para revisar» y del vigilante en minutos.
	 */
	@Disabled("QA-S7-1: un segundo respaldo con comparación IGUAL borra la alerta «Faltan filas» del panel y del vigilante")
	@Test
	void debeSeguirAvisandoFaltanFilasAunqueUnSegundoRespaldoDigaIgual() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		respaldo("cc-20261002-024500.sql.gz.age", "2026-10-02T02:46:00", "IGUAL", null);

		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("pago (faltan 1)")));
		assertThat(alertasParaPromotoria()).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("Faltan filas");
		});
	}

	@Test
	void debeAvisarFaltanFilasAPromotoriaYAlVigilanteCuandoElUltimoRespaldoLasEncontro() throws Exception {
		respaldo("cc-20261001-023000.sql.gz.age", "2026-10-01T02:31:00", "IGUAL", null);
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS",
				"ancla de la bitácora (evento 531), evento_auditoria (faltan 3)");

		mvc.perform(get("/salud/respaldo")).andExpect(status().isOk()).andExpect(content().string("REVISAR"));
		assertThat(alertasParaPromotoria()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("Faltan filas").doesNotContain("evento 531");
		});
	}

	@Test
	void debeMostrarPrimerRespaldoCuandoNoHayManifiestoAnterior() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "PRIMERO", null);

		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(content().string(containsString("Primer respaldo de este destino.")));
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
	}

	// ------------------------------------------------------------------ 26 horas

	@Test
	void debeDarAtrasadoUnRespaldoQueTerminoHaceExactamente26Horas() throws Exception {
		// Ahora: 02/10 08:00 en Lima. 26 horas antes: 01/10 06:00.
		respaldo("cc-20261001-055900.sql.gz.age", "2026-10-01T06:00:00", "IGUAL", null);

		mvc.perform(get("/salud/respaldo")).andExpect(content().string("ATRASADO"));
	}

	@Test
	void debeDarOkUnRespaldoQueTerminoHace25Horas59Minutos() throws Exception {
		respaldo("cc-20261001-055900.sql.gz.age", "2026-10-01T06:01:00", "IGUAL", null);

		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
		reloj.avanzar(Duration.ofMinutes(1));
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("ATRASADO"));
	}

	@Test
	void elVigilanteSoloRecibeOkAtrasadoORevisarSinFechasNiArchivos() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null);

		mvc.perform(get("/salud/respaldo")).andExpect(content().string(matchesPattern("^(OK|ATRASADO|REVISAR)$")))
				.andExpect(content().string("OK"))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("cc-2026"))));
	}

	// ------------------------------------------------------------------ destino simulado

	@Test
	void noDebeContarUnRespaldoSimuladoDondeNoSeAceptan() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null);
		EstadoTecnico comoEnProd = new EstadoTecnico(respaldos, propiedades(false), latidos, tareas, errores, cadena,
				fuenteDatos, correo, "", reloj);

		EstadoRespaldo estado = comoEnProd.respaldo();

		assertThat(estado.existe()).isFalse();
		assertThat(estado.paraVigilante()).isEqualTo("ATRASADO");
	}

	@Test
	void debeContarUnRespaldoRealAunqueDespuesHayaUnoSimulado() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null, "b2-colegio");
		respaldo("cc-20261002-030000.sql.gz.age", "2026-10-02T03:01:10", "FALTAN_FILAS", "pago (faltan 1)");
		EstadoTecnico comoEnProd = new EstadoTecnico(respaldos, propiedades(false), latidos, tareas, errores, cadena,
				fuenteDatos, correo, "", reloj);

		EstadoRespaldo estado = comoEnProd.respaldo();

		assertThat(estado.destino()).isEqualTo("b2-colegio");
		assertThat(estado.paraVigilante()).isEqualTo("OK");
	}

	// ------------------------------------------------------------------ la tabla (CHECK de V24)

	@Test
	void laBaseRechazaFaltanFilasSinLaListaDeDiferencias() {
		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaIgualConDiferencias() {
		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", "pago (1)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaUnaComparacionQueNoExiste() {
		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "OK", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaUnArchivoConOtroNombreOConRuta() {
		for (String archivo : List.of("respaldo.sql", "../cc-20261002-023000.sql.gz.age", "cc-20261002-0230.sql.gz.age",
				"cc-20261002-023000.sql.gz")) {
			assertThatThrownBy(() -> respaldo(archivo, "2026-10-02T02:31:10", "IGUAL", null)).as(archivo)
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Test
	void laBaseRechazaElMismoArchivoDosVeces() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null);

		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaAnclasQueRetroceden() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, "
				+ "secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, "
				+ "creado_en, creado_por) VALUES (TIMESTAMP '2026-10-02 02:30:00', TIMESTAMP '2026-10-02 02:31:00', "
				+ "'cc-20261002-023000.sql.gz.age', REPEAT('a', 64), 1024, '26', 10, REPEAT('0', 64), 9, REPEAT('0', 64), "
				+ "'{}', 'simulado', 'IGUAL', NULL, TIMESTAMP '2026-10-02 02:31:00', 'cc_respaldo')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaUnRegistroQueNoEsDeCcRespaldo() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, "
				+ "secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, "
				+ "creado_en, creado_por) VALUES (TIMESTAMP '2026-10-02 02:30:00', TIMESTAMP '2026-10-02 02:31:00', "
				+ "'cc-20261002-023000.sql.gz.age', REPEAT('a', 64), 1024, '26', 0, REPEAT('0', 64), 0, REPEAT('0', 64), "
				+ "'{}', 'simulado', 'IGUAL', NULL, TIMESTAMP '2026-10-02 02:31:00', 'cc_app')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseRechazaUnHashEnMayusculasOUnDestinoConCredenciales() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, "
				+ "secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, "
				+ "creado_en, creado_por) VALUES (TIMESTAMP '2026-10-02 02:30:00', TIMESTAMP '2026-10-02 02:31:00', "
				+ "'cc-20261002-023000.sql.gz.age', REPEAT('A', 64), 1024, '26', 0, REPEAT('0', 64), 0, REPEAT('0', 64), "
				+ "'{}', 'simulado', 'IGUAL', NULL, TIMESTAMP '2026-10-02 02:31:00', 'cc_respaldo')"))
				.as("sha256 en mayúsculas").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null,
				"s3://clave:secreta@bucket")).as("destino con credenciales").isInstanceOf(
						DataIntegrityViolationException.class);
	}

	@Test
	void elPanelDelSistemaNoLoVeNingunOtroRol() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		for (Rol rol : new Rol[] { Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO }) {
			mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("pago"))));
	}

	// ------------------------------------------------------------------ apoyo

	private List<AlertaRevision> alertasParaPromotoria() {
		EstadoTecnico estado = new EstadoTecnico(respaldos, propiedades(true), latidos, tareas, errores, cadena,
				fuenteDatos, correo, "", reloj);
		return new AlertasRespaldo(estado, propiedades(true)).alertas();
	}

	private static PropiedadesMonitoreo propiedades(boolean aceptaSimulado) {
		return new PropiedadesMonitoreo("", true, aceptaSimulado, 26, 5, Duration.ofMinutes(15), 0,
				Duration.ofMinutes(10), Duration.ofHours(1));
	}

	private void respaldo(String archivo, String fin, String comparacion, String diferencias) {
		respaldo(archivo, fin, comparacion, diferencias, "simulado");
	}

	private void respaldo(String archivo, String fin, String comparacion, String diferencias, String destino) {
		LocalDateTime termino = LocalDateTime.parse(fin);
		jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, "
				+ "hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, creado_en, "
				+ "creado_por) VALUES (?, ?, ?, REPEAT('a', 64), 1024, '26', 0, REPEAT('0', 64), 0, REPEAT('0', 64), "
				+ "'{\"pago\":[1,1]}', ?, ?, ?, ?, 'cc_respaldo')", termino.minusMinutes(1), termino, archivo, destino,
				comparacion, diferencias, termino);
	}
}
