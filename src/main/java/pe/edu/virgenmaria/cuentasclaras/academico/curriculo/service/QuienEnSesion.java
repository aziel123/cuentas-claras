package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/** La persona en sesión (la evaluación y los periodos los cambia siempre una persona de Dirección, nunca el sistema). */
final class QuienEnSesion {

	private QuienEnSesion() {
	}

	static UsuarioAutenticado actual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
