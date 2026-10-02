package pe.edu.virgenmaria.cuentasclaras.aprobaciones.model;

public enum EstadoSolicitud {

	PENDIENTE("Pendiente", "alerta"),
	APROBADA("Aprobada", "exito"),
	RECHAZADA("Rechazada", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoSolicitud(String etiqueta, String variante) {
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
