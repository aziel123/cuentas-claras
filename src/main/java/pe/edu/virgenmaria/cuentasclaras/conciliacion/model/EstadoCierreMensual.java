package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/** Estado del cierre bancario mensual (sprint 5, tanda 3). */
public enum EstadoCierreMensual {

	ABIERTO("Por cerrar", "alerta"),
	CUADRADO("Cuadrado", "exito"),
	DISCREPANCIA("Discrepancia", "peligro");

	private final String etiqueta;

	private final String variante;

	EstadoCierreMensual(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** La variante del badge. */
	public String variante() {
		return variante;
	}
}
