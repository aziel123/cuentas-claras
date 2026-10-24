package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;

/** Sprint 10, tanda 1: las áreas del catálogo de un nivel que se pueden dar en la propuesta (sin la transversal). */
public record AreasDelAnio(Nivel nivel, String etiqueta, List<AreaDelCatalogo> areas) {

	public AreasDelAnio {
		areas = List.copyOf(areas);
	}
}
