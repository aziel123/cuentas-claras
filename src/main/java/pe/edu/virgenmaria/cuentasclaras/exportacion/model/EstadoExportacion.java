package pe.edu.virgenmaria.cuentasclaras.exportacion.model;

/** Sprint 11, tanda 2 (sección 3.9): el estado de una exportación de los datos de un colegio. */
public enum EstadoExportacion {

	GENERANDO("Generándose"),
	LISTA("Lista para entregar"),
	ENTREGADA("Entregada"),
	VENCIDA("Vencida"),
	FALLIDA("Fallida");

	private final String etiqueta;

	EstadoExportacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
