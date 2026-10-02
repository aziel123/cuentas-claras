package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Quién está operando: su nombre de usuario, el mismo que la base guarda en {@code creado_por} y en {@code cajero}. */
final class SesionCaja {

	private SesionCaja() {
	}

	static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()) {
			throw new IllegalStateException("Se necesita un usuario en sesión para operar la caja");
		}
		return autenticacion.getName();
	}
}
