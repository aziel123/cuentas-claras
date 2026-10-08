package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

/**
 * Qué debía verse en el banco: un pago digital, un depósito de caja, una liquidación de la pasarela (neta), un lote de
 * recaudación, un reembolso digital o la devolución de una línea de recaudación (como cargo) o una explicación (abono o
 * cargo ajeno a la cobranza).
 */
public enum ObjetoPartida {

	PAGO("Pago digital", true),
	DEPOSITO("Depósito de caja", true),
	LIQUIDACION("Liquidación de la pasarela", true),
	LOTE_RECAUDACION("Recaudación del banco", true),
	REEMBOLSO("Devolución (reembolso)", false),
	/** Correcciones del sprint 4 (S4-A4): la devolución de una línea de recaudación sale del banco como cargo. */
	LINEA_RECAUDACION("Devolución de recaudación", false),
	EXPLICACION("Explicación", true);

	private final String etiqueta;

	private final boolean abono;

	ObjetoPartida(String etiqueta, boolean abono) {
		this.etiqueta = etiqueta;
		this.abono = abono;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** El tipo de movimiento con que se ve en el banco (el reembolso sale como cargo). */
	public TipoMovimiento movimiento() {
		return abono ? TipoMovimiento.ABONO : TipoMovimiento.CARGO;
	}

	/** «PAGO:125»: la clave única de una partida vigente ({@code objeto_vigente}). */
	public String clave(Long id) {
		return name() + ":" + id;
	}
}
