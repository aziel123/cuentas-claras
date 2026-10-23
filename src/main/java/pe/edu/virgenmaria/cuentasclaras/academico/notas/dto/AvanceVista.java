package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2: el avance del registro de notas de un periodo para Dirección y Promotoría, SIN alumnos: por sección y
 * área, cuántas notas se esperan (competencias por alumnos no exonerados) y cuántas hay. {@code sinAreas}: las asignaciones
 * vigentes sin áreas del catálogo (no pueden calificar).
 */
public record AvanceVista(List<HojaNotas.OpcionPeriodo> periodos, Long periodoId, String periodo, String estadoPeriodo,
		List<FilaAvance> filas, List<SinAreas> sinAreas, int esperadas, int conNota) {

	public AvanceVista {
		periodos = List.copyOf(periodos);
		filas = List.copyOf(filas);
		sinAreas = List.copyOf(sinAreas);
	}

	public int faltan() {
		return esperadas - conNota;
	}

	public List<FilaAvance> pendientes() {
		return filas.stream().filter(f -> f.faltan() > 0).toList();
	}

	/** Una sección y un área: quién la califica y cuántas notas faltan. */
	public record FilaAvance(Long seccionId, String seccion, Long areaId, String area, String docentes, int esperadas,
			int conNota) {

		public int faltan() {
			return esperadas - conNota;
		}
	}

	/** Una asignación vigente sin áreas del catálogo (sprint 9). */
	public record SinAreas(Long asignacionId, Long seccionId, String seccion, String docente, String tipo) {
	}
}
