package pe.edu.virgenmaria.cuentasclaras.caja.service;

import java.math.BigDecimal;

/**
 * Puerto de caja hacia la pasarela (correcciones del sprint 4, S4-A3): la devolución de un pago en línea solo sale por
 * la API de la pasarela ({@code PasarelaPagos.reembolsar}), que la devuelve al mismo medio de origen. Lo implementa el
 * módulo {@code pasarela}; caja no lo conoce.
 */
public interface ReembolsosEnLinea {

	/** Lo que devolvió la pasarela: el cargo reembolsado, el id del reembolso y el monto. */
	record Reembolsado(String cargoId, String reembolsoId, BigDecimal monto) {
	}

	/**
	 * Pide a la pasarela que reembolse el cargo del pago en línea (al mismo medio de origen).
	 *
	 * @throws pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException si la orden tuvo un contracargo o la
	 *         pasarela no está disponible
	 */
	Reembolsado reembolsar(Long ordenPagoId, BigDecimal monto, String motivo);
}
