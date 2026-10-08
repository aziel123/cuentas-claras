package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

/**
 * Qué alerta sale al celular de Promotoría (sprint 6, tanda 2, sección 12.2). El texto del mensaje es FIJO por tipo: nunca
 * el texto de la alerta, que puede llevar la explicación escrita por la cajera o el nombre de una familia (hallazgo 2).
 */
public enum TipoAviso {

	CIERRE_CON_DIFERENCIA("Cierre de caja con diferencia"),
	CAJA_SIN_CERRAR("Una caja de un día anterior sigue abierta"),
	CIERRE_NO_REALIZADO("Una caja no se cerró a la hora límite"),
	ANULACION_PAGO_PENDIENTE("Anulación de pago por aprobar"),
	DEPOSITO_DISTINTO("Depósito distinto de lo contado"),
	NO_APARECE_EN_BANCO("Un pago o depósito no aparece en el banco"),
	AVISO_FAMILIA_GRAVE("Una familia avisa de un pago que no reconoce o que no aparece"),
	HUELLA("La bitácora no coincide con su huella"),
	RESUMEN_NO_SALIO("El resumen diario no salió"),
	CIFRAS_CAMBIARON("Las cifras de un día ya informado cambiaron"),
	LLAMADA_NO_CONFIRMA("Una familia no confirma lo registrado"),
	/** Correcciones del sprint 6 (S6-M1): hay una foto del resumen que no guardó sistema.panel. */
	RESUMEN_SUPLANTADO("Hay un resumen diario que no generó el sistema"),
	/** S6-M2 (ATENCIÓN): una familia de la llamada de control no contestó dos veces y se eligió otra. */
	LLAMADA_REEMPLAZADA("Una familia de la llamada de control no contestó dos veces"),
	/** S6-M2 (ATENCIÓN): Dirección registró una llamada de control con la semana delegada. */
	LLAMADA_POR_DIRECCION("Dirección registró una llamada de control"),
	OTRA_CRITICA("Alerta crítica");

	private final String texto;

	TipoAviso(String texto) {
		this.texto = texto;
	}

	/** El texto fijo que sale por WhatsApp o correo ({{1}} de cc_alerta_promotoria). */
	public String texto() {
		return texto;
	}

	/** Las ATENCIÓN que también salen al celular (decisión 70); de las demás, solo las CRÍTICAS. */
	public boolean saleAunqueNoSeaCritica() {
		return this == CIERRE_NO_REALIZADO || this == ANULACION_PAGO_PENDIENTE || this == LLAMADA_REEMPLAZADA
				|| this == LLAMADA_POR_DIRECCION;
	}

	/**
	 * S6-B2 (QA-S6-7): los tipos que solo salen como CRÍTICA. No cuentan para el tope diario: la cajera no puede agotarlo
	 * con anulaciones por aprobar para que su faltante de caja no llegue ese día.
	 */
	public boolean esCritico() {
		return !saleAunqueNoSeaCritica();
	}
}
