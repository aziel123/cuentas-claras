package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 2 (decisión 152): por dónde. WhatsApp exige el «sí» explícito (opt-in de Meta); el correo sale salvo
 * oposición.
 */
public enum Canal {

	WHATSAPP("WhatsApp", false),
	CORREO("Correo", true);

	private final String etiqueta;

	private final boolean salvoOposicion;

	Canal(String etiqueta, boolean salvoOposicion) {
		this.etiqueta = etiqueta;
		this.salvoOposicion = salvoOposicion;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Sin ninguna fila, ¿se le escribe? WhatsApp no; correo sí. */
	public boolean salvoOposicion() {
		return salvoOposicion;
	}
}
