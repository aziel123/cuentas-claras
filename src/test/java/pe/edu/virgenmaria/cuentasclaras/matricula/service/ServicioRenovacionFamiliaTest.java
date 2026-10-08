package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.matricula.dto.RenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.proceso.VencimientoRenovaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.renovacionDe;

/**
 * Sprint 5, tanda 2 (decisión 53): la familia confirma en el portal y recién entonces el sistema reserva la matrícula
 * con la cuota del plan aprobado; si declina o no responde, no hay deuda. La familia sale de la sesión (G17).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioRenovacionFamiliaTest {

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioRenovacionFamilia familia;

	@Autowired
	private VencimientoRenovaciones vencimiento;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioRenovacion.Datos d;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), RESPONDEN_HASTA);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void confirmarCreaLaMatriculaReservadaConSuCuota() {
		como(d.rosaEnLinea());
		RenovacionFamilia vista = familia.deMiFamilia().stream().filter(r -> r.alumno().equals("Mateo")).findFirst()
				.orElseThrow();
		assertThat(vista.montoMatricula()).isEqualByComparingTo("300.00");
		assertThat(vista.vencimientoMatricula()).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(vista.porResponder()).isTrue();

		familia.responder(vista.id(), true);
		SecurityContextHolder.clearContext();

		Map<String, Object> renovacion = jdbc.queryForMap("SELECT * FROM renovacion_matricula WHERE id = ?", vista.id());
		assertThat(renovacion).containsEntry("estado", "MATRICULADA").containsEntry("canal_respuesta", "PORTAL")
				.containsEntry("respondido_por", "rosa.familia");
		Long matricula = (Long) renovacion.get("matricula_id");
		assertThat(jdbc.queryForMap("SELECT estado, seccion_id, creado_por FROM matricula WHERE id = ?", matricula))
				.containsEntry("estado", "RESERVADA").containsEntry("seccion_id", d.p6B2027())
				.containsEntry("creado_por", "sistema.matricula");
		// Solo la cuota de matrícula, del plan aprobado; las pensiones llegan al pagarla.
		assertThat(jdbc.queryForList("SELECT tipo, monto, estado FROM cuota WHERE matricula_id = ?", matricula))
				.singleElement().satisfies(c -> {
					assertThat(c).containsEntry("tipo", "MATRICULA").containsEntry("estado", "PENDIENTE");
					assertThat((BigDecimal) c.get("monto")).isEqualByComparingTo("300.00");
				});
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "MATRICULA_RESERVADA")).containsEntry("nombre_usuario",
				"sistema.matricula");
		// La respuesta se escribe una vez.
		como(d.rosaEnLinea());
		assertThatThrownBy(() -> familia.responder(vista.id(), false)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void declinarNoGeneraDeuda() {
		como(d.rosaEnLinea());
		familia.responder(renovacionDe(jdbc, d.valeria()), false);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE alumno_id = ?", String.class,
				d.valeria())).isEqualTo("NO_CONTINUA");
		assertThat(contar(jdbc, "matricula WHERE anio_escolar_id = " + d.anio2027())).isZero();
		assertThat(contar(jdbc, "cuota WHERE anio_escolar_id = " + d.anio2027())).isZero();
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "RENOVACION_NO_CONTINUA")).containsEntry("nombre_usuario",
				"rosa.familia");
	}

	@Test
	void vencidaNoGeneraDeuda() {
		assertThat(vencimiento.enColegio(1L, RESPONDEN_HASTA)).isZero();
		assertThat(vencimiento.enColegio(1L, RESPONDEN_HASTA.plusDays(1))).isEqualTo(2);
		assertThat(contar(jdbc, "renovacion_matricula WHERE estado = 'VENCIDA'")).isEqualTo(2);
		assertThat(contar(jdbc, "matricula WHERE anio_escolar_id = " + d.anio2027())).isZero();
		assertThat(contar(jdbc, "cuota WHERE anio_escolar_id = " + d.anio2027())).isZero();
		como(d.rosaEnLinea());
		assertThatThrownBy(() -> familia.responder(renovacionDe(jdbc, d.mateo()), true))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya pasó");
	}

	@Test
	void otraFamiliaNoVeNiRespondeLaRenovacion() {
		Long renovacion = renovacionDe(jdbc, d.mateo());
		como(d.pedroEnLinea());
		assertThat(familia.deMiFamilia()).isEmpty();
		assertThatThrownBy(() -> familia.una(renovacion)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> familia.responder(renovacion, true)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacion))
				.isEqualTo("PROPUESTA");
	}
}
