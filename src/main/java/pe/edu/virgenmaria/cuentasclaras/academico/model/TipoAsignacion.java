package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 1 (sección 3.2, decisión 140): qué hace un docente en una sección. Se guarda por nombre
 * ({@code VARCHAR(10)}, CHECK {@code ck_asignacion_docente_tipo}): nunca renombres un valor ya usado.
 */
public enum TipoAsignacion {

	/** Uno por sección (UNIQUE). Ve la asistencia completa de su sección y las justificaciones (tanda 2). */
	TUTOR("Tutor(a)"),
	/** Dicta en la sección; sus áreas van como texto en este sprint («Matemática, Ciencia y Tecnología»). */
	DOCENTE("Docente de área"),
	/** Auxiliar de educación: solo marca y corrige la asistencia del día (tanda 2); no envía comunicados. */
	AUXILIAR("Auxiliar de educación");

	private final String etiqueta;

	TipoAsignacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
