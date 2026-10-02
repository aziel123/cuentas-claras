package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

/**
 * Quién hizo la acción y desde dónde.
 *
 * @param colegioId     colegio del actor; {@code null} si no se pudo identificar (p. ej. login de un usuario inexistente)
 * @param usuarioId     id del usuario; {@code null} para procesos del sistema o visitantes
 * @param nombreUsuario nombre de usuario, {@code sistema} o {@code anonimo}
 * @param roles         roles separados por coma, sin el prefijo {@code ROLE_}
 * @param ip            IP de la petición, si la hay
 */
public record Actor(Long colegioId, Long usuarioId, String nombreUsuario, String roles, String ip) {

	public static final String SISTEMA = "sistema";

	public static final String ANONIMO = "anonimo";

	public Actor {
		if (nombreUsuario == null || nombreUsuario.isBlank()) {
			throw new IllegalArgumentException("El actor de un evento de auditoría debe tener nombre");
		}
	}

	/** Proceso sin usuario (arranque, tareas programadas) que actúa sobre un colegio. */
	public static Actor sistema(Long colegioId) {
		return new Actor(colegioId, null, SISTEMA, null, null);
	}
}
