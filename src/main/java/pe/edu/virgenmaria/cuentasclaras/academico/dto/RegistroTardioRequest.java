package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Sprint 9, tanda 2 (decisión 146): pedir registrar un día pasado (no hubo internet: se anotó en papel). */
public record RegistroTardioRequest(
		@NotNull(message = "Elige el día.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
