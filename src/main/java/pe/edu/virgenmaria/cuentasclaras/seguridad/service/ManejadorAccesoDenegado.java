package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

import java.io.IOException;

/**
 * Responde 403 (página en español) y audita {@code ACCESO_DENEGADO} con el método y la ruta, sin la
 * query string (puede traer datos). Solo audita a usuarios con sesión: así un visitante anónimo no
 * puede llenar la bitácora con peticiones sin token CSRF.
 */
@Component
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

	private final AuditoriaService auditoria;

	private final AccessDeniedHandler respuesta403 = new AccessDeniedHandlerImpl();

	public ManejadorAccesoDenegado(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException excepcion)
			throws IOException, ServletException {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.isAuthenticated()
				&& !(autenticacion instanceof AnonymousAuthenticationToken)) {
			auditoria.registrar(AccionAuditoria.ACCESO_DENEGADO, null, null, null, null,
					request.getMethod() + " " + request.getRequestURI());
		}
		respuesta403.handle(request, response, excepcion);
	}
}
