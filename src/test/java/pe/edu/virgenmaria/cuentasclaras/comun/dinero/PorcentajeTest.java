package pe.edu.virgenmaria.cuentasclaras.comun.dinero;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Observación de QA del sprint 6: «100 %» y «0 %» solo cuando son exactos. */
class PorcentajeTest {

	@Test
	void unValorQueNoEsCienNoSeRedondeaACien() {
		assertThat(Porcentaje.de(999, 1000)).hasValue(99);
		assertThat(Porcentaje.de(new BigDecimal("1999.99"), new BigDecimal("2000.00"))).hasValue(99);
		assertThat(Porcentaje.de(1000, 1000)).hasValue(100);
	}

	@Test
	void unValorQueNoEsCeroNoSeRedondeaACero() {
		assertThat(Porcentaje.de(1, 1000)).hasValue(1);
		assertThat(Porcentaje.de(0, 1000)).hasValue(0);
	}

	@Test
	void redondeaHalfUpYSinTotalNoHayPorcentaje() {
		assertThat(Porcentaje.de(2, 3)).hasValue(67);
		assertThat(Porcentaje.de(1, 8)).hasValue(13);
		assertThat(Porcentaje.de(0, 0)).isEmpty();
		assertThat(Porcentaje.de(BigDecimal.ONE, BigDecimal.ZERO)).isEmpty();
	}
}
