package pe.edu.virgenmaria.cuentasclaras.familias.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;

/**
 * QA sprint 5 (tanda 2 y 3): lo que la familia responde en el portal. Dado que la familia tiene su renovación 2027 por
 * responder y sus recordatorios encendidos, cuando envía una respuesta, entonces solo cuenta lo que ELIGIÓ: una petición
 * sin respuesta no se interpreta como «no continúa» ni como «apaga los recordatorios».
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RespuestasPortalQaTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioRenovacion.Datos d;

	private Long renovacion;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), RESPONDEN_HASTA);
		SecurityContextHolder.clearContext();
		renovacion = EscenarioRenovacion.renovacionDe(jdbc, d.mateo());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String estado() {
		return jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacion);
	}

	/**
	 * La respuesta se escribe UNA vez y «no continúa» libera la vacante. Un POST que llega sin el campo (doble envío, un
	 * navegador que no manda el botón, un enlace mal armado) no puede decidir por la familia.
	 */
	@Test
	@Disabled("QA-S5-2: PortalFamiliaController.responder usa @RequestParam(defaultValue = \"false\") continua: un POST "
			+ "sin el campo registra NO_CONTINUA para siempre")
	void unPostSinRespuestaNoRegistraQueElAlumnoNoContinua() throws Exception {
		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).with(UsuariosDePrueba.como(d.rosaEnLinea())));

		assertThat(estado()).isEqualTo("PROPUESTA");
	}

	@Test
	@Disabled("QA-S5-2: PortalFamiliaController.preferencias usa @RequestParam(defaultValue = \"false\"): un POST sin "
			+ "el campo apaga los recordatorios del apoderado")
	void unPostSinValorNoApagaLosRecordatorios() throws Exception {
		mvc.perform(post("/familia/preferencias").with(csrf()).with(UsuariosDePrueba.como(d.rosaEnLinea())));

		assertThat(jdbc.queryForObject("SELECT recordatorios_activos FROM apoderado WHERE id = ?", Boolean.class,
				d.rosa())).isTrue();
	}

	@Test
	void laFamiliaPuedeResponderHastaElUltimoMinutoDelDiaLimite() throws Exception {
		reloj.fijar(ZonedDateTime.of(2027, 1, 31, 23, 59, 0, 0, LIMA).toInstant());

		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(d.rosaEnLinea()))).andExpect(status().is3xxRedirection());

		assertThat(estado()).isEqualTo("MATRICULADA");
	}

	@Test
	void pasadoElDiaLimiteLaRespuestaSeRechazaYNoGeneraDeuda() throws Exception {
		reloj.fijar(ZonedDateTime.of(2027, 2, 1, 0, 0, 1, 0, LIMA).toInstant());

		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(d.rosaEnLinea()))).andExpect(status().is3xxRedirection());

		assertThat(estado()).isEqualTo("PROPUESTA");
		assertThat(contar(jdbc, "cuota WHERE anio_escolar_id = " + d.anio2027())).isZero();
	}

	@Test
	void sinTokenCsrfNoSeRegistraLaRespuesta() throws Exception {
		mvc.perform(post("/familia/matricula/" + renovacion).param("continua", "false")
				.with(UsuariosDePrueba.como(d.rosaEnLinea()))).andExpect(status().isForbidden());

		assertThat(estado()).isEqualTo("PROPUESTA");
	}

	@Test
	void laCajaNoRespondeLaRenovacionDeUnaFamilia() throws Exception {
		mvc.perform(post("/familia/matricula/" + renovacion).with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA)))
				.andExpect(status().isForbidden());

		assertThat(estado()).isEqualTo("PROPUESTA");
	}
}
