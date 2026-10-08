package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** Nace SOLICITADO; lo resuelve otra persona de Promotoría o Dirección y ya no cambia. */
public enum EstadoDescuento {

	SOLICITADO("Por aprobar", "alerta"),
	APROBADO("Aprobado", "exito"),
	RECHAZADO("Rechazado", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoDescuento(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String variante() {
		return variante;
	}
}
