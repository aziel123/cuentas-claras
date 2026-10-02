package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Textos libres que alguien podría abrir en Excel (exportaciones, reportes): no pueden empezar con {@code =}, {@code +},
 * {@code -} ni {@code @}, porque Excel los ejecuta como fórmula (inyección CSV/DDE).
 */
public final class TextoSeguro {

	private TextoSeguro() {
	}

	/** @return {@code true} si el texto (ya sin espacios al inicio) empieza con {@code = + - @} */
	public static boolean pareceFormula(String texto) {
		if (texto == null) {
			return false;
		}
		String limpio = texto.stripLeading();
		return !limpio.isEmpty() && "=+-@".indexOf(limpio.charAt(0)) >= 0;
	}

	/**
	 * @param campo nombre del dato para el mensaje, en minúsculas («el motivo»)
	 * @return el mismo texto, si es seguro
	 */
	public static String exigir(String texto, String campo) {
		if (pareceFormula(texto)) {
			throw new ReglaNegocioException(Character.toUpperCase(campo.charAt(0)) + campo.substring(1)
					+ " no puede empezar con «=», «+», «-» ni «@». Escríbelo con una letra o un número al inicio.");
		}
		return texto;
	}
}
