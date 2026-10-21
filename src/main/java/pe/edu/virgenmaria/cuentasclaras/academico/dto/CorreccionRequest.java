package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;

import java.time.LocalTime;

/** Sprint 9, tanda 2: corregir una marca (el mismo día) o pedir su corrección (otro día). */
public record CorreccionRequest(
		@NotNull(message = "Elige el estado correcto.") EstadoAsistencia estado,
		@DateTimeFormat(pattern = "HH:mm") LocalTime horaLlegada,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 300, message = "El motivo debe tener entre 10 y 300 caracteres.") String motivo) {
}
