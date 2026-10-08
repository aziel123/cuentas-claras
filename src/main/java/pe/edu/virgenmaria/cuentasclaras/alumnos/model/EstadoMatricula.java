package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

public enum EstadoMatricula {

	/** Sprint 5: matrícula del año siguiente, solo con su cuota de matrícula; pasa a ACTIVA al pagarla. */
	RESERVADA("Reservada"),
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
