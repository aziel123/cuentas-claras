package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** BORRADOR → ENVIADO → CONFIRMADO, o devuelto a BORRADOR, o DESCARTADO. */
public enum EstadoLote {

	BORRADOR("En preparación", "neutro"),
	ENVIADO("Enviado, por confirmar", "alerta"),
	CONFIRMADO("Confirmado", "exito"),
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
}
