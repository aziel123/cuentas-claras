package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/**
 * Estados que se guardan. VENCIDA no se guarda (la base la rechaza): se calcula con la fecha de Lima, ver
 * {@link EstadoVisibleCuota}.
 */
public enum EstadoCuota {
	PENDIENTE, PARCIAL, PAGADA, ANULADA
}
