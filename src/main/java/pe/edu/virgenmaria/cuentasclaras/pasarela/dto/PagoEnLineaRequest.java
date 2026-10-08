package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Crear la orden de pago en línea. {@code totalVisto} solo sirve para detectar que el saldo cambió entre la revisión y
 * el pago («El monto cambió; revisa de nuevo»): el servidor nunca cobra ese número, cobra la suma de los saldos.
 * {@code clave}: la misma orden si se envía dos veces (doble toque o recarga).
 */
public record PagoEnLineaRequest(
		@NotNull(message = "Vuelve a revisar el pago.") UUID clave,
		@NotEmpty(message = "Elige al menos una cuota.") @Size(max = 30, message = "Elige hasta 30 cuotas.") List<Long> cuotaIds,
		@NotNull(message = "Vuelve a revisar el pago.") BigDecimal totalVisto,
		Boolean factura) {

	/** Sin marcar la casilla el formulario no envía «factura»: boleta. */
	public boolean quiereFactura() {
		return Boolean.TRUE.equals(factura);
	}
}
