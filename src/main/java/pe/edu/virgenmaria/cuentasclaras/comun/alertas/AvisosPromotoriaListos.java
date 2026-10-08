package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 6, tanda 2: las alertas difundibles de una pasada de {@code sistema.panel} (cada 15 minutos), en el orden en que
 * se avisan (las CRÍTICAS primero). La mensajería crea, en la misma transacción, un mensaje ALERTA_PROMOTORIA por
 * destinatario y aviso con la clave {@code ALERTA:tipo:referencia} (se avisa UNA sola vez), con el texto FIJO del tipo,
 * el tope diario por persona y sin los excluidos. Vive en {@code comun} para que {@code comunicacion} no dependa del panel.
 *
 * @param fecha     el día de la pasada (va en el mensaje si la alerta no trae un monto o una hora)
 * @param inmediato S6-M3: el aviso nace de un hecho que acaba de confirmarse (un cierre con diferencia), no de una pasada:
 *                  sale aunque sea domingo, feriado o de noche (la alerta puede desaparecer antes de la próxima pasada si
 *                  el cierre se aprueba)
 */
public record AvisosPromotoriaListos(long colegioId, LocalDate fecha, List<Aviso> avisos, boolean inmediato) {

	public AvisosPromotoriaListos {
		Objects.requireNonNull(fecha, "fecha");
		avisos = List.copyOf(avisos);
	}

	/** Una pasada de {@code sistema.panel} (cada 15 minutos). */
	public AvisosPromotoriaListos(long colegioId, LocalDate fecha, List<Aviso> avisos) {
		this(colegioId, fecha, avisos, false);
	}
}
