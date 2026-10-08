package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio de una acción sensible sobre un usuario. Queda en la auditoría.
 */
public record MotivoRequest(
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
