package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

/**
 * Aviso de la página de ingreso según cómo se llegó a ella.
 *
 * @param tipo  {@code error}, {@code advertencia}, {@code exito} o {@code info} (variante de la alerta)
 * @param texto mensaje para el usuario
 */
public record MensajeLogin(String tipo, String texto) {

	/**
	 * Clave incorrecta, usuario inexistente, cuenta desactivada o bloqueada muestran el MISMO mensaje: así no
	 * se revela qué usuarios existen ni cuáles están bloqueados.
	 */
	static MensajeLogin para(String error, String vencida, String salio, String expirada, String claveCambiada,
			int intentosMaximos, long minutosBloqueo, long horasClaveTemporal) {
		if (error != null) {
			return new MensajeLogin("error", "Usuario o clave incorrectos. Después de " + intentosMaximos
					+ " intentos fallidos la cuenta se bloquea " + minutosBloqueo + " minutos.");
		}
		if (vencida != null) {
			return new MensajeLogin("advertencia", "Tu clave temporal venció: dura " + horasClaveTemporal + " horas. "
					+ "Pide a Promotoría o Dirección que te den una nueva.");
		}
		if (expirada != null) {
			return new MensajeLogin("info", "Tu sesión se cerró porque ingresaste desde otro equipo, cambiaron tus "
					+ "permisos o pasó mucho tiempo sin actividad. Vuelve a ingresar.");
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
