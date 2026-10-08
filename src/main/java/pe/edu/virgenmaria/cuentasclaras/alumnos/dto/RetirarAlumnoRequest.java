package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Retiro del alumno: fecha y motivo (queda resaltado en la bitácora). */
public record RetirarAlumnoRequest(
		@NotNull(message = "Elige la fecha de retiro.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
