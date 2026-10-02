package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

/**
 * Audita {@code SESION_CERRADA} cuando el usuario cierra sesión con el botón "Cerrar sesión".
 */
@Service
public class ServicioCierreSesion {

	private final AuditoriaService auditoria;

	public ServicioCierreSesion(AuditoriaService auditoria) {
		this.auditoria = auditoria;
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
}
