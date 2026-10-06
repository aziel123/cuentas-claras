package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

/**
 * Estado de una orden de pago en línea (sección 5 del diseño del sprint 4). Las transiciones las exige además
 * {@code trg_orden_pago_estado} en MySQL.
 * <pre>
 * CREADA ──▶ PAGADA | POR_REVISAR | RECHAZADA | VENCIDA
 * VENCIDA ──▶ PAGADA (tardía) | POR_REVISAR
 * POR_REVISAR ──▶ APLICADA | DEVUELTA
 * </pre>
 */
public enum EstadoOrden {

	CREADA("En curso", "info"),
	PAGADA("Pagada", "exito"),
	POR_REVISAR("Por revisar", "peligro"),
	VENCIDA("Vencida sin pagar", "neutro"),
	RECHAZADA("No se completó", "neutro"),
	APLICADA("Aplicada tras revisión", "exito"),
	DEVUELTA("Devuelta", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoOrden(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String variante() {
		return variante;
	}

	/** Ya no cambia (PAGADA, RECHAZADA, APLICADA y DEVUELTA). */
	public boolean finalizada() {
		return this == PAGADA || this == RECHAZADA || this == APLICADA || this == DEVUELTA;
	}

	/** Puede recibir todavía la confirmación de la pasarela (CREADA, o VENCIDA con un pago tardío). */
	public boolean admiteConfirmacion() {
		return this == CREADA || this == VENCIDA;
	}
}
