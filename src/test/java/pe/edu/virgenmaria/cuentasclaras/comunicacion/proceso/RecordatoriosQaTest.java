package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA sprint 5 (tanda 3): recordatorios con feriados del colegio, pagos que llegan antes del despacho y aislamiento del
 * calendario entre colegios. Escenario de {@code EscenarioCaja}: las pensiones de marzo de 2027 vencen el miércoles 31;
 * el «3 días antes» sale el sábado 27 (el 28 es domingo de Pascua y el 25 y 26 son Jueves y Viernes Santo).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RecordatoriosQaTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final LocalDate MIERCOLES_24 = LocalDate.of(2027, 3, 24);

	private static final LocalDate SABADO_27 = LocalDate.of(2027, 3, 27);

	@Autowired
	private Recordatorios recordatorios;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
	}

	/** Un día no laborable del colegio, como lo dejaría Promotoría (en H2 no hay triggers). */
	private void feriadoDelColegio(long colegio, LocalDate fecha) {
		jdbc.update("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, creado_en, creado_por, actualizado_en, "
				+ "version) VALUES (?, ?, 'Día no laborable decretado', TRUE, CURRENT_TIMESTAMP, 'promotor', "
				+ "CURRENT_TIMESTAMP, 0)", colegio, fecha);
		calendario.invalidarTodo();
	}

	private List<Map<String, Object>> recordatoriosDe(Long familia) {
		return jdbc.queryForList("SELECT id, tipo, estado FROM mensaje WHERE familia_id = ? AND tipo = "
				+ "'RECORDATORIO_VENCIMIENTO' ORDER BY id", familia);
	}

	@Test
	void unNoLaborableDelColegioRegistradoConTiempoAdelantaElRecordatorio() {
		feriadoDelColegio(1L, SABADO_27);

		assertThat(recordatorios.enColegio(1L, SABADO_27)).isZero();
		assertThat(recordatorios.enColegio(1L, MIERCOLES_24)).isPositive();
		assertThat(recordatoriosDe(f.quispe())).hasSize(1);
	}

	/**
	 * Dado que el miércoles 24 aún no había feriado del colegio (el recordatorio tocaba el sábado 27), cuando Promotoría
	 * registra el sábado 27 como no laborable (se permite desde mañana), entonces el recordatorio no puede perderse: debe
	 * salir el siguiente día de mensajes antes del vencimiento (el lunes 29).
	 */
	@Test
	@Disabled("QA-S5-4: ServicioRecordatorios.preparar solo envía si el «día de mensajes» calculado es HOY; si ese día "
			+ "se declara no laborable después de pasar el día anterior, el recordatorio no sale nunca")
	void unNoLaborableRegistradoTardeNoHacePerderElRecordatorio() {
		assertThat(recordatorios.enColegio(1L, MIERCOLES_24)).isZero();
		feriadoDelColegio(1L, SABADO_27);

		for (LocalDate dia = SABADO_27; dia.isBefore(LocalDate.of(2027, 3, 31)); dia = dia.plusDays(1)) {
			recordatorios.enColegio(1L, dia);
		}

		assertThat(recordatoriosDe(f.quispe())).isNotEmpty();
	}

	/**
	 * Dado un recordatorio preparado el sábado 27 a las 08:00, cuando la familia paga todas esas cuotas antes de que el
	 * despacho lo envíe, entonces el despacho ya no debe recordarle una deuda que no existe.
	 */
	@Test
	@Disabled("QA-S5-3: DespachoMensajes.procesar no vuelve a mirar las cuotas de un recordatorio: se envía aunque ya "
			+ "estén pagadas (pospuesto por la ventana o por reintentos puede salir días después)")
	void noDebeEnviarseElRecordatorioDeCuotasQueYaSePagaron() {
		recordatorios.enColegio(1L, SABADO_27);
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03"),
				cuota(jdbc, f.valeria(), "PEN-2027-03")), "900.00", "900.00"));
		SecurityContextHolder.clearContext();

		reloj.fijar(ZonedDateTime.of(2027, 3, 27, 10, 0, 0, 0, LIMA).toInstant());
		despacho.despacharColegio(1L);

		assertThat(recordatoriosDe(f.quispe())).allSatisfy(m -> assertThat(m.get("estado")).isNotEqualTo("ENVIADO"));
	}

	@Test
	void elMontoDelRecordatorioEsLaSumaExactaDeLosSaldos() {
		recordatorios.enColegio(1L, SABADO_27);

		String parametros = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE familia_id = ? AND tipo = "
				+ "'RECORDATORIO_VENCIMIENTO'", String.class, f.quispe());
		// Dos pensiones de 450.00: el monto del recordatorio es la suma exacta de los saldos.
		assertThat(parametros).contains("S/ 900.00");
	}

	@Test
	void elNoLaborableDeOtroColegioNoCambiaElCalendarioDeEsteColegio() {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		LocalDate lunes = LocalDate.of(2027, 3, 29);
		feriadoDelColegio(colegioB, lunes);

		assertThat(ContextoColegio.en(1L, () -> calendario.esHabil(lunes))).isTrue();
		assertThat(ContextoColegio.en(colegioB, () -> calendario.esHabil(lunes))).isFalse();
		// Y los recordatorios del colegio A no se mueven por el feriado del B.
		assertThat(recordatorios.enColegio(colegioB, SABADO_27)).isZero();
		assertThat(recordatorios.enColegio(1L, SABADO_27)).isPositive();
	}

	@Test
	void unaFamiliaQueApagoLosRecordatoriosNoRecibeNiElDeVencida() {
		jdbc.update("UPDATE apoderado SET recordatorios_activos = FALSE WHERE id = ?", f.rosa());

		recordatorios.enColegio(1L, SABADO_27);
		recordatorios.enColegio(1L, LocalDate.of(2027, 4, 1));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo IN "
				+ "('RECORDATORIO_VENCIMIENTO', 'CUOTA_VENCIDA')", Long.class, f.quispe())).isZero();
	}
}
