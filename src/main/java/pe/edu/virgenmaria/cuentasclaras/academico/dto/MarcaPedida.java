package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;

import java.time.LocalTime;

/**
 * Sprint 9, tanda 2 (sección 3.4): lo que el docente marcó para un alumno en la hoja del día. NO tiene {@code fuente}
 * (A15: la pone el servicio, siempre DOCENTE; lo comprueba {@code MarcaPedidaSinFuenteTest}) ni datos del alumno más
 * allá de su id.
 */
public record MarcaPedida(
		@NotNull(message = "Falta el alumno.") Long alumnoId,
		@NotNull(message = "Elige el estado.") EstadoAsistencia estado,
		@DateTimeFormat(pattern = "HH:mm") LocalTime horaLlegada) {
}
