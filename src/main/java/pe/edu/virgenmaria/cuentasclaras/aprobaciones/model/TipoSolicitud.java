package pe.edu.virgenmaria.cuentasclaras.aprobaciones.model;

/**
 * Cambios que una sola persona no puede hacer: los pide y los aprueba otra de Promotoría o Dirección. Cada tipo tiene
 * su {@code ManejadorSolicitud} en el módulo dueño del dato. Sprint 3: anulaciones de pago y cierres de caja.
 * Se guardan por nombre ({@code VARCHAR(40)}): nunca renombres un valor ya usado.
 */
public enum TipoSolicitud {

	RETIRO_ALUMNO("Retiro de alumno"),
	CAMBIO_CONTACTO_APODERADO("Cambio de celular o correo"),
	CAMBIO_RESPONSABLE_PAGO("Cambio de responsable de pago"),
	FECHA_MATRICULA("Ingreso tardío"),
	ANULACION_CUOTA("Anulación de cuota"),
	// Sprint 3, tanda 2 (caja y cobranza)
	ANULACION_PAGO("Anulación de pago"),
	DESCUENTO("Descuento o beca"),
	// Sprint 3, tanda 3 (caja)
	CIERRE_CAJA("Cierre de caja"),
	REAPERTURA_CAJA("Reapertura de caja"),
	// Correcciones del sprint 3 (B2): RUC y razón social de un apoderado para emitir factura.
	DATOS_FACTURACION("Datos de facturación (RUC)"),
	// Sprint 4 (pagos en línea): un ingreso que no se pudo aplicar solo (orden por revisar) se aplica a otras cuotas o se
	// devuelve al mismo medio de origen. Los pide Administración y los aprueba Promotoría o Dirección.
	APLICAR_INGRESO("Aplicar un ingreso por revisar"),
	DEVOLVER_INGRESO("Devolver un ingreso por revisar"),
	// Correcciones del sprint 4 (S4-C1): una pareja del extracto elegida a mano (mismo monto) la aprueba otra persona de
	// Promotoría o Dirección antes de que verifique nada en el banco.
	PARTIDA_MANUAL("Pareja manual de la conciliación"),
	// Sprint 6, tanda 2 (hallazgo 5, P6): el celular o el correo de alguien del personal (por ahí le llegan la huella, el
	// resumen y las alertas). Lo pide el titular o Promotoría; lo aprueba otra persona de Promotoría o Dirección.
	CAMBIO_CONTACTO_PERSONAL("Cambio de celular o correo del personal"),
	// Sprint 7, tanda 2 (H1, decisión 84): dar o quitar Promotoría o Dirección. Lo pide Promotoría; lo aprueba OTRA persona
	// de Promotoría o Dirección (nunca quien lo pidió ni el titular). En MySQL, los triggers de usuario_rol lo exigen.
	CAMBIO_ROLES("Cambio de roles de Promotoría o Dirección"),
	// Correcciones del sprint 7 (observación de QA): desactivar o reactivar una cuenta con Promotoría o Dirección. Lo pide
	// Promotoría; lo aprueba OTRA persona de Promotoría o Dirección (nunca quien lo pidió ni el titular). En MySQL,
	// trg_usuario_identidad lo exige; el colegio nunca queda sin Promotoría activa.
	ESTADO_CUENTA("Desactivar o reactivar una cuenta de Promotoría o Dirección"),
	// Sprint 8, tanda 2 (sección 3.6, E12): cambiar un conector del colegio (OSE, pasarela, cuenta bancaria o recaudación)
	// es cambiar adónde entra el dinero. Lo propone Administración o Promotoría; lo aprueba OTRA persona de Promotoría o
	// Dirección con su firma; si mueve dinero, una Promotoría participa. En MySQL lo exige trg_conector_colegio_estado.
	CAMBIO_CONECTOR("Cambio de conector (adónde entra el dinero)"),
	// Sprint 9, tanda 1 (sección 3.2, decisión 139): asignar un docente, tutor o auxiliar a una sección le da acceso a los
	// nombres de sus alumnos. Lo propone Administración o Dirección; lo aprueba OTRA persona de Dirección o Promotoría con su
	// firma, nunca el docente asignado. En MySQL lo exige trg_asignacion_docente_estado.
	ASIGNACION_DOCENTE("Asignación de docente a una sección"),
	// Sprint 9, tanda 2 (sección 3.4, decisiones 146 y 147): corregir la asistencia de otro día (o después del cierre de las
	// correcciones del día) y registrar un día pasado. Lo pide un docente de la sección; lo aprueba OTRA persona de
	// Dirección o Promotoría con su firma. En MySQL lo exigen trg_marca_asistencia_nace y trg_registro_asistencia_nace.
	CORRECCION_ASISTENCIA("Corrección de asistencia"),
	REGISTRO_ASISTENCIA_TARDIO("Registro de asistencia de un día pasado");

	private final String etiqueta;

	TipoSolicitud(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
