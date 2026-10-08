package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** Qué se debía antes de usar el sistema. */
public enum ConceptoSaldo {

	MATRICULA("Matrícula"),
	PENSION("Pensión"),
	OTRO("Otro concepto");

	private final String etiqueta;

	ConceptoSaldo(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
