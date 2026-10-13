package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/**
 * Sprint 8, tanda 1 (sección 3.2): un {@code COLEGIO} es un cliente (familias, cuotas y pagos); la {@code PLATAFORMA} es la
 * única fila donde viven los operadores ({@code OPERADOR_PLATAFORMA}) y nunca tiene familias ni dinero.
 */
public enum TipoColegio {

	COLEGIO("Colegio"),
	PLATAFORMA("Plataforma");

	private final String etiqueta;

	TipoColegio(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
