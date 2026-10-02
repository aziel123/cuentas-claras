package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué cuotas genera un plan para una matrícula (sección 10, regla 6). Pura y parametrizada. */
class CalculadoraCronogramaTest {

	private static final int ANIO = 2027;

	private static ConfiguracionPlan plan(String matricula, LocalDate cobroDesde) {
		ConfiguracionPlan base = ConfiguracionPlan.porDefecto(ANIO, new BigDecimal(matricula), new BigDecimal("450.00"));
		return new ConfiguracionPlan(base.montoMatricula(), base.vencimientoMatricula(), base.montoPension(),
				base.vencimientos(), cobroDesde);
	}

	private static List<CuotaPlanificada> calcular(ConfiguracionPlan plan, LocalDate fechaMatricula) {
		return CalculadoraCronograma.calcular(plan, ANIO, 41L, fechaMatricula);
	}

	@Test
	void planDe10CuotasGenera10PensionesYMatricula() {
		List<CuotaPlanificada> cuotas = calcular(plan("350.00", null), LocalDate.of(2027, 1, 15));

		assertThat(cuotas).hasSize(11);
		assertThat(cuotas.get(0).tipo()).isEqualTo(TipoCuota.MATRICULA);
		assertThat(cuotas.get(0).vencimiento()).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(cuotas.subList(1, 11)).extracting(CuotaPlanificada::numero)
				.containsExactly(3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	@ParameterizedTest(name = "matrícula {0} → {1} cuotas")
	@CsvSource({ "0.00, 10", "0, 10", "0.01, 11", "350.00, 11" })
	void matriculaEnCeroNoGeneraCuota(String matricula, int esperadas) {
		List<CuotaPlanificada> cuotas = calcular(plan(matricula, null), LocalDate.of(2027, 1, 15));
		assertThat(cuotas).hasSize(esperadas);
		assertThat(cuotas.stream().anyMatch(c -> c.tipo() == TipoCuota.MATRICULA)).isEqualTo(esperadas == 11);
	}

	@Test
	void cobroDesdeDiciembre2026SoloGeneraDiciembre() {
		ConfiguracionPlan base = ConfiguracionPlan.porDefecto(2026, new BigDecimal("350.00"), new BigDecimal("450.00"));
		ConfiguracionPlan plan2026 = new ConfiguracionPlan(base.montoMatricula(), base.vencimientoMatricula(),
				base.montoPension(), base.vencimientos(), LocalDate.of(2026, 12, 1));

		List<CuotaPlanificada> cuotas = CalculadoraCronograma.calcular(plan2026, 2026, 7L, LocalDate.of(2026, 3, 2));

		// Solo la pensión de diciembre: la matrícula (28/02) y las pensiones anteriores son saldo inicial.
		assertThat(cuotas).singleElement().satisfies(c -> {
			assertThat(c.descripcion()).isEqualTo("Pensión diciembre 2026");
			assertThat(c.vencimiento()).isEqualTo(LocalDate.of(2026, 12, 31));
			assertThat(c.obligacion()).isEqualTo("PEN-2026-12");
		});
	}

	@Test
	void ingresoEl10DeJunioCobraDeJunioADiciembre() {
		List<CuotaPlanificada> cuotas = calcular(plan("350.00", null), LocalDate.of(2027, 6, 10));

		assertThat(cuotas).hasSize(8);
		assertThat(cuotas.get(0).tipo()).isEqualTo(TipoCuota.MATRICULA);
		// La matrícula vence el día del ingreso (no en febrero, que ya pasó).
		assertThat(cuotas.get(0).vencimiento()).isEqualTo(LocalDate.of(2027, 6, 10));
		assertThat(cuotas.subList(1, 8)).extracting(CuotaPlanificada::numero).containsExactly(6, 7, 8, 9, 10, 11, 12);
	}

	@ParameterizedTest(name = "ingreso {0} → primera pensión del mes {1}")
	@CsvSource({ "2027-03-31, 3", "2027-03-01, 3", "2027-04-01, 4", "2027-09-30, 9", "2027-12-31, 12" })
	void ingresoEl31DeMarzoIncluyeMarzo(LocalDate ingreso, int primerMes) {
		List<CuotaPlanificada> pensiones = calcular(plan("0", null), ingreso);
		assertThat(pensiones.get(0).numero()).isEqualTo(primerMes);
		assertThat(pensiones).hasSize(12 - primerMes + 1);
	}

	@Test
	void matricula2027HechaEnDiciembre2026GeneraTodo() {
		List<CuotaPlanificada> cuotas = calcular(plan("350.00", null), LocalDate.of(2026, 12, 5));
		assertThat(cuotas).hasSize(11);
		assertThat(cuotas.get(0).vencimiento()).isEqualTo(LocalDate.of(2027, 2, 28));
	}

	@Test
	void cobroDesdeDespuesDeLaMatriculaOmiteLaMatricula() {
		List<CuotaPlanificada> cuotas = calcular(plan("350.00", LocalDate.of(2027, 9, 1)), LocalDate.of(2027, 1, 10));
		assertThat(cuotas).extracting(CuotaPlanificada::numero).containsExactly(9, 10, 11, 12);
	}

	@Test
	void clavesYObligacionesSonDeterministas() {
		List<CuotaPlanificada> primera = calcular(plan("350.00", null), LocalDate.of(2027, 1, 15));
		List<CuotaPlanificada> segunda = calcular(plan("350.00", null), LocalDate.of(2027, 1, 15));

		assertThat(primera).isEqualTo(segunda);
		assertThat(primera.get(0).clave()).isEqualTo("MAT:41");
		assertThat(primera.get(0).obligacion()).isEqualTo("MAT-2027");
		assertThat(primera.get(7).clave()).isEqualTo("PEN:41:9");
		assertThat(primera.get(7).obligacion()).isEqualTo("PEN-2027-09");
		assertThat(primera).extracting(CuotaPlanificada::clave).doesNotHaveDuplicates();
		// Otra matrícula del mismo alumno tendría otras claves, pero la misma obligación (no se cobra dos veces).
		assertThat(CalculadoraCronograma.calcular(plan("350.00", null), ANIO, 42L, LocalDate.of(2027, 1, 15)).get(7))
				.satisfies(c -> {
					assertThat(c.clave()).isEqualTo("PEN:42:9");
					assertThat(c.obligacion()).isEqualTo("PEN-2027-09");
				});
	}

	@Test
	void descripcionDiceSetiembre() {
		assertThat(calcular(plan("0", null), LocalDate.of(2027, 1, 15))).extracting(CuotaPlanificada::descripcion)
				.contains("Pensión setiembre 2027").doesNotContain("Pensión septiembre 2027");
	}

	@Test
	void montosSonExactamenteLosDelPlanConEscala2() {
		ConfiguracionPlan plan = new ConfiguracionPlan(new BigDecimal("333.33"), LocalDate.of(2027, 2, 28),
				new BigDecimal("333.33"), ConfiguracionPlan.porDefecto(ANIO, BigDecimal.ONE, BigDecimal.ONE).vencimientos(),
				null).validar(CuotasDePrueba.ANIO_2027);

		List<CuotaPlanificada> cuotas = calcular(plan, LocalDate.of(2027, 1, 15));

		assertThat(cuotas).extracting(CuotaPlanificada::monto).allSatisfy(m -> {
			assertThat(m.scale()).isEqualTo(2);
			assertThat(m).isEqualByComparingTo("333.33");
		});
		BigDecimal total = cuotas.stream().map(CuotaPlanificada::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(total).isEqualByComparingTo("3666.63");
	}
}
