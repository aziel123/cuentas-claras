package pe.edu.virgenmaria.cuentasclaras.academico.model;

/** Sprint 9, tanda 2 (H2, decisión 152): para qué se usa el contacto de la familia (Ley 29733: finalidad nueva). */
public enum Finalidad {

	ASISTENCIA("Avisos de asistencia"),
	COMUNICADOS("Comunicados del colegio");

	private final String etiqueta;

	Finalidad(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
