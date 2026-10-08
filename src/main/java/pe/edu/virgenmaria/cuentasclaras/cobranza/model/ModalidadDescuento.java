package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** PORCENTAJE del monto de cada cuota o un MONTO fijo por cuota. */
public enum ModalidadDescuento {

	PORCENTAJE("Porcentaje"),
	MONTO("Monto fijo por cuota");

	private final String etiqueta;

	ModalidadDescuento(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
