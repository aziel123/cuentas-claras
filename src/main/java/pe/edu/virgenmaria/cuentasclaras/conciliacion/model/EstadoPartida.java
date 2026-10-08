package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/**
 * Estado de una partida (trigger {@code trg_partida_conciliacion_estado}): PROPUESTA → CONFIRMADA | DESCARTADA, una sola
 * vez. Una DESCARTADA libera el movimiento y el objeto.
 */
public enum EstadoPartida {
	PROPUESTA, CONFIRMADA, DESCARTADA
}
