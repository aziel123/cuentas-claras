package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Un dato personal no cumple las reglas. {@code campo} es el nombre del campo del formulario (o de la columna del
 * Excel) para mostrar el error junto a él. El mensaje está en español y puede repetir lo que escribió el usuario:
 * se muestra solo a él, nunca va a los logs ni a la bitácora.
 */
public class DatoInvalidoException extends ReglaNegocioException {

	private final String campo;

	public DatoInvalidoException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String campo() {
		return campo;
	}
}
