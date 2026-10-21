package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDateTime;

/**
 * Sprint 9, tanda 2 (E5): un cambio de la asistencia del día para la lista de Dirección: la sección, el alumno, antes y
 * ahora, quién y cuándo, y si fue después del aviso a las familias (resaltado). {@code revertible}: una justificación de
 * la familia que Dirección puede revertir.
 */
public record CambioDelDia(long marcaId, String seccion, String alumno, String antes, String ahora, String origen,
		String quien, LocalDateTime cuando, boolean despuesDelAviso, String motivo, boolean revertible) {
}
