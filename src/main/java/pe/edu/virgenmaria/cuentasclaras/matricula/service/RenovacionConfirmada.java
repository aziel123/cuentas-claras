package pe.edu.virgenmaria.cuentasclaras.matricula.service;

/**
 * La familia confirmó que el alumno continúa. Después del commit, {@code sistema.matricula} reserva su matrícula (y el
 * barrido de cada 10 minutos lo retoma si algo falló).
 */
public record RenovacionConfirmada(Long colegioId, Long renovacionId) {
}
