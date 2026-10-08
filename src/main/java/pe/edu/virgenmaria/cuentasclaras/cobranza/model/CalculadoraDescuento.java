package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cuánto se descuenta de una cuota (diseño, sección 10.5). Siempre sobre el MONTO original de la cuota, sin componer.
 * <ul>
 *   <li>Porcentaje: lo que se paga queda en múltiplos de S/ 0.10 redondeando A FAVOR del apoderado (no hay monedas de
 *       1 ni 5 céntimos): {@code décimos = FLOOR(monto × (100 − pct) / 10)}, una sola división, nunca
 *       {@code UNNECESSARY}. 10 % de 437.50 → 43.80; 100 % → toda la cuota (EXONERADA).</li>
 *   <li>Monto fijo: múltiplo de S/ 0.10 y no mayor que la cuota.</li>
 * </ul>
 */
public final class CalculadoraDescuento {

	private static final BigDecimal CIEN = new BigDecimal("100");

	private CalculadoraDescuento() {
	}

	public static BigDecimal ajuste(BigDecimal montoCuota, ModalidadDescuento modalidad, BigDecimal valor) {
		BigDecimal monto = Dinero.positivo(montoCuota, "el monto de la cuota");
		Objects.requireNonNull(modalidad, "modalidad");
		BigDecimal v = Dinero.normalizar(valor);
		return switch (modalidad) {
			case PORCENTAJE -> {
				if (v.signum() <= 0 || v.compareTo(CIEN) > 0) {
					throw new ReglaNegocioException("El porcentaje debe ser mayor que 0 y como máximo 100.");
				}
				BigDecimal decimos = monto.multiply(CIEN.subtract(v)).divide(BigDecimal.TEN, 0, RoundingMode.FLOOR);
				BigDecimal aPagar = decimos.multiply(Dinero.DECIMO).setScale(Dinero.ESCALA, RoundingMode.UNNECESSARY);
				yield monto.subtract(aPagar);
			}
			case MONTO -> {
				if (v.signum() <= 0 || !Dinero.enDecimos(v)) {
					throw new ReglaNegocioException("El monto fijo debe ser mayor que 0 y múltiplo de S/ 0.10.");
				}
				if (v.compareTo(monto) > 0) {
					throw new ReglaNegocioException("El descuento (" + Dinero.formatear(v) + ") es mayor que la cuota ("
							+ Dinero.formatear(monto) + ").");
				}
				yield v;
			}
		};
	}
}
