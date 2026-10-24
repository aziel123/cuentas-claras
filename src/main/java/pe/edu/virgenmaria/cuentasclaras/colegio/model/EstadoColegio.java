package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/**
 * Sprint 8, tanda 1 (sección 3.4, decisión 116). Un colegio nace {@code PREPARACION}: se crean sus cuentas, su año, sus
 * alumnos y sus pensiones, pero la base no deja registrar pagos ni mandar recordatorios de cobro. Pasa a
 * {@code PRODUCCION} con la firma de su Promotoría y nunca vuelve atrás.
 * <p>
 * Sprint 11, tanda 1 (sección 3.2): los cambios que deciden dos operadores ({@code cambio_estado_colegio}). {@code BAJA}:
 * un colegio en preparación que no arrancó (sin dinero ni datos). {@code SUSPENDIDO}: en producción, a los 30 días de
 * aprobado; el personal no ingresa y las familias entran en modo consulta; se reactiva. {@code CERRADO} y
 * {@code SUPRIMIDO} se usan en la tanda 2 (exportación entregada y supresión con acta).
 */
public enum EstadoColegio {

	PREPARACION("En preparación"),
	PRODUCCION("En producción"),
	BAJA("Dado de baja"),
	SUSPENDIDO("Suspendido"),
	CERRADO("Cerrado"),
	SUPRIMIDO("Suprimido");

	private final String etiqueta;

	EstadoColegio(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** El colegio opera: su personal ingresa y sus procesos corren (preparación o producción). */
	public boolean opera() {
		return this == PREPARACION || this == PRODUCCION;
	}

	/** Las familias entran solo a consultar (estado de cuenta, comprobantes, libretas y comunicados): sección 3.8. */
	public boolean consultaFamilias() {
		return this == SUSPENDIDO || this == CERRADO;
	}
}
