package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;

/**
 * Correcciones del sprint 8 (QA-S8-6): una comprobación que la bandeja corre ANTES de abrir la transacción de la
 * aprobación, porque necesita ver otros colegios (por ejemplo, las series de las otras sedes con el mismo RUC) y el colegio
 * no se cambia con una transacción abierta. Si lanza {@code ReglaNegocioException}, no se aprueba nada. El
 * {@link ManejadorSolicitud} vuelve a validar lo suyo dentro de la transacción; MySQL, lo que le corresponde.
 */
public interface ValidacionPrevia {

	TipoSolicitud tipo();

	void validar(SolicitudCambio solicitud);
}
