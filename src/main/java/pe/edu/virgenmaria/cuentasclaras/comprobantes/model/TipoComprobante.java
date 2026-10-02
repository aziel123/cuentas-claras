package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

/** Tipo de comprobante electrónico con su código del catálogo 01 de SUNAT. */
public enum TipoComprobante {

	BOLETA("03", "Boleta de venta electrónica"),
	FACTURA("01", "Factura electrónica"),
	NOTA_CREDITO("07", "Nota de crédito electrónica");

	private final String codigoSunat;

	private final String etiqueta;

	TipoComprobante(String codigoSunat, String etiqueta) {
		this.codigoSunat = codigoSunat;
		this.etiqueta = etiqueta;
	}

	public String codigoSunat() {
		return codigoSunat;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Letra con la que empieza su serie: B (boleta) o F (factura). La nota de crédito usa la del comprobante que anula. */
	public char letra() {
		return this == FACTURA ? 'F' : 'B';
	}
}
