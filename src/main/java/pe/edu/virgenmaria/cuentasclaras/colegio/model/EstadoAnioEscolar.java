package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/** Ciclo de vida de un año escolar. Solo uno puede estar EN_CURSO por colegio (también lo exige la base). */
public enum EstadoAnioEscolar {

	PLANIFICADO("Planificado"),
	EN_CURSO("En curso"),
	CERRADO("Cerrado");

	private final String etiqueta;

	EstadoAnioEscolar(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
