package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Sprint 9, tanda 2 (sección 3.5): un día del mes de un hijo en el portal. {@code justificable}: una FALTA de los últimos
 * días hábiles (decisión 150) que nunca se revirtió (A3). Solo la familia del alumno lo ve.
 */
public record DiaDelMes(long marcaId, LocalDate fecha, String fechaTexto, String estado, String etiqueta, String variante,
		LocalTime horaLlegada, String origen, boolean justificable, String motivoJustificacion) {
}
