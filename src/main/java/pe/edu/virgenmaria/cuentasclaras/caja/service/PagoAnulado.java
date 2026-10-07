package pe.edu.virgenmaria.cuentasclaras.caja.service;

/**
 * Se anuló un pago. Sprint 5: la mensajería lo escucha en la MISMA transacción y avisa a todos los apoderados de la
 * familia, por WhatsApp y correo, con el motivo y quién lo aprobó.
 */
public record PagoAnulado(Long pagoId, String motivo, String aprobadoPor) {
}
