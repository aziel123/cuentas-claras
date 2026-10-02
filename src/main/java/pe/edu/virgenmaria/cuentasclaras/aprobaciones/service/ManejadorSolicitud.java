package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
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

	/** Igual que {@link #aplicar(SolicitudCambio, String)}, con el comentario de quien aprueba (puede ser null). */
	default void aplicar(SolicitudCambio solicitud, String aprobador, String comentario) {
		if (exigeComentario(solicitud) && (comentario == null || comentario.isBlank())) {
			throw new ReglaNegocioException("Para aprobar esto escribe un comentario: qué verificaste o cómo se resolvió.");
		}
		aplicar(solicitud, aprobador);
	}

	/** Orden en la bandeja: 0 primero (un cierre con diferencia), 1 (anulaciones de pago), 2 el resto. */
	default int prioridad(SolicitudCambio solicitud) {
		return 2;
	}

	/** Si aprobarla exige un comentario (por ejemplo, un cierre con faltante: cómo se resolvió). */
	default boolean exigeComentario(SolicitudCambio solicitud) {
		return false;
	}

	/** El texto del botón de rechazo («Observar» en un cierre de caja). */
	default String accionRechazo() {
		return "Rechazar";
	}

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
