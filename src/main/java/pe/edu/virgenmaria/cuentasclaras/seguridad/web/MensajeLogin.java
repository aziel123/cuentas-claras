package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

/**
 * Aviso de la página de ingreso según cómo se llegó a ella.
 *
 * @param tipo  {@code error}, {@code advertencia}, {@code exito} o {@code info} (variante de la alerta)
 * @param texto mensaje para el usuario
 */
public record MensajeLogin(String tipo, String texto) {

	/**
	 * Clave incorrecta, usuario inexistente o desactivado muestran el MISMO mensaje: así no se revela
	 * qué usuarios existen.
	 */
	static MensajeLogin para(String error, String bloqueada, String salio, String expirada, String claveCambiada) {
		if (bloqueada != null) {
			return new MensajeLogin("advertencia", "Tu cuenta está bloqueada por varios intentos fallidos. "
					+ "Espera 15 minutos o pide a Dirección que la desbloquee.");
		}
		if (error != null) {
			return new MensajeLogin("error", "Usuario o clave incorrectos. Revisa e intenta de nuevo.");
		}
		if (expirada != null) {
			return new MensajeLogin("info", "Tu sesión se cerró porque ingresaste desde otro equipo "
					+ "o pasó mucho tiempo sin actividad. Vuelve a ingresar.");
		}
		if (claveCambiada != null) {
			return new MensajeLogin("exito", "Listo, tu clave se cambió. Ingresa con tu nueva clave.");
		}
		if (salio != null) {
			return new MensajeLogin("exito", "Cerraste sesión. ¡Hasta pronto!");
		}
		return null;
	}
}
