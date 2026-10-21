package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 2 (sección 3.5, Ley 29733): el motivo de una justificación es de una LISTA; la salud es un dato
 * sensible, por eso no se pide el diagnóstico (la nota es opcional, corta y con aviso).
 */
public enum MotivoJustificacion {

	SALUD("Salud"),
	FAMILIAR("Motivo familiar"),
	TRAMITE("Trámite"),
	OTRO("Otro");

	private final String etiqueta;

	MotivoJustificacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
