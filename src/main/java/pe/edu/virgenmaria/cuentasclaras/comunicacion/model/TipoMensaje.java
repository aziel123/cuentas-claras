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
	AVISO_ATENDIDO("Respuesta a tu aviso", false),
	// Correcciones del sprint 5
	VERIFICACION_CONTACTO("Confirmar contacto", false),
	APODERADO_AGREGADO("Apoderado agregado", false),
	CONTACTO_POR_VERIFICAR("Contacto por confirmar", false),
	FERIADO_PROPUESTO("Día no laborable propuesto", false),
	// Sprint 6 · tanda 2: al personal de Promotoría (y de Dirección, las anulaciones por aprobar). Los crea solo
	// sistema.panel (trg_mensaje_nace).
	RESUMEN_DIARIO("Resumen del día", false),
	ALERTA_PROMOTORIA("Alerta para Promotoría", false),
	// Sprint 8 · tanda 2: a TODA la Promotoría y al correo externo de control, en la misma transacción del cambio.
	CONECTOR_VIGENTE("Cambio de conector del colegio", false),
	COLEGIO_EN_PRODUCCION("Colegio en producción", false),
	// Correcciones del sprint 8 (S8-A1, S8-A2 y S8-M5): lo que el operador de la plataforma cambia en un colegio se avisa
	// al titular, al correo institucional, al correo externo de control y a la Promotoría, en la misma transacción.
	AVISO_ALTA("Aviso del alta del colegio", false),
	CORREO_CONTROL("Cambio del correo externo de control", false),
	// Sprint 9, tanda 2 (sección 3.6): avisos de asistencia a las familias, con tipos y plantillas propios (nunca mezclados
	// con la cobranza: INDECOPI). Los crea solo sistema.asistencia (trg_mensaje_nace).
	ASISTENCIA_DIA("Aviso de asistencia del día", false),
	ASISTENCIA_CORREGIDA("Corrección de asistencia", false),
	// Sprint 9, tanda 3 (sección 3.10): el resumen diario de comunicados y el urgente. Los crea solo sistema.comunicados
	// (trg_mensaje_nace); nunca llevan el texto del comunicado (el título, solo el urgente).
	COMUNICADOS_RESUMEN("Resumen de comunicados", false),
	COMUNICADO_URGENTE("Comunicado urgente", false);

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
