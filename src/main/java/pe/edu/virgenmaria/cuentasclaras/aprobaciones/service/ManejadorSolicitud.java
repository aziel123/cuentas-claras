package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;

import java.util.List;
import java.util.Set;

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

	/**
	 * Otras personas que tampoco pueden resolverla, además de quien la pidió (por ejemplo, la cajera del pago que se
	 * anula aunque la pida Administración). La bandeja les suma quienes prepararon sus cuentas (A5).
	 */
	default Set<String> involucrados(SolicitudCambio solicitud) {
		return Set.of();
	}

	/** Lo que necesita ver quien aprueba, en líneas de texto (la tarjeta de la bandeja). */
	default List<String> detalle(SolicitudCambio solicitud) {
		return List.of();
	}

	/** Un aviso resaltado para quien aprueba (por ejemplo, «el dinero pasa a OTRA familia») o {@code null}. */
	default String advertencia(SolicitudCambio solicitud) {
		return null;
	}
}
