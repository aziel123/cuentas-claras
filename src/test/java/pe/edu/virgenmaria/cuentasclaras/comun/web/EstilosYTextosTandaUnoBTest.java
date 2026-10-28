package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 1b: reglas estáticas de los estilos y los textos.
 * <ul>
 *   <li>TokensCss: el armazón llega a 1760 px y existe el margen lateral (decisión 208).</li>
 *   <li>CssMovimientoReducido: toda {@code transition} o {@code animation} está dentro de
 *       {@code @media (prefers-reduced-motion: no-preference)}.</li>
 *   <li>TextosEstados: «En preparación» solo nombra al colegio (F-D4).</li>
 *   <li>PlantillasSinAnchoFijo: los inicios no ponen un {@code style} con ancho.</li>
 *   <li>PlantillasSinTitleSolo: ninguna plantilla usa {@code title=} como ayuda (las ayudas van en el «i»).</li>
 * </ul>
 */
class EstilosYTextosTandaUnoBTest {

	private static final Path CSS = Path.of("src/main/resources/static/css");

	private static final Path PLANTILLAS = Path.of("src/main/resources/templates");

	@Test
	void tokensConElAnchoDe1760YElMargenLateral() throws IOException {
		String tokens = Files.readString(CSS.resolve("tokens.css"));
		assertThat(tokens).contains("--ancho-max: 1760px;").contains("--margen-lateral: clamp(24px, 2vw, 32px);");
		String app = Files.readString(CSS.resolve("app.css"));
		assertThat(app).contains("var(--margen-lateral)").doesNotContain("72ch");
	}

	@Test
	void elMovimientoSoloSinPreferenciaDeReducirlo() throws IOException {
		List<String> problemas = new ArrayList<>();
		try (Stream<Path> archivos = Files.list(CSS)) {
			for (Path archivo : archivos.filter(p -> p.toString().endsWith(".css")).toList()) {
				String css = Files.readString(archivo).replaceAll("(?s)/\\*.*?\\*/", "");
				String fuera = sinBloquesMovimiento(css);
				Matcher m = Pattern.compile("(?<![-\\w])(transition|animation)\\s*:").matcher(fuera);
				while (m.find()) {
					problemas.add(archivo.getFileName() + ": " + m.group(1) + " fuera de prefers-reduced-motion");
				}
			}
		}
		assertThat(problemas).isEmpty();
	}

	@Test
	void enPreparacionSoloNombraAlColegio() throws Exception {
		List<String> otras = new ArrayList<>();
		try (Stream<Path> fuentes = Files.walk(Path.of("src/main/java/pe/edu/virgenmaria/cuentasclaras"))) {
			for (Path f : fuentes.filter(p -> p.getFileName().toString().startsWith("Estado") && p.toString().endsWith(".java"))
					.toList()) {
				String nombre = Path.of("src/main/java").relativize(f).toString().replace('\\', '.').replace('/', '.')
						.replaceAll("\\.java$", "");
				Class<?> clase = Class.forName(nombre);
				if (!clase.isEnum() || clase == EstadoColegio.class) {
					continue;
				}
				Method etiqueta;
				try {
					etiqueta = clase.getMethod("etiqueta");
				}
				catch (NoSuchMethodException e) {
					continue;
				}
				for (Object valor : clase.getEnumConstants()) {
					if ("En preparación".equals(etiqueta.invoke(valor))) {
						otras.add(clase.getSimpleName() + "." + valor);
					}
				}
			}
		}
		assertThat(otras).as("«En preparación» es solo el estado del colegio").isEmpty();
		assertThat(EstadoColegio.PREPARACION.etiqueta()).isEqualTo("En preparación");
	}

	@Test
	void losIniciosNoFijanAnchosEnLinea() throws IOException {
		try (Stream<Path> inicios = Files.list(PLANTILLAS.resolve("inicio"))) {
			for (Path p : inicios.toList()) {
				assertThat(Files.readString(p)).as(p.toString()).doesNotContainPattern("style=\"[^\"]*width");
			}
		}
	}

	@Test
	void ningunaPlantillaUsaTitleComoAyuda() throws IOException {
		List<String> con = new ArrayList<>();
		try (Stream<Path> todas = Files.walk(PLANTILLAS)) {
			for (Path p : todas.filter(f -> f.toString().endsWith(".html")).toList()) {
				// <title> del documento y de los SVG está bien, y <abbr title> (la abreviatura con su nombre, que además va
				// para el lector de pantalla); el atributo title= como ayuda de otro elemento, no.
				if (Pattern.compile("<(?!title|abbr)[a-z][^>]*\\s(th:)?title=").matcher(Files.readString(p)).find()) {
					con.add(PLANTILLAS.relativize(p).toString());
				}
			}
		}
		assertThat(con).isEmpty();
	}

	/** El CSS sin el contenido de los @media (prefers-reduced-motion: no-preference) { … } (con llaves anidadas). */
	private static String sinBloquesMovimiento(String css) {
		StringBuilder fuera = new StringBuilder();
		int i = 0;
		Pattern inicio = Pattern.compile("@media\\s*\\(\\s*prefers-reduced-motion\\s*:\\s*no-preference\\s*\\)\\s*\\{");
		Matcher m = inicio.matcher(css);
		while (m.find(i)) {
			fuera.append(css, i, m.start());
			int nivel = 1;
			int j = m.end();
			while (j < css.length() && nivel > 0) {
				char c = css.charAt(j++);
				if (c == '{') {
					nivel++;
				}
				else if (c == '}') {
					nivel--;
				}
			}
			i = j;
		}
		fuera.append(css.substring(i));
		return fuera.toString();
	}
}
