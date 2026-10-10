package pe.edu.virgenmaria.cuentasclaras.familias.model;

/** Qué no cuadra, en lenguaje simple (pantalla 5). Los tres primeros son una alerta CRÍTICA para Promotoría. */
public enum TipoAvisoFamilia {

	PAGUE_Y_NO_APARECE("Pagué y no aparece", true),
	NO_RECONOZCO_PAGO("No reconozco un pago", true),
	NO_RECONOZCO_ANULACION_O_DESCUENTO("No reconozco una anulación o un descuento", true),
	OTRO("Otra cosa", false),
	/** Sprint 7, tanda 3 (Ley 29733): un pedido sobre los datos personales, con su derecho y su plazo. */
	DATOS_PERSONALES("Mis datos personales", false);

	private final String etiqueta;

	private final boolean critico;

	TipoAvisoFamilia(String etiqueta, boolean critico) {
		this.etiqueta = etiqueta;
		this.critico = critico;
	}

	public String etiqueta() {
		return etiqueta;
	}

	public boolean critico() {
		return critico;
	}
}
