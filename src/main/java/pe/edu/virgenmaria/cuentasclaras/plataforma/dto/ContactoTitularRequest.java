package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Sprint 8, tanda 1: el contacto nuevo del titular (solo antes de la activación). Correcciones del sprint 8 (S8-A1): un
 * operador lo PROPONE con su motivo y lo aplica OTRO operador; se avisa al contacto anterior, al correo institucional y al
 * correo externo de control.
 */
public record ContactoTitularRequest(
		@Size(max = 20, message = "Revisa el celular: escribe 9 dígitos que empiecen con 9.") String telefonoWhatsapp,
		@Email(message = "Revisa el correo: no parece válido.")
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correo,
		@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.") String motivo) {

	public ContactoTitularRequest(String telefonoWhatsapp, String correo) {
		this(telefonoWhatsapp, correo, null);
	}
}
