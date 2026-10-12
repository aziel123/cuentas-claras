package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Qué archivos de alumnos se pueden importar. Fuera de la demo, cualquiera (lo valida y lo lee el importador). Sprint 8,
 * tanda 3 (decisión 127, E22, Ley 29733): en la demo, SOLO los dos archivos de ejemplo empaquetados en el jar, reconocidos
 * por su SHA-256: así un prospecto no sube a la demo la lista real de sus alumnos.
 */
public interface ArchivosPermitidosImportacion {

	/** Los archivos de ejemplo de la demo, dentro del jar ({@code classpath:demo/}). */
	List<String> EJEMPLOS = List.of("ejemplo-importacion.xlsx", "ejemplo-importacion-corregido.xlsx");

	/** @throws ReglaNegocioException si ese archivo (por su SHA-256) no se puede importar aquí */
	void exigirPermitido(String sha256);

	/** Fuera de la demo: el importador decide. */
	@Component
	@Profile("!demo")
	class Todos implements ArchivosPermitidosImportacion {

		@Override
		public void exigirPermitido(String sha256) {
			// Sin restricción por archivo.
		}
	}

	/** En la demo: solo los ejemplos, por su huella. */
	@Component
	@Profile("demo")
	class SoloEjemplosDemo implements ArchivosPermitidosImportacion {

		static final String MENSAJE = "En la demo solo se importa el archivo de ejemplo (descárgalo en /demo/ejemplos/"
				+ EJEMPLOS.getFirst() + "). No subas la lista real de tus alumnos: la demo es solo con datos ficticios.";

		private final Map<String, String> huellas;

		public SoloEjemplosDemo() {
			this.huellas = EJEMPLOS.stream().collect(Collectors.toUnmodifiableMap(SoloEjemplosDemo::huella,
					Function.identity()));
		}

		@Override
		public void exigirPermitido(String sha256) {
			if (sha256 == null || !huellas.containsKey(sha256)) {
				throw new ReglaNegocioException(MENSAJE);
			}
		}

		static String huella(String archivo) {
			try (InputStream entrada = new ClassPathResource("demo/" + archivo).getInputStream()) {
				return PlanificadorImportacion.sha256(entrada.readAllBytes());
			}
			catch (IOException e) {
				throw new UncheckedIOException("Falta el archivo de ejemplo de la demo " + archivo, e);
			}
		}
	}
}
