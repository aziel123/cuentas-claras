package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 8, tanda 1 (sección 3.3): 11 dígitos, prefijo 10 o 20 y dígito verificador de SUNAT. */
class ValidadorRucTest {

	@Test
	void aceptaUnRucValidoYLoLimpia() {
		assertThat(ValidadorRuc.validar("20131312955")).isEqualTo("20131312955");
		assertThat(ValidadorRuc.validar(" 20600000013 ")).isEqualTo("20600000013");
		assertThat(ValidadorRuc.validar("10400000013")).isEqualTo("10400000013");
	}

	@Test
	void rechazaElLargoElPrefijoYElDigito() {
		assertThatThrownBy(() -> ValidadorRuc.validar("2013131295")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("11 dígitos");
		// 15, 16 y 17 son RUC válidos de SUNAT, pero un colegio es persona natural con negocio (10) o jurídica (20).
		assertThatThrownBy(() -> ValidadorRuc.validar("15000000001")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("10 o 20");
		assertThatThrownBy(() -> ValidadorRuc.validar("20600000014")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es válido");
		assertThat(ValidadorRuc.valido(null)).isFalse();
	}
}
