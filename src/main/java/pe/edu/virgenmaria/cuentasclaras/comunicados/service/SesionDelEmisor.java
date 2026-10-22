package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

/**
 * Sprint 9, tanda 3: la persona en sesión y su sesión de la base ({@code sesion_id}). Lo que escribe una persona en los
 * comunicados (enviarlos, confirmarlos) lleva su sesión: en MySQL, {@code cc_sesion_abierta} la exige abierta y suya.
 */
@Component
public class SesionDelEmisor {

	static final String SIN_SESION = "Tu sesión ya no sirve para enviar (venció o ingresaste en otro equipo). Cierra "
			+ "sesión y vuelve a ingresar: no se guardó nada.";

	private final TokenDeSesion tokens;

	public SesionDelEmisor(TokenDeSesion tokens) {
		this.tokens = tokens;
	}

	/** La persona en sesión (los servicios de comunicados nunca corren sin una, salvo el proceso de avisos). */
	public static UsuarioAutenticado persona() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}

	/** El id de la sesión de la base de la persona (o un error claro si no tiene). */
	public Long de(UsuarioAutenticado persona) {
		return tokens.actual()
				.filter(s -> s.usuarioId().equals(persona.usuarioId()) && s.colegioId().equals(persona.colegioId()))
				.map(SesionAbierta::sesionId).orElseThrow(() -> new ReglaNegocioException(SIN_SESION));
	}
}
