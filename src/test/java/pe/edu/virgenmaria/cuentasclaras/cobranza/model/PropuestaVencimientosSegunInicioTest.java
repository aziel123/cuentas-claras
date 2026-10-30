package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 1b (F-D2 y F-D3): la propuesta de vencimientos sale de las fechas de clases y siempre pasa la
 * validación; los rangos del formulario (min y max) son los mismos que usa {@code validar}.
 */
class PropuestaVencimientosSegunInicioTest {

	private static AnioEscolar anio(int a, LocalDate inicio, LocalDate fin) {
		return AnioEscolar.nuevo(a, false, inicio, fin);
	}

	private static ConfiguracionPlan propuesta(AnioEscolar anio) {
		return ConfiguracionPlan.porDefecto(anio, new BigDecimal("300.00"), new BigDecimal("450.00"));
	}

	private static List<YearMonth> meses(ConfiguracionPlan plan) {
		return plan.vencimientos().stream().map(YearMonth::from).toList();
	}

	@Test
	void clasesDeMarzoADiciembre() {
		AnioEscolar a = anio(2026, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 18));
		ConfiguracionPlan plan = propuesta(a);
		assertThat(plan.vencimientos()).hasSize(10).first().isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(plan.vencimientos()).last().isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(plan.validar(a)).isNotNull();
	}

	@Test
	void clasesDeAbrilANoviembreSinMarzoNiDiciembre() {
		AnioEscolar a = anio(2026, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 11, 30));
		ConfiguracionPlan plan = propuesta(a);
		assertThat(plan.vencimientos()).hasSize(8);
		assertThat(meses(plan)).doesNotContain(YearMonth.of(2026, 3), YearMonth.of(2026, 12))
				.first().isEqualTo(YearMonth.of(2026, 4));
		assertThat(meses(plan)).last().isEqualTo(YearMonth.of(2026, 11));
		assertThat(plan.validar(a)).isNotNull();
	}

	@Test
	void clasesQueEmpiezanAMitadDeMes() {
		AnioEscolar a = anio(2026, LocalDate.of(2026, 3, 16), LocalDate.of(2026, 12, 18));
		ConfiguracionPlan plan = propuesta(a);
		assertThat(plan.vencimientos()).hasSize(10).first().isEqualTo(LocalDate.of(2026, 3, 31));
		assertThat(plan.validar(a)).isNotNull();
	}

	@Test
	void unAnioDeEneroADiciembreLlegaAlTope() {
		AnioEscolar a = anio(2027, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 12, 20));
		ConfiguracionPlan plan = propuesta(a);
		assertThat(plan.vencimientos()).hasSize(ConfiguracionPlan.MAX_PENSIONES);
		assertThat(plan.validar(a)).isNotNull();
	}

	@Test
	void losRangosSonLosDeLaValidacion() {
		AnioEscolar a = anio(2026, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 11, 30));
		ConfiguracionPlan.RangoFechas matricula = ConfiguracionPlan.rangoMatricula(a);
		ConfiguracionPlan.RangoFechas pensiones = ConfiguracionPlan.rangoPensiones(a);
		ConfiguracionPlan.RangoFechas cobro = ConfiguracionPlan.rangoCobroDesde(a);
		assertThat(matricula).isEqualTo(new ConfiguracionPlan.RangoFechas(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 30)));
		assertThat(pensiones).isEqualTo(new ConfiguracionPlan.RangoFechas(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 12, 31)));
		assertThat(cobro).isEqualTo(new ConfiguracionPlan.RangoFechas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
		assertThat(matricula.enPalabras()).isEqualTo("Entre el 01/01/2025 y el 30/11/2026");

		ConfiguracionPlan base = propuesta(a);
		// La matrícula: acepta el min y el max, rechaza un día antes y uno después.
		assertThat(con(base, matricula.desde(), base.vencimientos(), null).validar(a)).isNotNull();
		assertThat(con(base, matricula.hasta(), base.vencimientos(), null).validar(a)).isNotNull();
		assertThatThrownBy(() -> con(base, matricula.desde().minusDays(1), base.vencimientos(), null).validar(a))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> con(base, matricula.hasta().plusDays(1), base.vencimientos(), null).validar(a))
				.isInstanceOf(ReglaNegocioException.class);
		// La primera pensión: el día 1 del mes de inicio sí; el último día del mes anterior, no. La última: 31/12 sí.
		assertThat(con(base, base.vencimientoMatricula(), List.of(pensiones.desde()), null).validar(a)).isNotNull();
		assertThatThrownBy(() -> con(base, base.vencimientoMatricula(), List.of(pensiones.desde().minusDays(1)), null)
				.validar(a)).isInstanceOf(ReglaNegocioException.class);
		assertThat(con(base, base.vencimientoMatricula(), List.of(pensiones.hasta()), null).validar(a)).isNotNull();
		assertThatThrownBy(() -> con(base, base.vencimientoMatricula(), List.of(pensiones.hasta().plusDays(1)), null)
				.validar(a)).isInstanceOf(ReglaNegocioException.class);
		// «Cobrar desde»: el 01/01 del año sí; el 01/01 del año siguiente, no.
		assertThat(con(base, base.vencimientoMatricula(), base.vencimientos(), cobro.desde()).validar(a)).isNotNull();
		assertThatThrownBy(() -> con(base, base.vencimientoMatricula(), base.vencimientos(), cobro.hasta().plusDays(1))
				.validar(a)).isInstanceOf(ReglaNegocioException.class);
	}

	private static ConfiguracionPlan con(ConfiguracionPlan base, LocalDate matricula, List<LocalDate> pensiones,
			LocalDate cobroDesde) {
		return new ConfiguracionPlan(base.montoMatricula(), matricula, base.montoPension(), pensiones, cobroDesde);
	}
}
