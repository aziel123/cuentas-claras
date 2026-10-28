package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (pendiente de la 1b): en Thymeleaf {@code th:replace} se procesa ANTES que {@code th:if} y
 * {@code th:unless}, así que en el mismo elemento la condición no impide el fragmento. La condición va en un
 * {@code <th:block th:if="…">} que lo envuelve. Ninguna plantilla tiene los dos atributos en un mismo elemento.
 */
class PlantillasIfReplaceTest {

	private static final Pattern ETIQUETA = Pattern.compile("<[\\w:]+(\\s+[\\w:-]+=\"[^\"]*\")*\\s*/?>", Pattern.DOTALL);

	@Test
	void ningunElementoTieneThIfYThReplaceALaVez() throws IOException {
		List<String> malos = new ArrayList<>();
		try (Stream<Path> plantillas = Files.walk(Path.of("src/main/resources/templates"))) {
			for (Path p : plantillas.filter(x -> x.toString().endsWith(".html")).toList()) {
				Matcher m = ETIQUETA.matcher(Files.readString(p));
				while (m.find()) {
					String etiqueta = m.group();
					boolean condicion = etiqueta.contains("th:if=\"") || etiqueta.contains("th:unless=\"");
					boolean fragmento = etiqueta.contains("th:replace=\"") || etiqueta.contains("th:insert=\"");
					if (condicion && fragmento) {
						malos.add(p + ": " + etiqueta.substring(0, Math.min(120, etiqueta.length())));
					}
				}
			}
		}
		assertThat(malos).as("envuelve el fragmento en <th:block th:if=\"…\">").isEmpty();
	}
}
