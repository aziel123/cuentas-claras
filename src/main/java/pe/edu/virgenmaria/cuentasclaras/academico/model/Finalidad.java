package pe.edu.virgenmaria.cuentasclaras.academico.model;

/** Sprint 9, tanda 2 (H2, decisión 152): para qué se usa el contacto de la familia (Ley 29733: finalidad nueva). */
public enum Finalidad {

	ASISTENCIA("Avisos de asistencia"),
	COMUNICADOS("Comunicados del colegio"),
	/** Sprint 10, tanda 3 (decisión 180): el aviso de que la libreta está en el portal (nunca las notas). */
	LIBRETAS("Avisos de libretas");

	private final String etiqueta;

	Finalidad(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
