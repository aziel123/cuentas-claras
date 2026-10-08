package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Una exportación fuera de los límites (rango, tope diario o tamaño). La transacción NO se revierte por ella: el evento
 * {@code EXPORTACION_RECHAZADA} queda en la bitácora.
 */
public class ExportacionRechazadaException extends ReglaNegocioException {

	public ExportacionRechazadaException(String mensaje) {
		super(mensaje);
	}
}
