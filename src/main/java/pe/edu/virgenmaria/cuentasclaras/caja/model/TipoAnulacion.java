package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * DEVOLUCION: el dinero vuelve al apoderado. CORRECCION: el mismo dinero se aplica a otras cuotas (incluso de otra
 * familia) con un pago de reemplazo en la misma caja y una boleta nueva.
 */
public enum TipoAnulacion {

	DEVOLUCION("Devolución"),
	CORRECCION("Corrección");

	private final String etiqueta;

	TipoAnulacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
