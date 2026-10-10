package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;

/**
 * Sprint 7 (logs): agrega al MDC de cada petición el colegio, el id del usuario (nunca su nombre) y la ruta como
 * PATRÓN ({@code /familias/{id}}), nunca la URL real: los enlaces {@code /activar/{colegio}/{token}} llevan secretos.
 */
public class ContextoPeticionLog implements HandlerInterceptor {

	static final String MDC_COLEGIO = "colegio";

	static final String MDC_USUARIO = "usuario_id";

	static final String MDC_RUTA = "ruta";

	@Override
	public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
		Object patron = peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (patron != null) {
			MDC.put(MDC_RUTA, patron.toString());
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof PrincipalConColegio principal) {
			if (principal.colegioId() != null) {
				MDC.put(MDC_COLEGIO, principal.colegioId().toString());
			}
			if (principal.usuarioId() != null) {
				MDC.put(MDC_USUARIO, principal.usuarioId().toString());
			}
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador,
			Exception error) {
		MDC.remove(MDC_RUTA);
		MDC.remove(MDC_COLEGIO);
		MDC.remove(MDC_USUARIO);
	}
}
