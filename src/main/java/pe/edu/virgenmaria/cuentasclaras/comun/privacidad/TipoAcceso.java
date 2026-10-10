package pe.edu.virgenmaria.cuentasclaras.comun.privacidad;

/**
 * Pantallas con datos personales cuyo acceso queda registrado (sprint 7, tanda 3; Ley 29733, sección 8.2 y decisión 96).
 * Los nombres coinciden con el CHECK {@code ck_acceso_dato_tipo} de V26.
 */
public enum TipoAcceso {

	FICHA_FAMILIA("Ficha de la familia", true),
	FICHA_ALUMNO("Ficha del alumno", true),
	BUSQUEDA("Búsqueda de alumnos o familias", false),
	MOROSOS("Familias morosas", false),
	LLAMADA_CONTROL("Llamada de control", false),
	IMPORTACION("Vista previa de la importación", false),
	APROBACION_CONTACTO("Solicitud de cambio de contacto", false);

	private final String etiqueta;

	private final boolean ficha;

	TipoAcceso(String etiqueta, boolean ficha) {
		this.etiqueta = etiqueta;
		this.ficha = ficha;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Si cuenta para la alerta de «más de N fichas en un día» (decisión 96). */
	public boolean ficha() {
		return ficha;
	}
}
