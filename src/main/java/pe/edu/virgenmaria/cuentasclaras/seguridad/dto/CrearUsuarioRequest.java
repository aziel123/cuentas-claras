package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Set;

/**
 * Formulario de usuario nuevo. La clave no se pide ni se muestra: el sistema envía un enlace de un solo uso al celular
 * (WhatsApp) o al correo del titular (sprint 5). Por eso el celular o el correo son obligatorios (lo valida el servicio).
 */
public record CrearUsuarioRequest(
		@NotBlank(message = "Escribe el nombre completo.")
		@Size(max = 150, message = "El nombre completo puede tener hasta 150 caracteres.") String nombreCompleto,
		@NotBlank(message = "Escribe el nombre de usuario.")
		@Size(max = 60, message = "El nombre de usuario puede tener hasta 60 caracteres.") String nombreUsuario,
		@Email(message = "Revisa el correo: no parece válido.")
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correo,
		@Size(max = 20, message = "Revisa el celular: escribe 9 dígitos que empiecen con 9.") String telefonoWhatsapp,
		@NotEmpty(message = "Elige al menos un rol.") Set<Rol> roles) {

	public static CrearUsuarioRequest vacio() {
		return new CrearUsuarioRequest("", "", "", "", Set.of());
	}
}
