package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sprint 8, tanda 1 (sección 3.7): la ficha que Promotoría corrige desde la pantalla de marca. El RUC no está aquí a
 * propósito: no se cambia por esta pantalla (en producción lo exige la base). El logo llega aparte, como archivo.
 */
public record MarcaColegioRequest(
		@NotBlank(message = "Escribe el nombre del colegio.")
		@Size(max = 150, message = "El nombre del colegio tiene hasta 150 caracteres.")
		String nombre,

		@NotBlank(message = "Escribe la razón social (la que figura en SUNAT).")
		@Size(max = 150, message = "La razón social tiene hasta 150 caracteres.")
		String razonSocial,

		@NotBlank(message = "Escribe la dirección del colegio.")
		@Size(max = 200, message = "La dirección tiene hasta 200 caracteres.")
		String direccion,

		@NotBlank(message = "Escribe el correo institucional.")
		@Email(message = "Revisa el correo institucional: no tiene el formato de un correo.")
		@Size(max = 150, message = "El correo institucional tiene hasta 150 caracteres.")
		String correoInstitucional) {
}
