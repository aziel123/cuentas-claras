package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Quién está operando: su nombre de usuario, el mismo que la base guarda en {@code respondido_por}. */
final class SesionActual {

	private SesionActual() {
	}

	static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || autenticacion.getName() == null) {
			throw new IllegalStateException("No hay una persona en sesión");
		}
		return autenticacion.getName();
	}
}
