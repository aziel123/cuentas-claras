package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

/**
 * CARGADO → CONFIRMADO (otra persona escribió a ciegas el total que ve en el banco) → APLICADO (el sistema registró los
 * pagos); CARGADO → RECHAZADO (dos totales a ciegas distintos) | DESCARTADO (quien lo subió, antes de confirmarse).
 * Las transiciones las exige además {@code trg_lote_recaudacion_estado} en MySQL.
 */
public enum EstadoLote {

	CARGADO("Por confirmar", "alerta"),
	CONFIRMADO("Confirmado, aplicando", "info"),
	APLICADO("Aplicado", "exito"),
	RECHAZADO("Rechazado", "peligro"),
	DESCARTADO("Descartado", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoLote(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String variante() {
		return variante;
	}

	/** Mientras el archivo sigue vigente, no se puede volver a cargar el mismo (UNIQUE sobre su SHA-256). */
	public boolean vigente() {
		return this == CARGADO || this == CONFIRMADO || this == APLICADO;
	}
}
