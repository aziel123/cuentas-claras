package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sprint 6, tanda 2: el celular y el correo NUEVOS de alguien del personal (vacío = sin ese canal) y el motivo. Lo aprueba
 * otra persona de Promotoría o Dirección.
 */
public record ContactoPersonalRequest(
		@Size(max = 20, message = "Revisa el celular: escribe 9 dígitos que empiecen con 9.") String telefono,
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correo,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {

	public static ContactoPersonalRequest vacio() {
		return new ContactoPersonalRequest("", "", "");
	}
}
