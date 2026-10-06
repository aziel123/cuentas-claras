package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * Medio de pago. Los digitales (todos menos efectivo) exigen el número de operación y se verifican contra el banco.
 * RECAUDACION_BANCARIA (sprint 4, tanda 2): lo pagó la familia en el banco con el código del alumno; solo lo registra
 * {@code sistema.recaudacion} con el archivo del banco confirmado a ciegas, nunca una cajera.
 */
public enum MedioPago {

	EFECTIVO("Efectivo"),
	YAPE("Yape"),
	PLIN("Plin"),
	TRANSFERENCIA("Transferencia"),
	TARJETA("Tarjeta"),
	RECAUDACION_BANCARIA("Banco (código de alumno)");

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

	/** Medios que una cajera puede registrar en ventanilla (la recaudación bancaria entra sola). */
	public boolean enVentanilla() {
		return this != RECAUDACION_BANCARIA;
	}

	/** Medios que confirma una pasarela de pagos en línea (sprint 4). */
	public boolean enLinea() {
		return this == YAPE || this == PLIN || this == TARJETA;
	}
}
