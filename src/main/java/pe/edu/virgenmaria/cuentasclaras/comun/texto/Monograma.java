package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.Arrays;
import java.util.Locale;

/**
 * Las iniciales de la barra cuando el colegio no subió su logo: la primera letra de las dos últimas palabras del nombre
 * («Colegio Virgen María» da «VM»), o la primera de la única palabra. Auditoría del PR #19 (hallazgo 3): se calcula en
 * Java y no en la plantilla, y un nombre vacío o con solo espacios da {@link #POR_DEFECTO} en vez de un error.
 */
public final class Monograma {

	/** Lo que se muestra si no hay ningún nombre con letras. */
	public static final String POR_DEFECTO = "C";

	private Monograma() {
	}

	/** Iniciales en mayúsculas del primer nombre con contenido, o {@link #POR_DEFECTO}. */
	public static String de(String... nombres) {
		for (String nombre : nombres) {
			if (nombre == null || nombre.isBlank()) {
				continue;
			}
			String[] palabras = Arrays.stream(nombre.strip().split("\\s+"))
					.filter(p -> !p.isEmpty() && Character.isLetterOrDigit(p.codePointAt(0))).toArray(String[]::new);
			if (palabras.length == 0) {
				continue;
			}
			String iniciales = palabras.length == 1 ? inicial(palabras[0])
					: inicial(palabras[palabras.length - 2]) + inicial(palabras[palabras.length - 1]);
			return iniciales.toUpperCase(Locale.forLanguageTag("es-PE"));
		}
		return POR_DEFECTO;
	}

	private static String inicial(String palabra) {
		return new String(Character.toChars(palabra.codePointAt(0)));
	}
}
