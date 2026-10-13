package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sprint 8, tanda 1 (H10, sección 3.5, paso 10): el correo externo de control del colegio (el contador o un tercero de
 * confianza del contrato), que recibe el resumen diario y la huella de la bitácora. Correcciones del sprint 8 (S8-A2): en un
 * colegio en producción el operador solo lo propone, con su motivo, y lo aprueba la Promotoría.
 */
public record CorreoControlRequest(
		@NotBlank(message = "Escribe el correo externo de control.")
		@Email(message = "Revisa el correo: no parece válido.")
		@Size(max = 100, message = "El correo puede tener hasta 100 caracteres.") String correo,
		@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.") String motivo) {

	public CorreoControlRequest(String correo) {
		this(correo, null);
	}
}
