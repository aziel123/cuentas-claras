package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * El archivo trae días ya cargados con movimientos distintos de los guardados: el banco no cambia el pasado, así que el
 * archivo está alterado (o es de otra cuenta). Se audita resaltado ({@code EXTRACTO_DISCONTINUO}) y Promotoría recibe una
 * alerta crítica.
 */
public class ExtractoDiscontinuoException extends ReglaNegocioException {

	public ExtractoDiscontinuoException(String mensaje) {
		super(mensaje);
	}
}
