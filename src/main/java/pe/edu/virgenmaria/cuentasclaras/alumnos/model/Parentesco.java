package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

/** Relación del apoderado con los alumnos de su familia. */
public enum Parentesco {

	MADRE("Madre"),
	PADRE("Padre"),
	ABUELO("Abuelo o abuela"),
	TIO("Tío o tía"),
	HERMANO("Hermano o hermana"),
	TUTOR_LEGAL("Tutor legal"),
	OTRO("Otro");

	private final String etiqueta;

	Parentesco(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
