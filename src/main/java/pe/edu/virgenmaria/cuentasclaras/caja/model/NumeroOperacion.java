package pe.edu.virgenmaria.cuentasclaras.caja.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.util.Locale;
import java.util.regex.Pattern;

/** Número de operación de un Yape, Plin, transferencia o voucher: en mayúsculas, sin espacios, de 4 a 30 caracteres. */
public final class NumeroOperacion {

	private static final Pattern FORMATO = Pattern.compile("^[A-Z0-9-]{4,30}$");

	private NumeroOperacion() {
	}

	public static String normalizar(String numero) {
		String limpio = Normalizador.sinEspacios(numero);
		if (limpio == null) {
			throw new ReglaNegocioException(
					"Escribe el número de operación del Yape, Plin, transferencia o voucher (es obligatorio).");
		}
		limpio = limpio.toUpperCase(Locale.ROOT);
		if (!FORMATO.matcher(limpio).matches()) {
			throw new ReglaNegocioException("El número de operación debe tener de 4 a 30 letras, números o guiones.");
		}
		return limpio;
	}
}
