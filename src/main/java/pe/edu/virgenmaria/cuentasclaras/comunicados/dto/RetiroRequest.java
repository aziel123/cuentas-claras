package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sprint 9, tanda 3 (E28): el motivo del retiro, que verán las familias. */
public record RetiroRequest(
		@NotBlank(message = "Escribe el motivo del retiro.") @Size(min = 10, max = 300, message = "El motivo va de 10 a 300 caracteres.") String motivo) {
}
