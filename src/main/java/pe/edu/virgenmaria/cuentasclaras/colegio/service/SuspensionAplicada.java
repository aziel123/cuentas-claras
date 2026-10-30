package pe.edu.virgenmaria.cuentasclaras.colegio.service;

/**
 * Sprint 11, tanda 2 (sección 3.9): se aplicó la suspensión de un colegio. Al confirmarse, {@code sistema.exportacion}
 * genera la exportación completa de sus datos (el colegio la recibe el mismo día).
 */
public record SuspensionAplicada(long colegioId, long cambioEstadoId) {
}
