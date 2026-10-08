package pe.edu.virgenmaria.cuentasclaras.panel.model;

/** Qué respondió la familia en la llamada de control (sprint 6, tanda 3; decisión 77). «No confirma» es CRÍTICA. */
public enum ResultadoLlamada {

	CONFIRMA("Confirma lo registrado", "exito"),
	NO_CONFIRMA("No confirma lo registrado", "peligro"),
	NO_CONTESTA("No contesta", "neutro");

	private final String etiqueta;

	private final String variante;

	ResultadoLlamada(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Variante del badge del sistema de diseño (siempre con texto). */
	public String variante() {
		return variante;
	}
}
