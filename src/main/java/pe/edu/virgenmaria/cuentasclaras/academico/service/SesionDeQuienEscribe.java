package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

/**
 * Sprint 9, tanda 2: la sesión de la base de quien escribe la asistencia o un consentimiento ({@code sesion_id}). Lo que
 * escribe un docente o una familia lleva su sesión: en MySQL, {@code cc_sesion_abierta} la exige abierta y suya, así que
 * con la clave de {@code cc_app} nadie escribe «como» alguien que no está conectado.
 */
@Component
class SesionDeQuienEscribe {

	static final String SIN_SESION = "Tu sesión ya no sirve para guardar (venció o ingresaste en otro equipo). Cierra "
			+ "sesión y vuelve a ingresar: no se guardó nada.";

	private final TokenDeSesion tokens;

	SesionDeQuienEscribe(TokenDeSesion tokens) {
		this.tokens = tokens;
	}

	/** El id de la sesión de la base de la persona en sesión (o un error claro si no tiene). */
	Long de(UsuarioAutenticado persona) {
		return tokens.actual()
				.filter(s -> s.usuarioId().equals(persona.usuarioId()) && s.colegioId().equals(persona.colegioId()))
				.map(SesionAbierta::sesionId).orElseThrow(() -> new ReglaNegocioException(SIN_SESION));
	}
}
