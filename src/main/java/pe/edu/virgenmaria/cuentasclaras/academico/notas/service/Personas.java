package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/** La persona en sesión (las notas las escribe siempre una persona, nunca el sistema). */
final class Personas {

	private Personas() {
	}

	static UsuarioAutenticado actual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
