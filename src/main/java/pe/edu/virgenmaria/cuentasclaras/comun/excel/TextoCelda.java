package pe.edu.virgenmaria.cuentasclaras.comun.excel;

/**
 * Texto de una celda de reporte (sprint 6, sección 10.2). Lo que escribió una persona (motivos, nombres) puede llegar a
 * una celda; Excel, LibreOffice y Google Sheets ejecutan como fórmula lo que empieza con {@code = + - @}, una
 * tabulación, un retorno, {@code |} o {@code %}, también con sus variantes de ancho completo. Defensa doble:
 * <ul>
 *   <li>se quitan los caracteres de control (U+0000 a U+001F, U+007F, U+2028 y U+2029) y se recorta a 500;</li>
 *   <li>si es peligroso, se guarda con un apóstrofo delante <b>y</b> la celda lleva el prefijo de comilla
 *       ({@code setQuotePrefixed}): si el contador pasa el archivo a CSV, el apóstrofo sigue ahí.</li>
 * </ul>
 * (En el diseño se llama {@code TextoSeguro}; ese nombre ya lo usa {@code comun.texto.TextoSeguro}.)
 */
final class TextoCelda {

	static final int MAXIMO = 500;

	private static final String PELIGROSOS = "=+-@\t\r\n|%＝＋－＠";

	record Resultado(String texto, boolean conComilla) {
	}

	private TextoCelda() {
	}

	static Resultado preparar(String original) {
		if (original == null || original.isEmpty()) {
			return new Resultado("", false);
		}
		boolean peligroso = empiezaPeligroso(original) || empiezaPeligroso(original.stripLeading());
		StringBuilder limpio = new StringBuilder(Math.min(original.length(), MAXIMO + 1));
		original.codePoints().filter(cp -> !esControl(cp)).forEach(limpio::appendCodePoint);
		String texto = limpio.toString();
		peligroso = peligroso || empiezaPeligroso(texto) || empiezaPeligroso(texto.stripLeading());
		if (texto.codePointCount(0, texto.length()) > MAXIMO) {
			texto = texto.substring(0, texto.offsetByCodePoints(0, MAXIMO));
		}
		return peligroso ? new Resultado("'" + texto, true) : new Resultado(texto, false);
	}

	static boolean esControl(int cp) {
		return cp <= 0x1F || cp == 0x7F || cp == 0x2028 || cp == 0x2029;
	}

	private static boolean empiezaPeligroso(String texto) {
		return !texto.isEmpty() && PELIGROSOS.indexOf(texto.charAt(0)) >= 0;
	}
}
