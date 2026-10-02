package pe.edu.virgenmaria.cuentasclaras.comun.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * El recurso pedido no existe o pertenece a otro colegio. En ambos casos se responde
 * 404, para no revelar la existencia de datos de otro colegio.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
