package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * Cierra al instante las sesiones abiertas de un usuario (desactivado, con roles cambiados o con la
 * clave restablecida). En su siguiente petición vuelve al login.
 * <p>
 * El registro de sesiones está en memoria: vale para una sola instancia de la aplicación.
 */
@Component
public class SesionesUsuario {

	private final SessionRegistry registro;

	public SesionesUsuario(SessionRegistry registro) {
		this.registro = registro;
	}

	/** @return cuántas sesiones se cerraron */
	public int expirar(Long usuarioId) {
		int cerradas = 0;
		for (Object principal : registro.getAllPrincipals()) {
			if (principal instanceof UsuarioAutenticado usuario && usuario.usuarioId().equals(usuarioId)) {
				for (SessionInformation sesion : registro.getAllSessions(principal, false)) {
					sesion.expireNow();
					cerradas++;
				}
			}
		}
		return cerradas;
	}
}
