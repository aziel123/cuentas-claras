package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

/**
 * PENDIENTE → APLICADA (su pago) | EXCEPCION (no se pudo aplicar sola); EXCEPCION → APLICADA_REVISION (con una
 * aplicación aprobada) | DEVUELTA (con una devolución aprobada). Lo exige además {@code trg_linea_recaudacion_estado}.
 */
public enum EstadoLinea {

	PENDIENTE("Por aplicar", "neutro"),
	APLICADA("Aplicada", "exito"),
	EXCEPCION("Por revisar", "alerta"),
	APLICADA_REVISION("Aplicada tras revisión", "exito"),
	DEVUELTA("Devuelta", "neutro");

	private final String etiqueta;

	private final String variante;

	EstadoLinea(String etiqueta, String variante) {
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
