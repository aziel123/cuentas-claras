package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sprint 11, tanda 1 (AP-10): la objeción del titular o la respuesta del operador. */
public record TextoRequest(
		@NotBlank(message = "Escribe el texto.")
		@Size(min = 20, max = 500, message = "El texto va de 20 a 500 caracteres.") String texto) {
}
