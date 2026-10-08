package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

/** Quién está operando (su nombre de usuario, el mismo que la base guarda en {@code creado_por}) y sus roles. */
final class SesionActual {

	private SesionActual() {
	}

	static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}

	static boolean tieneAlgunRol(String... roles) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			return false;
		}
		return autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch(a -> Arrays.stream(roles).anyMatch(r -> a.equals("ROLE_" + r)));
	}
}
