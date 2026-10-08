package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * DEVOLUCION: el dinero vuelve al apoderado. CORRECCION: el mismo dinero se aplica a otras cuotas (incluso de otra
 * familia) con un pago de reemplazo en la misma caja y una boleta nueva. CONTRACARGO (correcciones del sprint 4, S4-A3):
 * el apoderado desconoció un pago en línea y su banco ya le devolvió el dinero; se anula SIN reembolso.
 */
public enum TipoAnulacion {

	DEVOLUCION("Devolución"),
	CORRECCION("Corrección"),
	CONTRACARGO("Contracargo");

	private final String etiqueta;

	TipoAnulacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
