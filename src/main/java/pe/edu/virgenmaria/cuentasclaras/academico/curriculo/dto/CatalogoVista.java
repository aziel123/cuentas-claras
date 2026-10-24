package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import java.util.List;

/** Sprint 10, tanda 1: la pantalla de solo lectura del catálogo ({@code /academico/curriculo}). */
public record CatalogoVista(List<VersionDelCatalogo> versiones, VersionDelCatalogo version, List<NivelDelCatalogo> niveles,
		int areas, int competencias, int valores) {
}
