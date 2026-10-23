package pe.edu.virgenmaria.cuentasclaras.academico.libreta.model;

/**
 * Sprint 10, tanda 3 (M9): cómo se entregó una libreta. PORTAL, la descargó un apoderado de la familia con su sesión;
 * IMPRESA, la imprimió Dirección o el tutor de la sección; ENTREVISTA, en Inicial, se entregó en la entrevista con la familia.
 */
public enum CanalEntrega {

	PORTAL("Descargada en el portal"),
	IMPRESA("Impresa por el colegio"),
	ENTREVISTA("Entregada en la entrevista");

	private final String etiqueta;

	CanalEntrega(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
