package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2: la entrada «Notas» del docente: las áreas que califica en cada sección (el tutor, también las
 * transversales), las secciones donde su asignación todavía no tiene áreas del catálogo (no puede calificar) y sus notas
 * tardías aprobadas por registrar (48 horas).
 */
public record MisNotasVista(List<AreaDeSeccion> areas, List<String> sinAreas, List<TardiaPorRegistrar> tardias) {

	public MisNotasVista {
		areas = List.copyOf(areas);
		sinAreas = List.copyOf(sinAreas);
		tardias = List.copyOf(tardias);
	}

	public record AreaDeSeccion(Long seccionId, String seccion, Long areaId, String area, boolean transversal) {
	}

	public record TardiaPorRegistrar(Long solicitudId, Long seccionId, Long areaId, Long periodoId, String resumen) {
	}
}
