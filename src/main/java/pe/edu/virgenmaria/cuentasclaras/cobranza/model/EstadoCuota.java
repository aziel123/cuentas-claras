package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/**
 * Estados que se guardan. VENCIDA no se guarda (la base la rechaza): se calcula con la fecha de Lima, ver
 * {@link EstadoVisibleCuota}. EXONERADA (sprint 3): un descuento o beca del 100 %, con saldo 0 y sin pagos.
 */
public enum EstadoCuota {
	PENDIENTE, PARCIAL, PAGADA, EXONERADA, ANULADA
}
