package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

public enum EstadoMatricula {

	ACTIVA("Activa"),
	RETIRADA("Retirada");

	private final String etiqueta;

	EstadoMatricula(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
