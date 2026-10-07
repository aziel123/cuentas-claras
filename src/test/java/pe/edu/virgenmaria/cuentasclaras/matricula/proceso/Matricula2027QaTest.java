package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.RESPONDEN_HASTA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion.renovacionDe;

/**
 * QA sprint 5 (tanda 2, G18): la matrícula 2027 y sus montos. Dado que Rosa confirmó en el portal que Mateo continúa,
 * cuando se paga (o no) la cuota de matrícula, entonces la matrícula se activa solo con la cuota PAGADA, las pensiones
 * salen del plan aprobado con escala 2 y nada se duplica al repetir los procesos.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.caja.permitir-pago-a-cuenta=true")
class Matricula2027QaTest {

	@Autowired
	private ActivacionMatriculas activacion;

	@Autowired
	private ReservaMatriculas reserva;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioRenovacionFamilia familia;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioRenovacion.Datos d;

	private Long matricula;

	private Long cuotaMatricula;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(d.anio2027(), RESPONDEN_HASTA);
		como(d.rosaEnLinea());
		familia.responder(renovacionDe(jdbc, d.mateo()), true);
		SecurityContextHolder.clearContext();
		matricula = jdbc.queryForObject("SELECT matricula_id FROM renovacion_matricula WHERE alumno_id = ?", Long.class,
				d.mateo());
		cuotaMatricula = EscenarioCaja.cuota(jdbc, d.mateo(), "MAT-2027");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String estadoMatricula() {
		return jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, matricula);
	}

	private void pagar(String monto, boolean aCuenta, String saldoVisto) {
		como(EscenarioCobranza.CAJA);
		cobro.cobrar(new CobroRequest(UUID.randomUUID(), d.quispe(), List.of(cuotaMatricula), MedioPago.EFECTIVO, null,
				new BigDecimal(monto), aCuenta ? new BigDecimal(monto) : null, new BigDecimal(saldoVisto),
				TipoComprobante.BOLETA, null, null, null));
		SecurityContextHolder.clearContext();
	}

	@Test
	void unPagoParcialDeLaMatriculaNoLaActivaNiGeneraPensiones() {
		pagar("100.00", true, "300.00");

		assertThat(EscenarioCaja.estado(jdbc, cuotaMatricula)).isEqualTo("PARCIAL");
		assertThat(activacion.activar(1L)).isZero();
		assertThat(estadoMatricula()).isEqualTo("RESERVADA");
		assertThat(contar(jdbc, "cuota WHERE tipo = 'PENSION' AND matricula_id = " + matricula)).isZero();
	}

	@Test
	void completarElPagoParcialActivaLaMatricula() {
		pagar("100.00", true, "300.00");
		pagar("200.00", false, "200.00");

		assertThat(EscenarioCaja.estado(jdbc, cuotaMatricula)).isEqualTo("PAGADA");
		assertThat(estadoMatricula()).isEqualTo("ACTIVA");
		assertThat(contar(jdbc, "cuota WHERE tipo = 'PENSION' AND matricula_id = " + matricula)).isEqualTo(10);
	}

	@Test
	void lasDiezPensionesSalenDelPlanConEscalaDosYVencenDeMarzoADiciembre2027() {
		pagar("300.00", false, "300.00");

		List<Map<String, Object>> pensiones = jdbc.queryForList("SELECT monto, fecha_vencimiento, estado FROM cuota "
				+ "WHERE tipo = 'PENSION' AND matricula_id = ? ORDER BY fecha_vencimiento", matricula);
		assertThat(pensiones).hasSize(10).allSatisfy(p -> {
			assertThat((BigDecimal) p.get("monto")).isEqualByComparingTo("450.00").satisfies(m -> assertThat(m.scale())
					.isEqualTo(2));
			assertThat(p).containsEntry("estado", "PENDIENTE");
			assertThat(((Date) p.get("fecha_vencimiento")).toLocalDate().getYear()).isEqualTo(2027);
		});
		assertThat(pensiones).extracting(p -> ((Date) p.get("fecha_vencimiento")).toLocalDate().getMonthValue())
				.containsExactly(3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
		// Toda la deuda 2027 de Mateo: 300 de matrícula (pagada) + 10 × 450.
		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE matricula_id = ?", BigDecimal.class,
				matricula)).isEqualByComparingTo("4800.00");
	}

	@Test
	void repetirLosBarridosNoDuplicaLaMatriculaNiLasCuotas() {
		pagar("300.00", false, "300.00");
		long cuotas = contar(jdbc, "cuota WHERE matricula_id = " + matricula);

		assertThat(reserva.enColegio(1L)).isZero();
		assertThat(activacion.activar(1L)).isZero();

		assertThat(contar(jdbc, "matricula WHERE anio_escolar_id = " + d.anio2027() + " AND alumno_id = " + d.mateo()))
				.isEqualTo(1);
		assertThat(contar(jdbc, "cuota WHERE matricula_id = " + matricula)).isEqualTo(cuotas);
	}

	@Test
	void sinConfirmacionNoHayDeudaDe2027ParaValeria() {
		assertThat(contar(jdbc, "cuota WHERE alumno_id = " + d.valeria() + " AND anio_escolar_id = " + d.anio2027()))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE alumno_id = ?", String.class,
				d.valeria())).isEqualTo("PROPUESTA");
		assertThat(jdbc.queryForObject("SELECT vence_en FROM renovacion_matricula WHERE alumno_id = ?", Date.class,
				d.valeria()).toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 31));
	}
}
