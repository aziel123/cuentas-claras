package pe.edu.virgenmaria.cuentasclaras.matricula.model;

/** Estados de la renovación de matrícula (sprint 5, sección 5). */
public enum EstadoRenovacion {

	PROPUESTA("Por responder", "alerta"),
	CONFIRMADA("Confirmada", "info"),
	MATRICULADA("Matrícula reservada", "exito"),
	NO_CONTINUA("No continúa", "neutro"),
	VENCIDA("Vencida sin respuesta", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoRenovacion(String etiqueta, String variante) {
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
