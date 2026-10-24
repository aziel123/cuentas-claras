package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/** La persona en sesión de los servicios de la libreta (la familia, Dirección o el tutor; nunca un proceso). */
final class QuienEnSesionLibreta {

	private QuienEnSesionLibreta() {
	}

	static UsuarioAutenticado actual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
