package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Limpieza de textos que escribe una persona o que llegan de un Excel. Puro: sin estado ni dependencias.
 */
public final class Normalizador {

	/** Caracter de escape de {@link #escaparLike}: las consultas usan {@code like :x escape '!'}. */
	public static final char ESCAPE_LIKE = '!';

	/** Controles (incluido el tabulador y los saltos de línea), formatos invisibles (ancho cero, BOM) y separadores raros. */
	private static final Pattern INVISIBLES = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]");

	/** Cualquier espacio, incluido el espacio duro (NBSP) y los espacios tipográficos. */
	private static final Pattern ESPACIOS = Pattern.compile("[\\s\\p{Zs}]+");

	private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

	private Normalizador() {
	}

	/**
	 * Forma NFC, sin caracteres invisibles, con los espacios (también NBSP) colapsados en uno y sin espacios
	 * alrededor. {@code null} si no queda nada.
	 */
	public static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = Normalizer.normalize(texto, Normalizer.Form.NFC);
		limpio = ESPACIOS.matcher(limpio).replaceAll(" ");
		limpio = INVISIBLES.matcher(limpio).replaceAll("");
		limpio = ESPACIOS.matcher(limpio).replaceAll(" ").strip();
		return limpio.isEmpty() ? null : limpio;
	}

	/** Como {@link #limpiar}, pero sin ningún espacio: documentos y teléfonos. */
	public static String sinEspacios(String texto) {
		String limpio = limpiar(texto);
		return limpio == null ? null : limpio.replace(" ", "");
	}

	/**
	 * Clave de búsqueda: las partes limpias unidas por un espacio, sin tildes (la ñ pasa a N) y en mayúsculas.
	 * «Quispe Huamán», «Mateo» → «QUISPE HUAMAN MATEO».
	 */
	public static String paraBusqueda(String... partes) {
		String unido = Arrays.stream(partes).map(Normalizador::limpiar).filter(Objects::nonNull)
				.collect(Collectors.joining(" "));
		String sinTildes = MARCAS.matcher(Normalizer.normalize(unido, Normalizer.Form.NFD)).replaceAll("");
		return sinTildes.toUpperCase(Locale.ROOT);
	}

	/** Escapa el caracter de escape, {@code %} y {@code _} para usar el texto dentro de un {@code like}. */
	public static String escaparLike(String texto) {
		if (texto == null) {
			return null;
		}
		StringBuilder escapado = new StringBuilder(texto.length() + 4);
		for (char c : texto.toCharArray()) {
			if (c == ESCAPE_LIKE || c == '%' || c == '_') {
				escapado.append(ESCAPE_LIKE);
			}
			escapado.append(c);
		}
		return escapado.toString();
	}
}
