package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/** Niveles de la Educación Básica Regular. */
public enum Nivel {

	INICIAL("Inicial"),
	PRIMARIA("Primaria"),
	SECUNDARIA("Secundaria");

	private final String etiqueta;

	Nivel(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
