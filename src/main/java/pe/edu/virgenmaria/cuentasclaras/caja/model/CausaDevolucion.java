package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * Por qué se devuelve un pago (A2). Se elige al pedir la devolución y queda en los datos de la solicitud. Si es
 * {@link #PAGO_DUPLICADO}, el sistema exige que exista OTRO pago vigente de alguna de esas cuotas.
 */
public enum CausaDevolucion {

	PAGO_DUPLICADO("Pago duplicado"),
	COBRO_EQUIVOCADO("Se cobró por error"),
	RETIRO("Retiro del alumno"),
	OTRA("Otra causa");

	private final String etiqueta;

	CausaDevolucion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
