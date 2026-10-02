package pe.edu.virgenmaria.cuentasclaras.caja.model;

/** Lo que Administración encontró en el estado de cuenta del banco (o de Yape/Plin). */
public enum ResultadoVerificacion {

	ENCONTRADO("Encontrado", "exito"),
	NO_ENCONTRADO("No aparece", "peligro");

	private final String etiqueta;

	private final String variante;

	ResultadoVerificacion(String etiqueta, String variante) {
		this.etiqueta = etiqueta;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String variante() {
		return variante;
	}
}
