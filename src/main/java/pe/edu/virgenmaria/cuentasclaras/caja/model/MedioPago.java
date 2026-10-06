package pe.edu.virgenmaria.cuentasclaras.caja.model;

/** Medio de pago. Los digitales (todos menos efectivo) exigen el número de operación y se verifican contra el banco. */
public enum MedioPago {

	EFECTIVO("Efectivo"),
	YAPE("Yape"),
	PLIN("Plin"),
	TRANSFERENCIA("Transferencia"),
	TARJETA("Tarjeta");

	private final String etiqueta;

	MedioPago(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public boolean digital() {
		return this != EFECTIVO;
	}

	/** Medios que confirma una pasarela de pagos en línea (sprint 4). */
	public boolean enLinea() {
		return this == YAPE || this == PLIN || this == TARJETA;
	}
}
