package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** Quien pidió una solicitud (o preparó esa cuenta) intentó resolverla: se audita y se rechaza. */
public class AutoaprobacionSolicitudException extends ReglaNegocioException {

	public AutoaprobacionSolicitudException(String mensaje) {
		super(mensaje);
	}
}
