package pe.edu.virgenmaria.cuentasclaras.caja.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Locale;

/**
 * Número de operación de un Yape, Plin, transferencia, voucher o depósito en su FORMA CANÓNICA: solo letras y dígitos
 * en mayúsculas, sin espacios, guiones ni ceros a la izquierda ({@code 0012-345} → {@code 12345}), de 4 a 30
 * caracteres. Así el mismo número no se registra dos veces cambiando el formato (auditoría del sprint 3, C1): la base
 * exige la forma canónica (CHECK) y la unicidad por colegio para todos los medios digitales juntos.
 */
public final class NumeroOperacion {

	private static final int MINIMO = 4;

	private static final int MAXIMO = 30;

	private NumeroOperacion() {
	}

	public static String normalizar(String numero) {
		if (numero == null || numero.isBlank()) {
			throw new ReglaNegocioException(
					"Escribe el número de operación del Yape, Plin, transferencia o voucher (es obligatorio).");
		}
		String limpio = numero.strip().toUpperCase(Locale.ROOT);
		if (!limpio.matches("[A-Z0-9 .\\-/]+")) {
			throw new ReglaNegocioException("El número de operación solo lleva letras y números (los espacios, puntos y "
					+ "guiones se quitan).");
		}
		String canonico = limpio.replaceAll("[^A-Z0-9]", "").replaceFirst("^0+", "");
		if (canonico.length() < MINIMO || canonico.length() > MAXIMO) {
			throw new ReglaNegocioException("El número de operación debe tener de " + MINIMO + " a " + MAXIMO
					+ " letras o números (sin contar los ceros a la izquierda).");
		}
		return canonico;
	}

	/**
	 * Dos números canónicos «parecidos»: iguales o a un solo carácter de distancia (cambiar, agregar o quitar uno). Sirve
	 * para marcar en la conciliación un número inventado a partir de uno real.
	 */
	public static boolean parecidos(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		return distancia(a, b) <= 1;
	}

	/** Distancia de Levenshtein (los números son cortos: hasta 30 caracteres). */
	static int distancia(String a, String b) {
		int[] previa = new int[b.length() + 1];
		int[] actual = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previa[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			actual[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cambio = previa[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				actual[j] = Math.min(cambio, Math.min(previa[j] + 1, actual[j - 1] + 1));
			}
			int[] t = previa;
			previa = actual;
			actual = t;
		}
		return previa[b.length()];
	}
}
