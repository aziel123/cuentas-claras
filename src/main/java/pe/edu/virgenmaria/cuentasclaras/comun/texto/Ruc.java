package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.Set;

/**
 * RUC de SUNAT: 11 dígitos, prefijo 10, 15, 16, 17 o 20 y dígito verificador (módulo 11 con pesos 5,4,3,2,7,6,5,4,3,2;
 * 10 → 0 y 11 → 1). Ejemplo válido: 20131312955.
 */
public final class Ruc {

	private static final Set<String> PREFIJOS = Set.of("10", "15", "16", "17", "20");

	private static final int[] PESOS = { 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 };

	private Ruc() {
	}

	public static boolean valido(String ruc) {
		if (ruc == null || !ruc.matches("\\d{11}") || !PREFIJOS.contains(ruc.substring(0, 2))) {
			return false;
		}
		int suma = 0;
		for (int i = 0; i < PESOS.length; i++) {
			suma += (ruc.charAt(i) - '0') * PESOS[i];
		}
		int digito = 11 - suma % 11;
		if (digito == 10) {
			digito = 0;
		}
		else if (digito == 11) {
			digito = 1;
		}
		return digito == ruc.charAt(10) - '0';
	}
}
