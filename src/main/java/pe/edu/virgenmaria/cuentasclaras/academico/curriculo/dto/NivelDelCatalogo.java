package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;

/** Sprint 10, tanda 1: lo que la pantalla del catálogo muestra de un nivel (áreas con sus competencias y la escala). */
public record NivelDelCatalogo(Nivel nivel, List<AreaConCompetencias> areas, List<ValorEscala> escala) {

	public record AreaConCompetencias(AreaDelCatalogo area, List<CompetenciaDelCatalogo> competencias) {
	}
}
