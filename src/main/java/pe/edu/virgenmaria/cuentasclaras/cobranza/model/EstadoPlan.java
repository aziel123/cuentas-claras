package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/**
 * BORRADOR (Administración lo edita) → ENVIADO (bloqueado) → APROBADO (otra persona) → REEMPLAZADO cuando se aprueba
 * otra versión. Un ENVIADO puede volver a BORRADOR (devuelto con motivo).
 */
public enum EstadoPlan {

	BORRADOR("En preparación", "neutro"),
	/** Bloqueado: nadie lo edita mientras otra persona lo revisa. */
	ENVIADO("Enviado, por aprobar", "alerta"),
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
