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
		return para(error, vencida, salio, expirada, claveCambiada, null, intentosMaximos, minutosBloqueo,
				horasClaveTemporal);
	}

	/** {@code cuentaActivada}: el apoderado acaba de elegir su clave con su enlace de activación (S4-M2). */
	static MensajeLogin para(String error, String vencida, String salio, String expirada, String claveCambiada,
			String cuentaActivada, int intentosMaximos, long minutosBloqueo, long horasClaveTemporal) {
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
		if (cuentaActivada != null) {
			return new MensajeLogin("exito", "Listo, tu cuenta está activa. Ingresa con tu número de documento y la "
					+ "clave que acabas de elegir.");
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
