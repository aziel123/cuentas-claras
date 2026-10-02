package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Confirmación de un cobro. NO lleva el monto a cobrar: el sistema lo recalcula con los saldos de las cuotas y lo
 * compara con {@code totalVisto} (lo que la cajera vio en la revisión). La cajera solo escribe lo {@code recibido} en
 * efectivo (para el vuelto) y, si está habilitado, un {@code montoACuenta}.
 *
 * @param clave clave de idempotencia de la pantalla de revisión: el doble clic no duplica el pago
 */
public record CobroRequest(
		@NotNull(message = "Vuelve a revisar el cobro.") UUID clave,
		@NotNull(message = "Elige la familia.") Long familiaId,
		@NotEmpty(message = "Elige al menos una cuota.")
		@Size(max = 24, message = "Elige como máximo 24 cuotas por cobro.") List<@NotNull Long> cuotaIds,
		@NotNull(message = "Elige el medio de pago.") MedioPago medio,
		@Size(max = 30, message = "El número de operación tiene hasta 30 caracteres.") String numeroOperacion,
		@Digits(integer = 5, fraction = 2, message = "Lo recibido debe tener hasta 2 decimales.") BigDecimal recibido,
		@Digits(integer = 5, fraction = 2, message = "El monto a cuenta debe tener hasta 2 decimales.") BigDecimal montoACuenta,
		@NotNull(message = "Vuelve a revisar el cobro.") BigDecimal totalVisto,
		@NotNull(message = "Elige boleta o factura.") TipoComprobante comprobante,
		Long receptorApoderadoId,
		@Pattern(regexp = "(\\d{11})?", message = "El RUC tiene 11 dígitos.") String ruc,
		@Size(max = 150, message = "La razón social tiene hasta 150 caracteres.") String razonSocial) {
}
