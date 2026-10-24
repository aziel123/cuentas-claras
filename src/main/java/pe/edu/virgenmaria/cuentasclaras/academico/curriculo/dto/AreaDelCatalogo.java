package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

/** Sprint 10, tanda 1: un área del catálogo para las pantallas y los otros módulos (sin la entidad). */
public record AreaDelCatalogo(Long id, Long versionId, Nivel nivel, String codigo, String nombre, int orden,
		boolean transversal, boolean exonerable) {
}
