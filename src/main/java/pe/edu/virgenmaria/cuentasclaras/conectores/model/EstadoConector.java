package pe.edu.virgenmaria.cuentasclaras.conectores.model;

/**
 * Sprint 8, tanda 2: PROPUESTO → VIGENTE (con su CAMBIO_CONECTOR aprobada y firmada por otra persona) o RECHAZADO; un
 * VIGENTE pasa a REEMPLAZADO cuando otro del mismo tipo entra en su lugar. Nada se borra (trg_conector_colegio_estado).
 */
public enum EstadoConector {

	PROPUESTO("Por aprobar", "alerta"),
	VIGENTE("Vigente", "exito"),
	RECHAZADO("Rechazado", "peligro"),
	REEMPLAZADO("Reemplazado", "neutro"),
	/**
	 * Sprint 11, tanda 1c (decisión 218): la cuenta bancaria, la pasarela o la recaudación que aprobó directo una
	 * Promotoría esperan 24 horas antes de entrar en vigencia (las frena otra Promotoría o un operador; el socio las
	 * aprueba al instante).
	 */
	EN_ESPERA("En espera (24 horas)", "alerta"),
	/** Sprint 11, tanda 1c: frenado durante la espera (no entra en vigencia; se vuelve a proponer). */
	FRENADO("Frenado", "peligro");

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
