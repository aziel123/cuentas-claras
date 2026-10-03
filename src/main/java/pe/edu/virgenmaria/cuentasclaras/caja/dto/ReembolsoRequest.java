package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.Size;

/**
 * El reembolso de una devolución: en un pago digital, el número de operación de la transferencia a la cuenta de origen
 * (y la confirmación de que va a esa cuenta); en efectivo, el nombre y el documento de quien lo recibe y firma.
 */
public record ReembolsoRequest(
		@Size(max = 40, message = "El número de operación tiene hasta 30 caracteres.") String numeroOperacion,
		boolean cuentaDeOrigen,
		@Size(max = 150, message = "El nombre tiene hasta 150 caracteres.") String recibidoPorNombre,
		@Size(max = 20, message = "El documento tiene hasta 20 caracteres.") String recibidoPorDocumento) {
}
