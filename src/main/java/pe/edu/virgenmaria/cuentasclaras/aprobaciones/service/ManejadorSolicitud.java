package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;

/**
 * Aplica un tipo de solicitud cuando otra persona la aprueba. Lo implementa el módulo dueño del dato (alumnos,
 * cobranza; en el sprint 3, caja), así {@code aprobaciones} no depende de ellos.
 * <ul>
 *   <li>{@link #aplicar} corre en la transacción de la aprobación, después de comprobar quién aprueba. Debe volver a
 *       validar (el dato pudo cambiar desde la solicitud) y lanzar {@code ReglaNegocioException} si ya no corresponde;
 *       debe auditar el cambio y, si bloquea el año escolar, hacerlo antes de auditar.</li>
 *   <li>{@link #alRechazar} deshace lo que la solicitud dejó marcado (por ejemplo, «anulación por aprobar»).</li>
 * </ul>
 */
public interface ManejadorSolicitud {

	TipoSolicitud tipo();

	void aplicar(SolicitudCambio solicitud, String aprobador);

	default void alRechazar(SolicitudCambio solicitud) {
		// nada que deshacer
	}
}
