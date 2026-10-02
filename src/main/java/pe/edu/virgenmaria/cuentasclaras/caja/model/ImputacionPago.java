package pe.edu.virgenmaria.cuentasclaras.caja.model;

import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cómo se reparte un monto entre las cuotas que eligió el apoderado (Código Civil, art. 1256): se ordenan por
 * vencimiento (y por id si vencen el mismo día) y se pagan completas hasta la última, que puede quedar PARCIAL.
 * Ejemplo: 1000.00 con tres cuotas de 450.00 → 450.00, 450.00 y 100.00. Puro: sin base de datos.
 */
public final class ImputacionPago {

	/** Una cuota elegida con lo que falta pagar. */
	public record CuotaPorPagar(Long id, LocalDate vencimiento, BigDecimal saldo) {

		public CuotaPorPagar {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(vencimiento, "vencimiento");
			Objects.requireNonNull(saldo, "saldo");
		}
	}

	/** Cuánto del pago va a una cuota. */
	public record Imputacion(Long cuotaId, BigDecimal monto) {
	}

	private ImputacionPago() {
	}

	public static List<Imputacion> imputar(BigDecimal monto, List<CuotaPorPagar> elegidas) {
		BigDecimal restante = Dinero.normalizar(monto);
		if (restante.signum() <= 0) {
			throw new ReglaNegocioException("El monto a aplicar debe ser mayor que cero.");
		}
		if (elegidas == null || elegidas.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		List<CuotaPorPagar> ordenadas = new ArrayList<>(elegidas);
		ordenadas.sort(Comparator.comparing(CuotaPorPagar::vencimiento).thenComparing(CuotaPorPagar::id));
		BigDecimal debe = Dinero.sumar(ordenadas.stream().map(CuotaPorPagar::saldo).toList());
		if (restante.compareTo(debe) > 0) {
			throw new ReglaNegocioException("El monto (" + Dinero.formatear(restante)
					+ ") supera lo que se debe de las cuotas elegidas (" + Dinero.formatear(debe) + ").");
		}
		List<Imputacion> imputaciones = new ArrayList<>();
		for (CuotaPorPagar cuota : ordenadas) {
			if (restante.signum() == 0) {
				break;
			}
			BigDecimal saldo = Dinero.normalizar(cuota.saldo());
			if (saldo.signum() <= 0) {
				throw new ReglaNegocioException("Una de las cuotas elegidas ya no tiene saldo.");
			}
			BigDecimal parte = restante.min(saldo);
			imputaciones.add(new Imputacion(cuota.id(), parte));
			restante = restante.subtract(parte);
		}
		return List.copyOf(imputaciones);
	}
}
