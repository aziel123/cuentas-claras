package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** El archivo subido no es un .xlsx aceptable (formato, tamaño, contenido peligroso). Mensaje claro, en español. */
public class ArchivoNoValidoException extends ReglaNegocioException {

	public ArchivoNoValidoException(String mensaje) {
		super(mensaje);
	}
}
