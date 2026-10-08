package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/**
 * Cómo se armó una partida (sección 10.4 del diseño del sprint 4).
 * <ul>
 *   <li>EXACTA: misma operación canónica (o la referencia de la liquidación o la glosa del lote) y mismo monto: la
 *       confirma el sistema al confirmarse el extracto.</li>
 *   <li>SUGERIDA: mismo monto, fecha cercana y candidato único en ambos sentidos: la confirma una persona que no cobró
 *       ni depositó.</li>
 *   <li>MANUAL: la elige una persona, con nota.</li>
 *   <li>EXPLICADA: un abono o cargo ajeno a la cobranza (intereses, transferencia propia...), con categoría y nota.</li>
 * </ul>
 */
public enum ReglaPartida {

	EXACTA("Exacta"),
	SUGERIDA("Sugerida"),
	MANUAL("Manual"),
	EXPLICADA("Explicada");

	private final String etiqueta;

	ReglaPartida(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
