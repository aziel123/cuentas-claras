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
	COMUNICADO_URGENTE("Comunicado urgente", false),
	// Sprint 10, tanda 3 (sección 3.7): la libreta está en el portal (o se corrigió). Los crea solo sistema.libretas
	// (trg_mensaje_nace); nunca llevan notas, conclusiones ni montos.
	LIBRETA_DISPONIBLE("Libreta disponible", false),
	LIBRETA_CORREGIDA("Libreta corregida", false),
	// Sprint 11, tanda 1 (AP-2): el código de cada uso de la facultad del titular. Va solo al titular, a su contacto
	// vigente (trg_mensaje_nace); ni el mensaje ni sus parámetros guardan el código: lo deriva el envío.
	CODIGO_FACULTAD("Código de la facultad del titular", false),
	/**
	 * Sprint 11, tanda 1c (decisión 216, A1c-5): el código de 6 dígitos de una acción sensible de Promotoría (también la
	 * facultad del titular), atado a esa acción. Guarda solo su SHA-256 (CHECK ck_mensaje_codigo).
	 */
	CODIGO_PROMOTORIA("Código de una acción de Promotoría", false),
	/** Sprint 11, tanda 1c (A1c-1): el código al contacto ANTERIOR de una cuenta directiva que cambia su contacto. */
	CODIGO_CONTACTO("Código para confirmar el cambio de contacto", false),
	/** Sprint 11, tanda 2 (AP2-4): el enlace de la entrega de la exportación, solo por correo al titular. */
	EXPORTACION_ENLACE("Enlace para descargar los datos del colegio", false),
	/** Sprint 11, tanda 2 (AP2-4): el código de la entrega, solo por WhatsApp al titular (guarda solo su SHA-256). */
	CODIGO_EXPORTACION("Código para descargar los datos del colegio", false);

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
