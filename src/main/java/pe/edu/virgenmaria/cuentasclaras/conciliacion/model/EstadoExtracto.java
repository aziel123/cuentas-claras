package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/**
 * Estado de un extracto bancario (trigger {@code trg_extracto_bancario_estado}): CARGADO → CONFIRMADO (otra persona
 * escribió a ciegas el saldo final que ve en su app del banco) | RECHAZADO (saldos a ciegas distintos) | DESCARTADO
 * (quien lo subió, antes de confirmarse y si no tiene uno siguiente).
 */
public enum EstadoExtracto {

	CARGADO("Por confirmar", "alerta"),
	CONFIRMADO("Confirmado", "exito"),
	RECHAZADO("Rechazado", "peligro"),
	DESCARTADO("Descartado", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoExtracto(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String variante() {
		return variante;
	}

	/** Vigente: ocupa su lugar en la cadena de la cuenta. */
	public boolean vigente() {
		return this == CARGADO || this == CONFIRMADO;
	}
}
