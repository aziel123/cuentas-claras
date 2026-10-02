package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** BORRADOR (Administración) → APROBADO (otra persona) → REEMPLAZADO cuando se aprueba otra versión. */
public enum EstadoPlan {

	BORRADOR("Borrador, por aprobar", "alerta"),
	APROBADO("Aprobado y vigente", "exito"),
	REEMPLAZADO("Reemplazado", "neutro"),
	DESCARTADO("Descartado", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoPlan(String etiqueta, String variante) {
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
