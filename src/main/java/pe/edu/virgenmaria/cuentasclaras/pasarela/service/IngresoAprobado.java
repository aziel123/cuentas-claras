package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

/** Se aprobó aplicar un ingreso por revisar: después del commit, el sistema registra el pago ({@code AplicadorIngresos}). */
public record IngresoAprobado(Long ordenId, Long solicitudId, Long colegioId) {
}
