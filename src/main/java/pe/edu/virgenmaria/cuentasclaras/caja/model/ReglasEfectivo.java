package pe.edu.virgenmaria.cuentasclaras.caja.model;

import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;

/**
 * Reglas del efectivo en caja:
 * <ul>
 *   <li>Todo monto en efectivo es múltiplo de S/ 0.10: no circulan monedas de 1 ni 5 céntimos (BCRP, 2019) y el
 *       redondeo sería a favor del consumidor (Ley 29571, art. 44). La base lo exige con un CHECK.</li>
 *   <li>Vuelto = recibido − total, con 0 ≤ vuelto &lt; S/ 200.00 (el billete más grande).</li>
 * </ul>
 */
public final class ReglasEfectivo {

	public static final BigDecimal VUELTO_MAXIMO = new BigDecimal("200.00");

	public static final String NO_EXACTO = "Este monto no se puede pagar exacto en efectivo (no hay monedas de 1 ni 5 "
			+ "céntimos). Cóbralo con Yape, Plin, transferencia o tarjeta.";

	private ReglasEfectivo() {
	}

	/** Exige que el total a cobrar en efectivo sea múltiplo de S/ 0.10. */
	public static BigDecimal exigirDecimos(BigDecimal monto, String campo) {
		return Dinero.exigirDecimos(monto, "total".equals(campo) ? NO_EXACTO
				: capitalizar(campo) + " debe ser múltiplo de S/ 0.10 (no hay monedas de 1 ni 5 céntimos).");
	}

	/** Vuelto a entregar. Rechaza lo recibido que no alcanza o que daría un vuelto de S/ 200.00 o más. */
	public static BigDecimal vuelto(BigDecimal total, BigDecimal recibido) {
		BigDecimal aCobrar = exigirDecimos(total, "total");
		if (recibido == null) {
			throw new ReglaNegocioException("Escribe cuánto te entregó el apoderado en efectivo.");
		}
		BigDecimal entregado = exigirDecimos(recibido, "lo recibido");
		BigDecimal vuelto = entregado.subtract(aCobrar);
		if (vuelto.signum() < 0) {
			throw new ReglaNegocioException("Lo recibido (" + Dinero.formatear(entregado) + ") no alcanza para el total ("
					+ Dinero.formatear(aCobrar) + ").");
		}
		if (vuelto.compareTo(VUELTO_MAXIMO) >= 0) {
			throw new ReglaNegocioException("El vuelto sería " + Dinero.formatear(vuelto)
					+ ": no puede ser de S/ 200.00 o más. Revisa lo recibido.");
		}
		return vuelto;
	}

	private static String capitalizar(String texto) {
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}
}
