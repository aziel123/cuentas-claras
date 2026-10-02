package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * La clave actual escrita al cambiar la clave no es correcta. Cuenta como intento fallido para el
 * bloqueo de la cuenta; si la bloqueó, quien llama debe cerrar la sesión.
 */
public class ClaveActualIncorrectaException extends ReglaNegocioException {

	private final boolean cuentaBloqueada;

	public ClaveActualIncorrectaException(boolean cuentaBloqueada) {
		super(cuentaBloqueada
				? "Tu cuenta se bloqueó por varios intentos fallidos. Espera 15 minutos o pide a Dirección que la desbloquee."
				: "Tu clave actual no es correcta.");
		this.cuentaBloqueada = cuentaBloqueada;
	}

	public boolean cuentaBloqueada() {
		return cuentaBloqueada;
	}
}
