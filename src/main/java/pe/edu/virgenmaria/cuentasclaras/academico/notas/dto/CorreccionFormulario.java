package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2 (sección 3.6): lo que ve quien pide corregir una nota de un periodo CERRADO: dónde está, qué dice hoy y
 * qué niveles exigen la conclusión. Solo el nombre del alumno.
 */
public record CorreccionFormulario(Long calificacionId, Long seccionId, String seccion, Long areaId, String area,
		String competencia, String alumno, String periodo, String nivelActual, String conclusionActual,
		List<String> exigenConclusion, boolean conclusionSiempre, int conclusionMax) {
}
