package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del plan (sección 10 del diseño). Pura: sin base ni reloj. */
class ConfiguracionPlanTest {

	private static final AnioEscolar ANIO_2027 = AnioEscolar.nuevo(2027, false, LocalDate.of(2027, 3, 1),
			LocalDate.of(2027, 12, 17));

	private static ConfiguracionPlan porDefecto(String matricula, String pension) {
		return ConfiguracionPlan.porDefecto(2027, new BigDecimal(matricula), new BigDecimal(pension));
	}

	@Test
	void vencimientosPorDefectoSonElUltimoDiaDeMarzoADiciembre() {
		assertThat(porDefecto("350", "450").vencimientos()).containsExactly(LocalDate.of(2027, 3, 31),
				LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 30), LocalDate.of(2027, 7, 31),
				LocalDate.of(2027, 8, 31), LocalDate.of(2027, 9, 30), LocalDate.of(2027, 10, 31), LocalDate.of(2027, 11, 30),
				LocalDate.of(2027, 12, 31));
	}

	@Test
	void matriculaVencePorDefectoEl28DeFebrero() {
		assertThat(porDefecto("350", "450").vencimientoMatricula()).isEqualTo(LocalDate.of(2027, 2, 28));
	}

	@Test
	void en2028LaMatriculaVenceEl29DeFebrero() {
		assertThat(ConfiguracionPlan.porDefecto(2028, BigDecimal.ONE, BigDecimal.TEN).vencimientoMatricula())
				.isEqualTo(LocalDate.of(2028, 2, 29));
	}

	@Test
	void montosQuedanEnEscala2() {
		ConfiguracionPlan valido = porDefecto("350", "450.5").validar(ANIO_2027);
		assertThat(valido.montoMatricula()).hasToString("350.00");
		assertThat(valido.montoPension()).hasToString("450.50");
	}

	@Test
	void montoConTresDecimalesSeRechazaSinRedondear() {
		assertThatThrownBy(() -> porDefecto("350", "450.005").validar(ANIO_2027))
				.isInstanceOf(ReglaNegocioException.class).hasMessage("El monto debe tener como máximo 2 decimales.");
		assertThatThrownBy(() -> porDefecto("349.999", "450").validar(ANIO_2027))
				.hasMessage("El monto debe tener como máximo 2 decimales.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-1", "-450.00" })
	void pensionCeroONegativaEsRechazada(String pension) {
		assertThatThrownBy(() -> porDefecto("0", pension).validar(ANIO_2027))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La pensión debe estar entre S/ 0.01 y S/ 99,999.99.");
	}

	@Test
	void matriculaMayorQueLaPensionEsRechazada() {
		assertThatThrownBy(() -> porDefecto("450.01", "450").validar(ANIO_2027))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La matrícula (S/ 450.01) no puede ser mayor que una pensión (S/ 450.00): lo prohíbe el "
						+ "DS 005-2021-MINEDU.");
		// Igual a la pensión sí se permite, y cero también (no se cobra).
		assertThat(porDefecto("450", "450").validar(ANIO_2027).montoMatricula()).hasToString("450.00");
		assertThat(porDefecto("0", "450").validar(ANIO_2027).montoMatricula()).hasToString("0.00");
	}

	@Test
	void vencimientosRepetidosYMatriculaNegativaSonRechazados() {
		LocalDate marzo = LocalDate.of(2027, 3, 31);
		assertThatThrownBy(() -> conVencimientos(List.of(marzo, marzo)).validar(ANIO_2027)).hasMessageContaining("uno por mes");
		assertThatThrownBy(() -> porDefecto("-0.01", "450").validar(ANIO_2027))
				.hasMessage("La matrícula debe estar entre S/ 0.00 y S/ 99,999.99.");
	}

	@Test
	void vencimientosDesordenadosOEnElMismoMesSonRechazados() {
		List<LocalDate> desordenados = new ArrayList<>(porDefecto("0", "450").vencimientos());
		desordenados.set(2, LocalDate.of(2027, 4, 15));
		assertThatThrownBy(() -> conVencimientos(desordenados).validar(ANIO_2027))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("uno por mes");
		List<LocalDate> mismoMes = List.of(LocalDate.of(2027, 3, 15), LocalDate.of(2027, 3, 31));
		assertThatThrownBy(() -> conVencimientos(mismoMes).validar(ANIO_2027)).hasMessageContaining("uno por mes");
		List<LocalDate> alReves = List.of(LocalDate.of(2027, 5, 31), LocalDate.of(2027, 4, 30));
		assertThatThrownBy(() -> conVencimientos(alReves).validar(ANIO_2027)).hasMessageContaining("en orden");
	}

	@Test
	void entreUnaYDocePensiones() {
		assertThatThrownBy(() -> conVencimientos(List.of()).validar(ANIO_2027))
				.hasMessage("El plan debe tener de 1 a 12 pensiones.");
		assertThat(conVencimientos(List.of(LocalDate.of(2027, 12, 31))).validar(ANIO_2027).vencimientos()).hasSize(1);
	}

	@Test
	void vencimientoFueraDelAnioEsRechazado() {
		assertThatThrownBy(() -> conVencimientos(List.of(LocalDate.of(2027, 12, 31), LocalDate.of(2028, 1, 31)))
				.validar(ANIO_2027)).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La pensión con vencimiento 31/01/2028 está fuera del año 2027.");
	}

	@Test
	void pensionAntesDelInicioDeClasesEsRechazada() {
		assertThatThrownBy(() -> conVencimientos(List.of(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)))
				.validar(ANIO_2027)).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("La pensión de febrero vence antes del inicio de clases (01/03/2027): no se cobra por adelantado.");
	}

	@Test
	void matriculaVenceEntreElAnioAnteriorYElFinDeClases() {
		ConfiguracionPlan base = porDefecto("350", "450");
		assertThatThrownBy(() -> new ConfiguracionPlan(base.montoMatricula(), LocalDate.of(2025, 12, 31),
				base.montoPension(), base.vencimientos(), null).validar(ANIO_2027))
				.hasMessage("La matrícula debe vencer entre el 01/01/2026 y el fin de clases (17/12/2027).");
		assertThat(new ConfiguracionPlan(base.montoMatricula(), LocalDate.of(2026, 12, 15), base.montoPension(),
				base.vencimientos(), null).validar(ANIO_2027).vencimientoMatricula()).isEqualTo(LocalDate.of(2026, 12, 15));
	}

	@Test
	void cobroDesdeDebeSerDia1() {
		ConfiguracionPlan base = porDefecto("350", "450");
		assertThatThrownBy(() -> conCobroDesde(base, LocalDate.of(2027, 9, 15)).validar(ANIO_2027))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("«Cobrar desde» debe ser el día 1 de un mes de 2027 (por ejemplo 01/12/2027).");
		assertThatThrownBy(() -> conCobroDesde(base, LocalDate.of(2026, 12, 1)).validar(ANIO_2027))
				.hasMessageContaining("día 1 de un mes de 2027");
		assertThat(conCobroDesde(base, LocalDate.of(2027, 9, 1)).validar(ANIO_2027).descripcionCobroDesde())
				.isEqualTo("setiembre 2027");
	}

	/** QA: «cobrar desde» después de la última pensión se aprobaba y generaba cero pensiones en silencio. */
	@Test
	void cobroDesdeDespuesDeLaUltimaPensionEsRechazado() {
		assertThat(conCobroDesde(porDefecto("350", "450"), LocalDate.of(2027, 12, 1)).validar(ANIO_2027).cobroDesde())
				.isEqualTo(LocalDate.of(2027, 12, 1));
		ConfiguracionPlan hastaNoviembre = new ConfiguracionPlan(BigDecimal.ZERO, LocalDate.of(2027, 2, 28),
				new BigDecimal("450"), Calendario.vencimientosPorDefecto(2027, 3, 9), LocalDate.of(2027, 12, 1));
		assertThatThrownBy(() -> hastaNoviembre.validar(ANIO_2027)).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("Cobrando desde el 01/12/2027 no se cobraría ninguna pensión del plan: la última vence el "
						+ "30/11/2027.");
	}

	@Test
	void montoMaximoEs99999_99() {
		assertThat(porDefecto("0", "99999.99").validar(ANIO_2027).montoPension()).hasToString("99999.99");
		assertThatThrownBy(() -> porDefecto("0", "100000.00").validar(ANIO_2027))
				.hasMessage("La pensión debe estar entre S/ 0.01 y S/ 99,999.99.");
	}

	private static ConfiguracionPlan conVencimientos(List<LocalDate> vencimientos) {
		return new ConfiguracionPlan(BigDecimal.ZERO, LocalDate.of(2027, 2, 28), new BigDecimal("450"), vencimientos,
				null);
	}

	private static ConfiguracionPlan conCobroDesde(ConfiguracionPlan base, LocalDate desde) {
		return new ConfiguracionPlan(base.montoMatricula(), base.vencimientoMatricula(), base.montoPension(),
				base.vencimientos(), desde);
	}
}
