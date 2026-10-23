package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10, tanda 2: las áreas que la persona confirmó en «Completar áreas», por asignación (parámetros
 * {@code areas-{asignacion}} con un valor por área marcada). Lo demás del formulario se ignora.
 */
public final class AreasPorAsignacion {

	public static final String PREFIJO = "areas-";

	private AreasPorAsignacion() {
	}

	public static Map<Long, List<Long>> deFormulario(Map<String, List<String>> parametros) {
		Map<Long, List<Long>> resultado = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> p : parametros.entrySet()) {
			if (!p.getKey().startsWith(PREFIJO)) {
				continue;
			}
			try {
				Long asignacion = Long.valueOf(p.getKey().substring(PREFIJO.length()));
				List<Long> areas = new ArrayList<>();
				for (String v : p.getValue()) {
					areas.add(Long.valueOf(v.strip()));
				}
				resultado.put(asignacion, areas.stream().distinct().toList());
			}
			catch (NumberFormatException e) {
				throw new ReglaNegocioException("El formulario tiene un dato que no se entiende: vuelve a abrirlo.");
			}
		}
		return resultado;
	}
}
