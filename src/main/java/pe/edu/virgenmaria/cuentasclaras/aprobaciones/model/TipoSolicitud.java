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
	DESCUENTO("Descuento o beca");

	private final String etiqueta;

	TipoSolicitud(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
