package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.renovacionDe;

/**
 * Sprint 5, tanda 3 (pendiente de la tanda 2, sección 13): Promotoría ve «renovaciones confirmadas sin pagar a 7 días
 * del vencimiento». La matrícula 2027 de Mateo vence el domingo 28/02/2027: el aviso aparece desde el 21/02 y se va al
 * pagarla.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AlertasRenovacionSinPagarTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioRenovacionFamilia familia;

	@Autowired
	private AlertasMatricula alertas;

	@Autowired
	private ServicioCobro cobro;

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

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), RESPONDEN_HASTA);
		como(d.rosaEnLinea());
		familia.responder(renovacionDe(jdbc, d.mateo()), true);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private List<AlertaRevision> sinPagar() {
		como(EscenarioCobranza.PROMOTORIA);
		return alertas.alertas().stream().filter(a -> a.texto().contains("sin pagar la matrícula")).toList();
	}

	@Test
	void renovacionConfirmadaSinPagarASieteDiasDelVencimientoEsAtencion() {
		Long cuota = EscenarioCaja.cuota(jdbc, d.mateo(), "MAT-2027");
		assertThat(jdbc.queryForObject("SELECT fecha_vencimiento FROM cuota WHERE id = ?", String.class, cuota))
				.isEqualTo("2027-02-28");

		reloj.fijar(ZonedDateTime.of(2027, 2, 20, 10, 0, 0, 0, LIMA).toInstant());
		assertThat(sinPagar()).isEmpty();

		reloj.fijar(ZonedDateTime.of(2027, 2, 21, 10, 0, 0, 0, LIMA).toInstant());
		assertThat(sinPagar()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION);
			assertThat(a.texto()).startsWith("1 renovación(es) confirmada(s) sin pagar").contains("Mateo Quispe Huamán");
			assertThat(a.enlace()).isEqualTo("/matricula-2027");
		});

		// Pagada (en caja), la matrícula se activa y el aviso se va.
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		como(EscenarioCobranza.CAJA);
		cobro.cobrar(efectivo(d.quispe(), List.of(cuota), "300.00", "300.00"));
		SecurityContextHolder.clearContext();
		reloj.fijar(ZonedDateTime.of(2027, 2, 21, 10, 0, 0, 0, LIMA).toInstant());
		assertThat(sinPagar()).isEmpty();
	}
}
