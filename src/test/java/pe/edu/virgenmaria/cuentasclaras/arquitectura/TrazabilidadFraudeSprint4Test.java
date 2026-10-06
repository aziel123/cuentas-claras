package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 4 (QA, trazabilidad): cada prueba que nombra la sección 14 del diseño del sprint 4 («Escenarios
 * de fraude, su control y la prueba que lo demuestra») existe con ese nombre. Antes nombraba unas 40 que no existían.
 * Lee el documento y las fuentes de prueba (sin cargar Spring).
 */
class TrazabilidadFraudeSprint4Test {

	private static final Path DISENO = Path.of("docs/arquitectura/sprint-4-cero-digitacion.md");

	private static final Path PRUEBAS = Path.of("src/test/java");

	private static final Pattern NOMBRE = Pattern.compile("`([A-Za-z0-9.]+)`");

	@Test
	void cadaPruebaQueNombraLaSeccion14Existe() throws IOException {
		Map<String, Set<String>> metodos = metodosPorClase();
		List<String> lineas = Files.readAllLines(DISENO, StandardCharsets.UTF_8);
		int inicio = indice(lineas, "## 14.");
		int fin = indice(lineas, "## 15.");
		List<String> faltan = new ArrayList<>();
		int referencias = 0;
		for (String linea : lineas.subList(inicio, fin)) {
			if (!linea.startsWith("| F")) {
				continue;
			}
			String[] columnas = linea.split("\\|");
			String escenario = columnas[1].strip();
			String clase = null;
			Matcher m = NOMBRE.matcher(columnas[4]);
			while (m.find()) {
				String nombre = m.group(1);
				String metodo = null;
				if (nombre.contains(".")) {
					clase = nombre.substring(0, nombre.indexOf('.'));
					metodo = nombre.substring(nombre.indexOf('.') + 1);
				}
				else if (Character.isUpperCase(nombre.charAt(0))) {
					clase = nombre;
				}
				else {
					metodo = nombre;
				}
				referencias++;
				if (clase == null || !metodos.containsKey(clase) || (metodo != null && !metodos.get(clase).contains(metodo))) {
					faltan.add(escenario + ": " + clase + (metodo == null ? "" : "." + metodo));
				}
			}
		}
		assertThat(referencias).as("la sección 14 nombra pruebas").isGreaterThan(80);
		assertThat(faltan).as("pruebas de la sección 14 que no existen").isEmpty();
	}

	private static int indice(List<String> lineas, String prefijo) {
		for (int i = 0; i < lineas.size(); i++) {
			if (lineas.get(i).startsWith(prefijo)) {
				return i;
			}
		}
		throw new IllegalStateException("No está la sección " + prefijo);
	}

	private static Map<String, Set<String>> metodosPorClase() throws IOException {
		Pattern metodo = Pattern.compile("(?:void|ArchRule) (\\w+)");
		Map<String, Set<String>> mapa = new HashMap<>();
		try (Stream<Path> archivos = Files.walk(PRUEBAS)) {
			for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
				String clase = archivo.getFileName().toString().replace(".java", "");
				Set<String> nombres = mapa.computeIfAbsent(clase, c -> new HashSet<>());
				Matcher m = metodo.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
				while (m.find()) {
					nombres.add(m.group(1));
				}
			}
		}
		return mapa;
	}
}
