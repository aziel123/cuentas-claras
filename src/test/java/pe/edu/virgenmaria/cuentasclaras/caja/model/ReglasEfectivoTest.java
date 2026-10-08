package pe.edu.virgenmaria.cuentasclaras.caja.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vuelto, múltiplos de S/ 0.10 en efectivo y número de operación (diseño, sección 10.4). */
class ReglasEfectivoTest {

	@Test
	void vueltoDe100Con1000Por900() {
		BigDecimal vuelto = ReglasEfectivo.vuelto(new BigDecimal("900.00"), new BigDecimal("1000"));

		assertThat(vuelto).isEqualByComparingTo("100.00");
		assertThat(vuelto.scale()).isEqualTo(2);
		assertThat(ReglasEfectivo.vuelto(new BigDecimal("450.00"), new BigDecimal("450.00"))).isEqualByComparingTo("0.00");
	}

	@Test
	void vueltoDe200OMasEsRechazado() {
		assertThatThrownBy(() -> ReglasEfectivo.vuelto(new BigDecimal("450.00"), new BigDecimal("650.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("S/ 200.00 o más");
		assertThat(ReglasEfectivo.vuelto(new BigDecimal("450.00"), new BigDecimal("649.90"))).isEqualByComparingTo("199.90");
		assertThatThrownBy(() -> ReglasEfectivo.vuelto(new BigDecimal("450.00"), new BigDecimal("400.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no alcanza");
		assertThatThrownBy(() -> ReglasEfectivo.vuelto(new BigDecimal("450.00"), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cuánto te entregó");
	}

	@ParameterizedTest
	@ValueSource(strings = { "399.99", "450.05", "0.01" })
	void efectivoConCentimosNoMultiplosDe10EsRechazado(String total) {
		assertThatThrownBy(() -> ReglasEfectivo.vuelto(new BigDecimal(total), new BigDecimal("500.00")))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage(ReglasEfectivo.NO_EXACTO);
		// Lo recibido tampoco puede tener céntimos sueltos.
		assertThatThrownBy(() -> ReglasEfectivo.vuelto(new BigDecimal("450.00"), new BigDecimal("500.05")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("múltiplo de S/ 0.10");
	}

	@Test
	void numeroDeOperacionSeNormalizaEnMayusculas() {
		// C1: forma canónica (solo letras y dígitos, en mayúsculas, sin ceros a la izquierda).
		assertThat(NumeroOperacion.normalizar("  ab 12-cd34 ")).isEqualTo("AB12CD34");
		assertThat(NumeroOperacion.normalizar("00918273")).isEqualTo("918273");
		assertThatThrownBy(() -> NumeroOperacion.normalizar(" ")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("obligatorio");
		assertThatThrownBy(() -> NumeroOperacion.normalizar("12")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> NumeroOperacion.normalizar("=1+2;DROP")).isInstanceOf(ReglaNegocioException.class);
	}
}
