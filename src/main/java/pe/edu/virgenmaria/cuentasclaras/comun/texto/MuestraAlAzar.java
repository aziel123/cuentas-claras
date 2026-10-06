package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * La muestra fija de una confirmación a ciegas (correcciones del sprint 4, S4-A1): se elige UNA vez, al registrar el
 * archivo, con un azar que quien lo sube no puede predecir, y se guarda (columna {@code muestra}, inmutable). Así recargar
 * la pantalla no muestra otras líneas. Nunca cubre todo el archivo: con {@code n} líneas, a lo más {@code n - 1}.
 */
public final class MuestraAlAzar {

	private static final SecureRandom AZAR = new SecureRandom();

	/** Lo más largo que cabe en la columna {@code VARCHAR(100)}. */
	static final int LARGO_MAXIMO = 100;

	private MuestraAlAzar() {
	}

	/**
	 * @param total  cuántas líneas tiene el archivo (numeradas de 1 a {@code total})
	 * @param maximo cuántas mostrar como máximo
	 * @return los números elegidos, ordenados y separados por comas (vacío si el archivo tiene 1 línea o menos)
	 */
	public static String elegir(int total, int maximo) {
		int cuantas = Math.max(0, Math.min(maximo, total - 1));
		if (cuantas == 0) {
			return "";
		}
		List<Integer> numeros = new ArrayList<>(IntStream.rangeClosed(1, total).boxed().toList());
		Collections.shuffle(numeros, AZAR);
		String texto = numeros.subList(0, cuantas).stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		if (texto.length() > LARGO_MAXIMO) {
			throw new IllegalArgumentException("La muestra no cabe en la columna");
		}
		return texto;
	}

	/** Una semilla secreta para el muestreo diario (no se deriva de la fecha: nadie puede predecirla). */
	public static long semilla() {
		return AZAR.nextLong();
	}

	/** Los números de una muestra guardada. */
	public static List<Integer> numeros(String muestra) {
		if (muestra == null || muestra.isBlank()) {
			return List.of();
		}
		return Arrays.stream(muestra.split(",")).map(String::strip).map(Integer::valueOf).toList();
	}
}
