package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sprint 9, tanda 1: proponer para este año las asignaciones del año anterior (cada una con su solicitud). */
public record CopiarAsignacionesRequest(
		@NotNull(message = "Elige el año.") Long anioId,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
