package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.OptionalLong;

/** Formatos y datos de sesión que comparten los servicios del panel. */
final class Formato {

	private Formato() {
	}

	/** «72 %»; sin pagos, «—» (no es lo mismo que 0 %). */
	static String porcentaje(OptionalLong valor) {
		return valor.isPresent() ? valor.getAsLong() + " %" : "—";
	}

	/** Si la persona en sesión tiene alguno de esos roles (sin el prefijo ROLE_). */
	static boolean tieneAlgunRol(String... roles) {
		Authentication sesion = SecurityContextHolder.getContext().getAuthentication();
		return sesion != null && sesion.getAuthorities().stream().anyMatch(a -> Arrays.stream(roles)
				.anyMatch(r -> ("ROLE_" + r).equals(a.getAuthority())));
	}

	static String usuarioActual() {
		Authentication sesion = SecurityContextHolder.getContext().getAuthentication();
		return sesion == null ? "desconocido" : sesion.getName();
	}
}
