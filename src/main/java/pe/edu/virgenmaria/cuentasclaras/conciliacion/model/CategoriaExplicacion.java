package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/** Por qué un movimiento del extracto no es de la cobranza (se explica con categoría y nota). */
public enum CategoriaExplicacion {

	INTERESES("Intereses del banco"),
	TRANSFERENCIA_PROPIA("Transferencia entre cuentas del colegio"),
	APORTE("Aporte de la promotoría"),
	COMISION_BANCARIA("Comisión del banco"),
	IMPUESTO_ITF("Impuesto ITF"),
	OTRO_INGRESO("Otro ingreso"),
	OTRO_EGRESO("Otro egreso");

	private final String etiqueta;

	CategoriaExplicacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
