package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUC: 11 dígitos, prefijo válido y dígito verificador módulo 11 (diseño, hallazgo 10). */
class RucTest {

	@Test
	void rucDeSunatEsValido() {
		assertThat(Ruc.valido("20131312955")).isTrue();
		// Persona natural con negocio (prefijo 10): 10 → 0 en el dígito verificador.
		assertThat(Ruc.valido("10464364990")).isTrue();
		assertThat(Receptor.de(DocumentoReceptor.RUC, " 20131312955 ", "  Superintendencia  Nacional ").nombre())
				.isEqualTo("Superintendencia Nacional");
	}

	@Test
	void digitoVerificadorAlteradoEsRechazado() {
		assertThat(Ruc.valido("20131312954")).isFalse();
		assertThatThrownBy(() -> Receptor.de(DocumentoReceptor.RUC, "20131312954", "Empresa"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("RUC no es válido");
	}

	@Test
	void rucConPrefijoInvalidoEsRechazado() {
		assertThat(Ruc.valido("30131312955")).isFalse();
		assertThat(Ruc.valido("2013131295")).isFalse();
		assertThat(Ruc.valido("2013131295A")).isFalse();
		assertThat(Ruc.valido(null)).isFalse();
		// Una razón social que Excel ejecutaría como fórmula tampoco.
		assertThatThrownBy(() -> Receptor.de(DocumentoReceptor.RUC, "20131312955", "=HIPERVINCULO(\"x\")"))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
