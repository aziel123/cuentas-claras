package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Auditoría del PR #19 (hallazgo 3): las iniciales de la barra, calculadas en Java y sin fallar con nombres vacíos. */
class MonogramaTest {

	@Test
	void usaLasDosUltimasPalabras() {
		assertThat(Monograma.de("Colegio Virgen María")).isEqualTo("VM");
		assertThat(Monograma.de("  colegio   de prueba b ")).isEqualTo("PB");
		assertThat(Monograma.de("Cuotaria")).isEqualTo("C");
		assertThat(Monograma.de("Ñandú")).isEqualTo("Ñ");
	}

	@Test
	void unNombreVacioPasaAlSiguienteYSinNingunoDaElPorDefecto() {
		assertThat(Monograma.de("", "Cuotaria")).isEqualTo("C");
		assertThat(Monograma.de("   ", "Santa Rosa")).isEqualTo("SR");
		assertThat(Monograma.de((String) null)).isEqualTo(Monograma.POR_DEFECTO);
		assertThat(Monograma.de("   ", "\t")).isEqualTo(Monograma.POR_DEFECTO);
		assertThat(Monograma.de("— ·")).isEqualTo(Monograma.POR_DEFECTO);
		assertThat(Monograma.de()).isEqualTo(Monograma.POR_DEFECTO);
	}

	@Test
	void laPlantillaYaNoCortaTextos() throws Exception {
		String layout = Files.readString(Path.of("src/main/resources/templates/fragments/layout.html"));
		assertThat(layout).doesNotContain("substring").contains("th:text=\"${monograma} ?: 'C'\"");
	}
}
