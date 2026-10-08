package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * De dónde sale una verificación bancaria (sprint 4, tanda 3).
 * <ul>
 *   <li>MANUAL: Administración escribió a ciegas lo que vio en el banco (sprint 3; ahora para las excepciones).</li>
 *   <li>AUTOMATICA: la deja {@code sistema.conciliacion} cuando una partida de la conciliación queda CONFIRMADA sobre
 *       un extracto CONFIRMADO a ciegas (con el monto y la fecha del movimiento del banco).</li>
 * </ul>
 */
public enum OrigenVerificacion {

	MANUAL("Manual (a ciegas)"),
	AUTOMATICA("Conciliación automática");

	private final String etiqueta;

	OrigenVerificacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
