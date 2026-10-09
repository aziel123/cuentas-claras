package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesionHttp;

/**
 * El cierre de la sesión:
 * <ul>
 *   <li>con el botón «Cerrar sesión», cierra la sesión de la base ({@code SALIO}; es un {@link LogoutHandler} que corre
 *       antes de invalidar la sesión HTTP) y audita {@code SESION_CERRADA};</li>
 *   <li>cuando la sesión HTTP expira o se invalida por otra razón, cierra la de la base ({@code VENCIO}) si seguía
 *       abierta (sprint 7, tanda 2): su secreto ya no firma.</li>
 * </ul>
 */
@Service
public class ServicioCierreSesion implements LogoutHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ServicioCierreSesion.class);

	private final AuditoriaService auditoria;

	private final SesionesFirmadas sesiones;

	public ServicioCierreSesion(AuditoriaService auditoria, SesionesFirmadas sesiones) {
		this.auditoria = auditoria;
		this.sesiones = sesiones;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication autenticacion) {
		TokenDeSesionHttp.de(request.getSession(false)).ifPresent(abierta -> sesiones.cerrar(abierta, MotivoCierreSesion.SALIO));
	}

	@EventListener
	public void alCerrarSesion(LogoutSuccessEvent evento) {
		if (evento.getAuthentication() != null
				&& evento.getAuthentication().getPrincipal() instanceof UsuarioAutenticado usuario) {
			auditoria.registrar(auditoria.actorPara(usuario.colegioId(), usuario.usuarioId(), usuario.getUsername(),
					usuario.rolesComoTexto()), AccionAuditoria.SESION_CERRADA, "usuario",
					usuario.usuarioId().toString(), null, null, null);
		}
	}

	@EventListener
	public void alExpirar(HttpSessionDestroyedEvent evento) {
		TokenDeSesionHttp.de(evento.getSession()).ifPresent(abierta -> {
			try {
				sesiones.cerrar(abierta, MotivoCierreSesion.VENCIO);
			}
			catch (RuntimeException e) {
				// Al apagar la aplicación la base puede ya no estar: la cierra el próximo arranque (REINICIO).
				LOG.warn("No se pudo cerrar la sesión {} de la base al expirar: {}", abierta.sesionId(),
						e.getClass().getSimpleName());
			}
		});
	}
}
