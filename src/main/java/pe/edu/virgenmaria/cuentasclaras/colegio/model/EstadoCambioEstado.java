package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/** Sprint 11, tanda 1 (sección 3.3): PROPUESTO por un operador, APROBADO por OTRO, APLICADO en su fecha; o resuelto. */
public enum EstadoCambioEstado {

	PROPUESTO("Propuesto"),
	APROBADO("Aprobado"),
	APLICADO("Aplicado"),
	RECHAZADO("Rechazado"),
	ANULADO("Anulado");

	private final String etiqueta;

	EstadoCambioEstado(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public boolean pendiente() {
		return this == PROPUESTO || this == APROBADO;
	}
}
