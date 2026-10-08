package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

import java.io.IOException;

/**
 * Después de ingresar: con clave temporal va directo a cambiarla; si no, a la página que pidió
 * antes del login o a {@code /inicio}.
 */
@Component
public class ManejadorIngresoExitoso extends SavedRequestAwareAuthenticationSuccessHandler {

	public ManejadorIngresoExitoso() {
		setDefaultTargetUrl("/inicio");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication autenticacion) throws ServletException, IOException {
		boolean clavePendiente = autenticacion.getAuthorities().stream()
				.anyMatch(a -> UsuarioAutenticado.CLAVE_PENDIENTE.equals(a.getAuthority()));
		if (clavePendiente) {
			clearAuthenticationAttributes(request);
			getRedirectStrategy().sendRedirect(request, response, ModuloApp.RUTA_CAMBIAR_CLAVE);
			return;
		}
		super.onAuthenticationSuccess(request, response, autenticacion);
	}
}
