package pe.edu.virgenmaria.cuentasclaras.caja.model;

import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * El total contado por la cajera: el que escribe o, si llenó las denominaciones (opcional), la suma que hace el
 * servidor. Si escribió las dos cosas y no cuadran, se le pide revisar. Siempre múltiplo de S/ 0.10.
 *
 * @param total el total contado
 * @param denominaciones «B200×2, B50×1, M1×3» o {@code null} si contó solo el total
 */
public record ConteoEfectivo(BigDecimal total, String denominaciones) {

	private static final int MAX_PIEZAS = 10_000;

	public static ConteoEfectivo de(BigDecimal contado, Map<Denominacion, Integer> denominaciones) {
		boolean conDenominaciones = denominaciones != null
				&& denominaciones.values().stream().anyMatch(n -> n != null && n > 0);
		if (!conDenominaciones) {
			if (contado == null) {
				throw new ReglaNegocioException("Escribe cuánto efectivo contaste (incluye el fondo fijo).");
			}
			return new ConteoEfectivo(validar(contado), null);
		}
		BigDecimal suma = Dinero.CERO;
		for (Map.Entry<Denominacion, Integer> e : denominaciones.entrySet()) {
			Integer piezas = e.getValue();
			if (piezas == null || piezas == 0) {
				continue;
			}
			if (piezas < 0 || piezas > MAX_PIEZAS) {
				throw new ReglaNegocioException("Revisa la cantidad de «" + e.getKey().etiqueta() + "».");
			}
			suma = suma.add(e.getKey().valor().multiply(BigDecimal.valueOf(piezas)));
		}
		suma = Dinero.normalizar(suma);
		if (contado != null && !Dinero.iguales(contado, suma)) {
			throw new ReglaNegocioException("El total que escribiste (" + Dinero.formatear(contado) + ") no es la suma de "
					+ "los billetes y monedas (" + Dinero.formatear(suma) + "). Revisa el conteo.");
		}
		String texto = Stream.of(Denominacion.values()).filter(d -> denominaciones.getOrDefault(d, 0) != null
				&& denominaciones.getOrDefault(d, 0) > 0)
				.map(d -> d.name() + "×" + denominaciones.get(d)).collect(Collectors.joining(", "));
		return new ConteoEfectivo(validar(suma), texto);
	}

	private static BigDecimal validar(BigDecimal contado) {
		if (contado.signum() < 0) {
			throw new ReglaNegocioException("El conteo no puede ser negativo.");
		}
		if (contado.compareTo(Dinero.MAXIMO_TOTAL) > 0) {
			throw new ReglaNegocioException("Ese conteo es demasiado grande: revísalo.");
		}
		return Dinero.exigirDecimos(Dinero.normalizar(contado),
				"El conteo debe ser múltiplo de S/ 0.10 (no hay monedas de 1 ni 5 céntimos).");
	}
}
