package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

/** Por qué se descuenta. HERMANOS exige dos o más hermanos con matrícula activa en el año. */
public enum TipoDescuento {

	HERMANOS("Descuento por hermanos"),
	BECA("Beca"),
	OTRO("Otro descuento");

	private final String etiqueta;

	TipoDescuento(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
