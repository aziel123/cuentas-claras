package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 2 (decisión 145): el estado de un alumno en un día. FALTA_JUSTIFICADA solo la crea la familia desde el
 * portal (marca JUSTIFICACION) o una corrección aprobada (A1, {@code ck_marca_asistencia_justificada}).
 */
public enum EstadoAsistencia {

	PRESENTE("Presente", "presente", "exito"),
	TARDE("Tarde", "llegó tarde", "alerta"),
	FALTA("Falta", "falta", "peligro"),
	FALTA_JUSTIFICADA("Falta justificada", "falta justificada", "neutro");

	private final String etiqueta;

	private final String enElAviso;

	private final String variante;

	EstadoAsistencia(String etiqueta, String enElAviso, String variante) {
		this.etiqueta = etiqueta;
		this.enElAviso = enElAviso;
		this.variante = variante;
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Cómo lo dice el aviso a la familia («Mateo: falta. Lucía: llegó tarde (08:12)»). */
	public String enElAviso() {
		return enElAviso;
	}

	/** Variante del badge (exito, alerta, peligro, neutro): siempre con su texto. */
	public String variante() {
		return variante;
	}

	/** Lo que el aviso a la familia menciona (A1: también la falta justificada). */
	public boolean seAvisa() {
		return this != PRESENTE;
	}
}
