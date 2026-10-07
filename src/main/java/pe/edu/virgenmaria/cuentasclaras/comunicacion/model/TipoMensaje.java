package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

/**
 * Para qué es un mensaje (sprint 5). Los de la tanda 1 son los avisos financieros (el control 4 de la skill: el padre es
 * el auditor), el aviso al contacto anterior, la activación de una cuenta y la huella diaria. Los demás llegan en las
 * tandas 2 y 3 (la base ya los admite).
 */
public enum TipoMensaje {

	PAGO_REGISTRADO("Pago registrado", true),
	PAGO_ANULADO("Pago anulado", true),
	DESCUENTO_APROBADO("Descuento aprobado", true),
	CONTACTO_CAMBIADO("Cambio de contacto", false),
	ACTIVACION_CUENTA("Enlace de acceso", false),
	HUELLA_BITACORA("Huella de la bitácora", false),
	RECORDATORIO_VENCIMIENTO("Recordatorio de vencimiento", false),
	CUOTA_VENCIDA("Cuota vencida", false),
	RENOVACION_MATRICULA("Renovación de matrícula", false),
	RENOVACION_REGISTRADA("Renovación registrada", false),
	AVISO_ATENDIDO("Respuesta a tu aviso", false);

	private final String etiqueta;

	private final boolean financiero;

	TipoMensaje(String etiqueta, boolean financiero) {
		this.etiqueta = etiqueta;
		this.financiero = financiero;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Avisos de pago, anulación y descuento: no se apagan y su falta es una alerta CRÍTICA. */
	public boolean financiero() {
		return financiero;
	}
}
