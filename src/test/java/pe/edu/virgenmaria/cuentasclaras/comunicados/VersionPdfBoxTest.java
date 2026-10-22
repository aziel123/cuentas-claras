package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.apache.pdfbox.util.Version;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (decisión 157): PDFBox va en la última 3.x estable y NUNCA anterior a 3.0.7 (que corrige
 * CVE-2026-23907). La versión está fijada en el pom.xml y es la que corre.
 */
class VersionPdfBoxTest {

	@Test
	void pdfboxEsTresPuntoCeroPuntoSieteOMas() throws Exception {
		String pom = Files.readString(Path.of("pom.xml"));
		Matcher m = Pattern.compile("<pdfbox\\.version>(\\d+)\\.(\\d+)\\.(\\d+)</pdfbox\\.version>").matcher(pom);
		assertThat(m.find()).as("pdfbox.version fijada en el pom").isTrue();
		assertThat(Integer.parseInt(m.group(1))).isEqualTo(3);
		assertThat(Integer.parseInt(m.group(2)) > 0 || Integer.parseInt(m.group(3)) >= 7).as("nunca anterior a 3.0.7")
				.isTrue();
		assertThat(Version.getVersion()).isEqualTo(m.group(1) + "." + m.group(2) + "." + m.group(3));
	}
}
