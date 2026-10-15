package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 1 (sección 5): PROPUESTA → VIGENTE (con su ASIGNACION_DOCENTE aprobada y firmada por otra persona de
 * Dirección o Promotoría que no es el docente) o RECHAZADA; VIGENTE → TERMINADA (Dirección o Promotoría con su firma, o
 * {@code sistema.academico}). Nada se borra: lo exige trg_asignacion_docente_estado.
 */
public enum EstadoAsignacion {

	PROPUESTA("Por aprobar", "alerta"),
	VIGENTE("Vigente", "exito"),
	RECHAZADA("Rechazada", "peligro"),
	TERMINADA("Terminada", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoAsignacion(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Variante del badge (exito, alerta, peligro, neutro): siempre con su texto. */
	public String variante() {
		return variante;
	}
}
