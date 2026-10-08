package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.MuestraAlAzar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elige las familias de la llamada de control con la semilla SECRETA de la semana (sprint 6, tanda 3; decisión 77). Sin
 * estado ni base: la misma semilla y las mismas candidatas dan la misma muestra.
 * <ul>
 *   <li>Las candidatas se barajan con la semilla (el orden de entrada debe ser fijo: por id).</li>
 *   <li>Todas las plazas menos una van a las de mayor prioridad (sin portal y con un solo apoderado, después las que
 *       cumplen una de las dos); los empates los decide el azar.</li>
 *   <li>La última plaza es al azar entre TODAS las demás: ninguna familia que paga en efectivo queda fuera del control
 *       solo porque usa el portal (la cajera no sabe a quién no llamarán).</li>
 * </ul>
 */
final class MuestraLlamadas {

	private MuestraLlamadas() {
	}

	static List<FamiliaParaLlamada> elegir(long semilla, List<FamiliaParaLlamada> candidatas, int cuantas) {
		if (cuantas <= 0 || candidatas.isEmpty()) {
			return List.of();
		}
		List<FamiliaParaLlamada> barajadas = MuestraAlAzar.delDia(semilla, candidatas, candidatas.size());
		List<FamiliaParaLlamada> porPrioridad = new ArrayList<>(barajadas);
		porPrioridad.sort(Comparator.comparingInt(FamiliaParaLlamada::prioridad).reversed());
		List<FamiliaParaLlamada> elegidas = new ArrayList<>(porPrioridad.subList(0, Math.min(cuantas - 1,
				porPrioridad.size())));
		for (FamiliaParaLlamada f : barajadas) {
			if (elegidas.size() >= cuantas) {
				break;
			}
			if (!elegidas.contains(f)) {
				elegidas.add(f);
			}
		}
		return List.copyOf(elegidas);
	}
}
