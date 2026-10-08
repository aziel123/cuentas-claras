package pe.edu.virgenmaria.cuentasclaras.comun.dinero;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DineroTest {

	@Test
	void normalizaAEscala2SinRedondear() {
		assertThat(Dinero.normalizar(new BigDecimal("450"))).hasToString("450.00");
		assertThat(Dinero.normalizar(new BigDecimal("450.5"))).hasToString("450.50");
		assertThat(Dinero.normalizar(new BigDecimal("450.500"))).hasToString("450.50");
	}

	@Test
	void masDeDosDecimalesSeRechazaNuncaSeRedondea() {
		assertThatThrownBy(() -> Dinero.normalizar(new BigDecimal("450.005")))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("El monto debe tener como máximo 2 decimales.");
	}

	@Test
	void rangoIncluyeLosExtremos() {
		assertThat(Dinero.positivo(new BigDecimal("0.01"), "la pensión")).hasToString("0.01");
		assertThat(Dinero.positivo(new BigDecimal("99999.99"), "la pensión")).hasToString("99999.99");
		assertThatThrownBy(() -> Dinero.positivo(new BigDecimal("100000.00"), "la pensión"))
				.hasMessage("La pensión debe estar entre S/ 0.01 y S/ 99,999.99.");
		assertThatThrownBy(() -> Dinero.positivo(BigDecimal.ZERO, "la pensión")).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void sumaExactaSinDouble() {
		BigDecimal suma = Dinero.sumar(List.of(new BigDecimal("0.10"), new BigDecimal("0.20")));
		assertThat(suma).hasToString("0.30");
		assertThat(Dinero.iguales(suma, new BigDecimal("0.3"))).isTrue();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"1250    | S/ 1,250.00",
			"0       | S/ 0.00",
			"450.5   | S/ 450.50",
			"1234567.89 | S/ 1,234,567.89",
			"-35     | -S/ 35.00" })
	void formateaComoSePagaEnPeru(String monto, String esperado) {
		assertThat(Dinero.formatear(new BigDecimal(monto))).isEqualTo(esperado);
	}

	@Test
	void ningunMetodoNiCampoUsaDouble() {
		assertThat(Arrays.stream(Dinero.class.getDeclaredMethods()).flatMap(m -> {
			Class<?>[] tipos = m.getParameterTypes();
			return java.util.stream.Stream.concat(Arrays.stream(tipos), java.util.stream.Stream.of(m.getReturnType()));
		})).doesNotContain(double.class, Double.class, float.class, Float.class);
		assertThat(Arrays.stream(Dinero.class.getDeclaredFields()).map(Field::getType))
				.doesNotContain(double.class, Double.class);
		assertThat(Arrays.stream(Dinero.class.getDeclaredMethods()).map(Method::getName)).isNotEmpty();
	}
}
