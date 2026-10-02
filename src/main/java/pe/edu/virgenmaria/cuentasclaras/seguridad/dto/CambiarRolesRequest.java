package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Set;

/**
 * Nuevos roles de un usuario, con motivo obligatorio.
 */
public record CambiarRolesRequest(
		@NotEmpty(message = "Elige al menos un rol.") Set<Rol> roles,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
