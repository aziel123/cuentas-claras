package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesionHttp;

import java.io.IOException;
import java.time.Clock;

/**
 * Después de ingresar: abre la sesión de la base (sprint 7, tanda 2: su secreto queda en la sesión HTTP y firma cada
 * aprobación; un ingreso anterior en otro equipo queda cerrado) y, con clave temporal, va directo a cambiarla; si no, a la
 * página que pidió antes del login o a {@code /inicio}. Corre después del cambio de id de la sesión (fijación).
 */
@Component
public class ManejadorIngresoExitoso extends SavedRequestAwareAuthenticationSuccessHandler {

	/**
	 * Atributo de la sesión HTTP con el instante del ingreso (sprint 7, tanda 3): desde ahí cuenta el tiempo máximo de la
	 * sesión ({@code FiltroSesionMaxima}, decisión 83).
	 */
	public static final String ATRIBUTO_INGRESO = ManejadorIngresoExitoso.class.getName() + ".INGRESO";

	private final SesionesFirmadas sesiones;

	private final Clock reloj;

	public ManejadorIngresoExitoso(SesionesFirmadas sesiones, Clock reloj) {
		this.sesiones = sesiones;
		this.reloj = reloj;
		setDefaultTargetUrl("/inicio");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication autenticacion) throws ServletException, IOException {
		if (autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			SesionAbierta abierta = sesiones.abrir(usuario.colegioId(), usuario.usuarioId(),
					Actor.normalizarIp(request.getRemoteAddr()));
			TokenDeSesionHttp.guardar(request.getSession(), abierta);
		}
		request.getSession().setAttribute(ATRIBUTO_INGRESO, reloj.instant());
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
