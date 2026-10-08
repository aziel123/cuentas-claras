package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.Set;

/**
 * Elige las familias de la llamada de control con la semilla SECRETA de la semana (sprint 6, tanda 3; decisión 77). Sin
 * estado ni base: la misma semilla y las mismas candidatas dan la misma muestra. Correcciones del sprint 6:
 * <ul>
 *   <li><b>Muestreo ponderado sin plazas fijas</b> (S6-B3, QA-S6-2): cada candidata tiene un peso (no usa el portal, tiene
 *       un solo apoderado, tiene deuda vencida, dejó de pagar en efectivo) y la muestra son las de mayor clave
 *       {@code ln(u) / peso} (Efraimidis y Spirakis), con {@code u} del azar de la semilla. Una familia de peso alto sale
 *       más seguido, pero ninguna sale siempre ni nunca: la cajera no puede saber a quién no llamarán.</li>
 *   <li><b>Una plaza reservada para la deuda vencida</b> (S6-A2): si hay candidatas con deuda vencida, la primera plaza es
 *       la de mayor clave entre ellas. El efectivo que nunca se registró no deja un pago, pero sí deuda.</li>
 * </ul>
 * Las candidatas entran ordenadas por id (el orden fija qué número del azar le toca a cada una).
 */
final class MuestraLlamadas {

	private MuestraLlamadas() {
	}

	/**
	 * Una familia candidata.
	 *
	 * @param deudaVencida         tiene deuda vencida al lunes
	 * @param dejoDePagarEfectivo  pagaba en efectivo (antes de las 5 semanas) y en las últimas 5 no pagó nada, con deuda
	 *                             vencida: la señal más fuerte de un pago en efectivo que no se registró
	 */
	record Candidata(FamiliaParaLlamada perfil, boolean deudaVencida, boolean dejoDePagarEfectivo) {

		Candidata {
			Objects.requireNonNull(perfil, "perfil");
		}

		Long familiaId() {
			return perfil.familiaId();
		}

		/** De 1 a 6: 1 más uno por cada señal (sin portal, un solo apoderado, deuda) y dos si dejó de pagar en efectivo. */
		int peso() {
			return 1 + perfil.prioridad() + (deudaVencida ? 1 : 0) + (dejoDePagarEfectivo ? 2 : 0);
		}
	}

	/** Sin datos de deuda (todas pagaron en efectivo y ninguna debe): solo pesan el portal y los apoderados. */
	static List<FamiliaParaLlamada> elegir(long semilla, List<FamiliaParaLlamada> perfiles, int cuantas) {
		List<Candidata> candidatas = perfiles.stream().map(p -> new Candidata(p, false, false)).toList();
		return elegirCandidatas(semilla, candidatas, cuantas).stream().map(Candidata::perfil).toList();
	}

	/** La muestra, en el orden en que se eligió (la plaza de la deuda primero). */
	static List<Candidata> elegirCandidatas(long semilla, List<Candidata> candidatas, int cuantas) {
		if (cuantas <= 0 || candidatas.isEmpty()) {
			return List.of();
		}
		Map<Long, Double> claves = claves(semilla, candidatas);
		Comparator<Candidata> porClave = Comparator.comparing((Candidata c) -> claves.get(c.familiaId())).reversed()
				.thenComparing(Candidata::familiaId);
		List<Candidata> elegidas = new ArrayList<>();
		candidatas.stream().filter(Candidata::deudaVencida).min(porClave).ifPresent(elegidas::add);
		candidatas.stream().sorted(porClave).filter(c -> !elegidas.contains(c)).limit(Math.max(0, cuantas - elegidas.size()))
				.forEach(elegidas::add);
		return List.copyOf(elegidas.subList(0, Math.min(cuantas, elegidas.size())));
	}

	/**
	 * S6-M2: la que reemplaza a una familia que no contestó dos veces: la de mayor clave (con la MISMA semilla) entre las
	 * candidatas que todavía no están en la muestra de la semana. Vacío si ya no queda ninguna.
	 */
	static java.util.Optional<Candidata> reemplazo(long semilla, List<Candidata> candidatas, Set<Long> yaEnLaMuestra) {
		Map<Long, Double> claves = claves(semilla, candidatas);
		return candidatas.stream().filter(c -> !yaEnLaMuestra.contains(c.familiaId()))
				.max(Comparator.comparing((Candidata c) -> claves.get(c.familiaId()))
						.thenComparing(Comparator.comparing(Candidata::familiaId).reversed()));
	}

	/**
	 * {@code ln(u) / peso} con {@code u} en (0, 1]: con más peso, la clave queda más cerca de 0 (más alta). El azar es
	 * {@link SplittableRandom}, que mezcla la semilla: con {@code java.util.Random}, semillas cercanas dan primeros
	 * números casi iguales y la primera candidata quedaba casi siempre en el mismo lugar.
	 */
	private static Map<Long, Double> claves(long semilla, List<Candidata> candidatas) {
		SplittableRandom azar = new SplittableRandom(semilla);
		Map<Long, Double> claves = new HashMap<>();
		candidatas.stream().sorted(Comparator.comparing(Candidata::familiaId)).forEach(c -> {
			double u = 1.0 - azar.nextDouble();
			claves.put(c.familiaId(), Math.log(u) / c.peso());
		});
		return claves;
	}
}
