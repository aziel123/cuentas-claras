package pe.edu.virgenmaria.cuentasclaras.conectores.model;

/**
 * Sprint 8, tanda 2: PROPUESTO → VIGENTE (con su CAMBIO_CONECTOR aprobada y firmada por otra persona) o RECHAZADO; un
 * VIGENTE pasa a REEMPLAZADO cuando otro del mismo tipo entra en su lugar. Nada se borra (trg_conector_colegio_estado).
 */
public enum EstadoConector {

	PROPUESTO("Por aprobar", "alerta"),
	VIGENTE("Vigente", "exito"),
	RECHAZADO("Rechazado", "peligro"),
	REEMPLAZADO("Reemplazado", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoConector(String etiqueta, String variante) {
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
