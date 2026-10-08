package pe.edu.virgenmaria.cuentasclaras.caja.service;

/**
 * Se registró un pago (en caja, en línea, por el banco o como reemplazo). Sprint 5: la mensajería lo escucha en la MISMA
 * transacción y crea el aviso al responsable de pago: sin mensaje no hay pago.
 */
public record PagoRegistrado(Long pagoId) {
}
