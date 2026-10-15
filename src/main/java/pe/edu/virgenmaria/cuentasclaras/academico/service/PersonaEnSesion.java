package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/** La persona en sesión (los servicios académicos nunca corren sin una, salvo el proceso de sistema.academico). */
final class PersonaEnSesion {

	private PersonaEnSesion() {
	}

	static UsuarioAutenticado actual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
