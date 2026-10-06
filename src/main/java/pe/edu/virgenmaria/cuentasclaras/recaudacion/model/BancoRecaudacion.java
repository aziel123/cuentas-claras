package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

/**
 * Banco cuyo formato de recaudación se lee. GENERICO: el formato CSV o XLSX del colegio (sección 10.3 del diseño). El
 * adaptador de cada banco se construye cuando el banco entregue un archivo de ejemplo (anonimizado) para probarlo.
 */
public enum BancoRecaudacion {

	GENERICO("Formato genérico"),
	BCP("BCP"),
	INTERBANK("Interbank"),
	BBVA("BBVA"),
	SCOTIABANK("Scotiabank");

	private final String etiqueta;

	BancoRecaudacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
