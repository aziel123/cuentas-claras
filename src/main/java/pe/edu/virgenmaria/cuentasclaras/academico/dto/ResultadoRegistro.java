package pe.edu.virgenmaria.cuentasclaras.academico.dto;

/** Sprint 9, tanda 2: lo que quedó guardado («26 presentes, 2 faltas, 1 tarde»). */
public record ResultadoRegistro(long registroId, int presentes, int faltas, int tardes) {

	public String resumen() {
		return presentes + (presentes == 1 ? " presente, " : " presentes, ") + faltas
				+ (faltas == 1 ? " falta, " : " faltas, ") + tardes + (tardes == 1 ? " tarde" : " tardes");
	}
}
