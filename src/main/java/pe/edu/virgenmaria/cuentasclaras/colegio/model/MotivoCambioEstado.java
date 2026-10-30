package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/** Sprint 11, tanda 1 (sección 3.3): el motivo tipificado de un cambio de estado (más el motivo escrito, de 20 a 500). */
public enum MotivoCambioEstado {

	PRUEBA("Era un colegio de prueba"),
	NO_ARRANCO("No arrancó"),
	PEDIDO_COLEGIO("Lo pidió el colegio"),
	DUPLICADO("Alta duplicada"),
	FALTA_PAGO("Falta de pago"),
	FIN_CONTRATO("Fin del contrato"),
	PAGO_RECIBIDO("Pago recibido"),
	ACUERDO("Acuerdo con el colegio"),
	SEGURIDAD("Seguridad"),
	OTRO("Otro");

	private final String etiqueta;

	MotivoCambioEstado(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
