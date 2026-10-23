package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2 (sección 3.5): las notas de TODA una sección en un periodo, de solo lectura, para su tutor y para
 * Dirección. Solo nombres de los alumnos (decisión 142): sin documentos, contactos ni deudas.
 * {@code puedeCorregir}: Dirección pide desde aquí la corrección de una nota (el tutor, solo desde su hoja).
 */
public record NotasSeccionVista(Long seccionId, String seccion, List<HojaNotas.OpcionPeriodo> periodos, Long periodoId,
		String periodo, List<AreaConNotas> areas, boolean puedeCorregir) {

	public NotasSeccionVista {
		periodos = List.copyOf(periodos);
		areas = List.copyOf(areas);
	}

	/** Un área con sus competencias (columnas) y una fila por alumno. */
	public record AreaConNotas(Long areaId, String area, List<HojaNotas.ColumnaCompetencia> competencias,
			List<HojaNotas.FilaAlumno> filas) {
	}
}
