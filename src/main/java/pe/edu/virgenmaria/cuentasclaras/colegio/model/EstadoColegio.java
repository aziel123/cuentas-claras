package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/**
 * Sprint 8, tanda 1 (sección 3.4, decisión 116). Un colegio nace {@code PREPARACION}: se crean sus cuentas, su año, sus
 * alumnos y sus pensiones, pero la base no deja registrar pagos ni mandar recordatorios de cobro. Pasa a
 * {@code PRODUCCION} con la firma de su Promotoría y nunca vuelve atrás.
 */
public enum EstadoColegio {

	PREPARACION("En preparación"),
	PRODUCCION("En producción");

	private final String etiqueta;

	EstadoColegio(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
