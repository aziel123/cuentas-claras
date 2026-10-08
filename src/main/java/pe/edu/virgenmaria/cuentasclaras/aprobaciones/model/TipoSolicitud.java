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
	CAMBIO_CONTACTO_PERSONAL("Cambio de celular o correo del personal");

	private final String etiqueta;

	TipoSolicitud(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
