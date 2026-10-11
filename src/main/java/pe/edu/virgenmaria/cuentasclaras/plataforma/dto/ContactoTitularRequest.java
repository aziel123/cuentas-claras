package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Sprint 8, tanda 1: corregir el contacto del titular y reenviar su enlace (solo antes de la activación). */
public record ContactoTitularRequest(
		@Size(max = 20, message = "Revisa el celular: escribe 9 dígitos que empiecen con 9.") String telefonoWhatsapp,
		@Email(message = "Revisa el correo: no parece válido.")
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correo) {
}
