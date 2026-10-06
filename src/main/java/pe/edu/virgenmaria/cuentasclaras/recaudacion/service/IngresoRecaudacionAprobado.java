package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

/** Se aprobó aplicar una línea de recaudación por revisar: después del commit, el sistema registra su pago. */
public record IngresoRecaudacionAprobado(Long lineaId, Long solicitudId, Long colegioId) {
}
