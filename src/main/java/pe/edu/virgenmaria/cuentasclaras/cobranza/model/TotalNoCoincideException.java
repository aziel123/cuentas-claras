package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** Quien confirma un lote escribió un total del informe del contador distinto del declarado: se audita y se rechaza. */
public class TotalNoCoincideException extends ReglaNegocioException {

	public TotalNoCoincideException(String mensaje) {
		super(mensaje);
	}
}
