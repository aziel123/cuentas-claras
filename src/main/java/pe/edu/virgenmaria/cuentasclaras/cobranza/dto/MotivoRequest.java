package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de un cambio sensible: de 10 a 500 caracteres; queda en la bitácora. */
public record MotivoRequest(
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.")
		String motivo) {
}
