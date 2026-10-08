package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

/**
 * Afectación al IGV (catálogo 07 de SUNAT). Los servicios educativos de una institución educativa para sus fines propios
 * no están gravados (TUO de la Ley del IGV, art. 2; a confirmar por el contador).
 */
public enum AfectacionIgv {

	INAFECTO("30", "Operación inafecta al IGV"),
	EXONERADO("20", "Operación exonerada del IGV");

	private final String codigoSunat;

	private final String etiqueta;

	AfectacionIgv(String codigoSunat, String etiqueta) {
		this.codigoSunat = codigoSunat;
		this.etiqueta = etiqueta;
	}

	public String codigoSunat() {
		return codigoSunat;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
