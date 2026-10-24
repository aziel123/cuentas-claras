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
	APROBACION_CONTACTO("Solicitud de cambio de contacto", false),
	/**
	 * Correcciones del sprint 7 (S7-B1): la pantalla de cobro de caja ({@code /caja/familias/{id}}). Queda registrada con
	 * la familia, pero NO cuenta para la alerta de más de 50 fichas en un día: una cajera abre decenas de familias al día
	 * para cobrar (y cada cobro queda además en la bitácora).
	 */
	COBRO("Pantalla de cobro de caja", false),
	/**
	 * Sprint 9, tanda 1 (sección 3.3): la lista de los alumnos de una sección (nombres y número de orden), con la cantidad.
	 * Es una lista, no fichas: no cuenta para la alerta de «más de 50 fichas en un día».
	 */
	LISTA_SECCION("Lista de alumnos de una sección", false),
	/** Sprint 9, tanda 2: el reporte mensual de asistencia de una sección o un alumno (con la cantidad). */
	REPORTE_ASISTENCIA("Reporte de asistencia", false),
	/** Sprint 9, tanda 3: la nómina de una sección para el SIAGIE (DNI y fechas de nacimiento; con la cantidad). */
	NOMINA_SIAGIE("Nómina para el SIAGIE", false),
	/**
	 * Sprint 10 (H7): los tres tipos que V35 agrega al CHECK de una vez. Las notas de una sección (tanda 2), una libreta
	 * (con el alumno; tanda 3) y la exportación de calificaciones para el SIAGIE (tanda 3). La tanda 1 aún no los registra.
	 */
	NOTAS_SECCION("Notas de una sección", false),
	LIBRETA("Libreta de un alumno", false),
	CALIFICACIONES_SIAGIE("Calificaciones para el SIAGIE", false);

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
