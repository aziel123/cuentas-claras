package pe.edu.virgenmaria.cuentasclaras.caja.model;

/** Un cierre nace POR_REVISAR; otra persona de Promotoría o Dirección lo aprueba u observa, y ya no cambia. */
public enum EstadoCierre {

	POR_REVISAR("Por revisar", "alerta"),
	APROBADO("Aprobado", "exito"),
	OBSERVADO("Observado", "peligro");

	private final String etiqueta;

	private final String variante;

	EstadoCierre(String etiqueta, String variante) {
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
