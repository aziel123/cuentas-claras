package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

public enum EstadoAlumno {

	ACTIVO("Activo"),
	RETIRADO("Retirado"),
	EGRESADO("Egresado");

	private final String etiqueta;

	EstadoAlumno(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
