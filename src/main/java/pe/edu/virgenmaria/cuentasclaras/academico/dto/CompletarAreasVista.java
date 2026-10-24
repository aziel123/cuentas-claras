package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2 (sección 4 del brief): «Completar áreas». Una propuesta de reemplazo YA LLENADA por cada asignación
 * vigente sin áreas del catálogo: mismas claves (sección, docente, tipo) y las áreas SUGERIDAS desde el texto libre del
 * sprint 9. Quien propone las revisa y confirma; la aprueba otra persona (como toda asignación).
 */
public record CompletarAreasVista(Long seccionId, List<Propuesta> propuestas) {

	public CompletarAreasVista {
		propuestas = List.copyOf(propuestas);
	}

	public record Propuesta(Long asignacionId, String seccion, String docente, String tipo, String textoAnterior,
			List<OpcionArea> areas) {
	}

	/** Un área del catálogo del nivel de la sección; {@code sugerida}: coincide con el texto anterior. */
	public record OpcionArea(Long id, String nombre, boolean sugerida) {
	}
}
