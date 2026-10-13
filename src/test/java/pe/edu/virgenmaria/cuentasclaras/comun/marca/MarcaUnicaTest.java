package pe.edu.virgenmaria.cuentasclaras.comun.marca;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisiones 120 a 122; escenarios E18 y E19): el nombre comercial vive en UN solo
 * parámetro y el nombre del colegio cliente nunca va fijo en el código. Recorre las plantillas, los
 * {@code messages*.properties} y las clases de {@code src/main/java}; falla si aparece «Cuentas Claras» fuera de
 * {@link Marca#NOMBRE_POR_DEFECTO}, o «Virgen María» en una cadena de texto o en una plantilla.
 * <p>
 * No cuenta lo que no se ve: los comentarios ({@code //}, {@code /* *&#47;}, javadoc y {@code <!-- -->}), el texto de
 * muestra de un elemento con {@code th:text} (Thymeleaf lo reemplaza siempre), el paquete {@code virgenmaria} y los
 * datos de demostración ({@code *DemoDev*}), que nombran al colegio como dato.
 */
class MarcaUnicaTest {

	private static final Path JAVA = Path.of("src/main/java");

	private static final Path RECURSOS = Path.of("src/main/resources");

	private static final Pattern COMENTARIO_BLOQUE = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

	private static final Pattern COMENTARIO_LINEA = Pattern.compile("//[^\\r\\n]*");

	private static final Pattern COMENTARIO_HTML = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

	/** El texto de muestra de un elemento con th:text nunca se muestra: Thymeleaf lo reemplaza. */
	private static final Pattern MUESTRA_TH_TEXT = Pattern.compile("(th:text=\"[^\"]*\"[^>]*>)[^<]*");

	@Test
	void laMarcaSoloViveEnElValorPorDefectoDeMarca() throws IOException {
		List<String> hallazgos = new ArrayList<>();
		for (Path archivo : archivos(JAVA, ".java")) {
			if (archivo.getFileName().toString().equals("Marca.java")) {
				continue;
			}
			buscar(archivo, sinComentariosJava(Files.readString(archivo, StandardCharsets.UTF_8)), "Cuentas Claras", hallazgos);
		}
		for (Path archivo : plantillasYMensajes()) {
			buscar(archivo, sinMuestrasDePlantilla(Files.readString(archivo, StandardCharsets.UTF_8)), "Cuentas Claras",
					hallazgos);
		}
		assertThat(hallazgos).as("«Cuentas Claras» fijo fuera de Marca.NOMBRE_POR_DEFECTO (usa ${marca.nombre} o "
				+ "marca.nombre())").isEmpty();
		assertThat(Marca.NOMBRE_POR_DEFECTO).isEqualTo("Cuentas Claras");
	}

	@Test
	void ningunColegioVaFijoEnElCodigoNiEnLasPlantillas() throws IOException {
		List<String> hallazgos = new ArrayList<>();
		for (Path archivo : archivos(JAVA, ".java")) {
			if (archivo.getFileName().toString().contains("DemoDev")) {
				continue;
			}
			buscar(archivo, sinComentariosJava(Files.readString(archivo, StandardCharsets.UTF_8)), "Virgen María", hallazgos);
		}
		for (Path archivo : plantillasYMensajes()) {
			buscar(archivo, sinMuestrasDePlantilla(Files.readString(archivo, StandardCharsets.UTF_8)), "Virgen María",
					hallazgos);
		}
		assertThat(hallazgos).as("«Virgen María» fijo (el nombre del colegio sale de la base: ColegioService)").isEmpty();
	}

	private static List<Path> plantillasYMensajes() throws IOException {
		List<Path> archivos = new ArrayList<>(archivos(RECURSOS.resolve("templates"), ".html"));
		try (Stream<Path> propiedades = Files.list(RECURSOS)) {
			propiedades.filter(p -> p.getFileName().toString().matches("messages.*\\.properties")).forEach(archivos::add);
		}
		assertThat(archivos).as("se encontraron las plantillas").hasSizeGreaterThan(10);
		return archivos;
	}

	private static List<Path> archivos(Path raiz, String extension) throws IOException {
		try (Stream<Path> paseo = Files.walk(raiz)) {
			return paseo.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(extension)).sorted().toList();
		}
	}

	private static String sinComentariosJava(String fuente) {
		return COMENTARIO_LINEA.matcher(COMENTARIO_BLOQUE.matcher(fuente).replaceAll(" ")).replaceAll(" ");
	}

	private static String sinMuestrasDePlantilla(String fuente) {
		return MUESTRA_TH_TEXT.matcher(COMENTARIO_HTML.matcher(fuente).replaceAll(" ")).replaceAll("$1");
	}

	private static void buscar(Path archivo, String contenido, String texto, List<String> hallazgos) {
		String[] lineas = contenido.split("\\r?\\n");
		for (int i = 0; i < lineas.length; i++) {
			if (lineas[i].contains(texto)) {
				hallazgos.add(archivo + ":" + (i + 1) + ": " + lineas[i].strip());
			}
		}
	}
}
