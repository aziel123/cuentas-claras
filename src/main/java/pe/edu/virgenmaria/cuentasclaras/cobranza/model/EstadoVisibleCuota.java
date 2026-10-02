package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** Lo que ve el usuario: los cinco estados del glosario. VENCIDA se calcula, nunca se guarda. */
public enum EstadoVisibleCuota {

	PENDIENTE("Pendiente", "info"),
	VENCIDA("Vencida", "peligro"),
	PARCIAL("Parcial", "alerta"),
	PAGADA("Pagada", "exito"),
	ANULADA("Anulada", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoVisibleCuota(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Variante del badge del sistema de diseño (siempre con texto, nunca solo color). */
	public String variante() {
		return variante;
	}
}
