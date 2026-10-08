package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 6, tanda 3 (decisión 77, P17): la muestra de la llamada de control es estable con la misma semilla, pondera a
 * las familias sin portal y con un solo apoderado, y aun así cualquier candidata puede salir. Correcciones del sprint 6:
 * sin plazas fijas (S6-B3), con una plaza para la deuda vencida (S6-A2) y con reemplazo (S6-M2).
 */
class MuestraLlamadasTest {

	private static FamiliaParaLlamada familia(long id, boolean conPortal, int apoderados) {
		return new FamiliaParaLlamada(id, "Familia " + id, apoderados, conPortal, List.of());
	}

	/** 3 sin portal y con un solo apoderado (2, 5, 8), 3 con una sola condición y 6 que usan el portal con dos apoderados. */
	private static List<FamiliaParaLlamada> candidatas() {
		return LongStream.rangeClosed(1, 12).mapToObj(id -> switch ((int) id) {
			case 2, 5, 8 -> familia(id, false, 1);
			case 3, 9 -> familia(id, false, 2);
			case 11 -> familia(id, true, 1);
			default -> familia(id, true, 2);
		}).toList();
	}

	@Test
	void laMismaSemillaDaLaMismaMuestra() {
		List<FamiliaParaLlamada> una = MuestraLlamadas.elegir(42L, candidatas(), 3);
		List<FamiliaParaLlamada> otra = MuestraLlamadas.elegir(42L, candidatas(), 3);
		assertThat(una).hasSize(3).isEqualTo(otra).doesNotHaveDuplicates();
	}

	/**
	 * Correcciones del sprint 6 (S6-B3, QA-S6-2): muestreo ponderado SIN plazas fijas. En 2000 semanas, las familias sin
	 * portal y con un solo apoderado (peso 3) salen más seguido que las que usan el portal con dos apoderados (peso 1),
	 * pero todas salen alguna vez y ninguna sale siempre: la cajera no puede saber a quién no llamarán.
	 */
	@Test
	void elMuestreoPonderadoNoTienePlazasFijas() {
		java.util.Map<Long, Integer> veces = new java.util.HashMap<>();
		int semanas = 2000;
		for (long semilla = 1; semilla <= semanas; semilla++) {
			List<FamiliaParaLlamada> muestra = MuestraLlamadas.elegir(semilla, candidatas(), 3);
			assertThat(muestra).hasSize(3).doesNotHaveDuplicates();
			muestra.forEach(f -> veces.merge(f.familiaId(), 1, Integer::sum));
		}
		assertThat(veces.keySet()).as("todas salen alguna vez").hasSize(12);
		assertThat(veces.values()).as("ninguna sale siempre").allMatch(v -> v < semanas);
		double prioritarias = (veces.get(2L) + veces.get(5L) + veces.get(8L)) / 3.0;
		double conPortal = (veces.get(1L) + veces.get(4L) + veces.get(6L) + veces.get(7L) + veces.get(10L)
				+ veces.get(12L)) / 6.0;
		assertThat(prioritarias).as("pesan más").isGreaterThan(conPortal * 1.5);
		assertThat(prioritarias).as("pero no tienen la plaza asegurada").isLessThan(semanas * 0.9);
	}

	/** S6-A2: con deuda vencida, una plaza es para una familia que debe (la de efectivo no registrado no deja pago). */
	@Test
	void unaPlazaEsParaLaDeudaVencida() {
		List<MuestraLlamadas.Candidata> conUnaDeudora = candidatas().stream().map(f -> new MuestraLlamadas.Candidata(f,
				f.familiaId() == 11L, false)).toList();
		for (long semilla = 1; semilla <= 300; semilla++) {
			List<MuestraLlamadas.Candidata> muestra = MuestraLlamadas.elegirCandidatas(semilla, conUnaDeudora, 3);
			assertThat(muestra).as("semilla " + semilla).hasSize(3);
			assertThat(muestra.getFirst().familiaId()).as("semilla " + semilla).isEqualTo(11L);
		}
	}

	/** S6-A2: la familia que pagaba en efectivo y dejó de pagar (con deuda) pesa más que una que solo debe. */
	@Test
	void dejarDePagarEnEfectivoPesaMas() {
		FamiliaParaLlamada f = familia(1, true, 2);
		assertThat(new MuestraLlamadas.Candidata(f, false, false).peso()).isEqualTo(1);
		assertThat(new MuestraLlamadas.Candidata(f, true, false).peso()).isEqualTo(2);
		assertThat(new MuestraLlamadas.Candidata(f, true, true).peso()).isEqualTo(4);
		assertThat(new MuestraLlamadas.Candidata(familia(2, false, 1), true, true).peso()).isEqualTo(6);
	}

	/** S6-M2: el reemplazo es una candidata que aún no está en la muestra (con la misma semilla, estable). */
	@Test
	void elReemplazoEsOtraFamiliaDeLasCandidatas() {
		List<MuestraLlamadas.Candidata> todas = candidatas().stream().map(f -> new MuestraLlamadas.Candidata(f, false,
				false)).toList();
		java.util.Set<Long> yaEstan = java.util.Set.of(1L, 2L, 3L);
		var reemplazo = MuestraLlamadas.reemplazo(9L, todas, yaEstan);
		assertThat(reemplazo).isPresent();
		assertThat(yaEstan).doesNotContain(reemplazo.get().familiaId());
		assertThat(MuestraLlamadas.reemplazo(9L, todas, yaEstan)).isEqualTo(reemplazo);
		java.util.Set<Long> todasYa = new java.util.HashSet<>();
		todas.forEach(c -> todasYa.add(c.familiaId()));
		assertThat(MuestraLlamadas.reemplazo(9L, todas, todasYa)).isEmpty();
	}

	@Test
	void conPocasCandidatasSalenTodasYSinCandidatasNinguna() {
		assertThat(MuestraLlamadas.elegir(7L, List.of(familia(1, true, 2), familia(2, false, 1)), 3))
				.extracting(FamiliaParaLlamada::familiaId).containsExactlyInAnyOrder(1L, 2L);
		assertThat(MuestraLlamadas.elegir(7L, List.of(), 3)).isEmpty();
	}

	@Test
	void laPrioridadCuentaPortalYApoderados() {
		assertThat(familia(1, false, 1).prioridad()).isEqualTo(2);
		assertThat(familia(1, false, 2).prioridad()).isEqualTo(1);
		assertThat(familia(1, true, 1).prioridad()).isEqualTo(1);
		assertThat(familia(1, true, 0).prioridad()).isEqualTo(1);
		assertThat(familia(1, true, 2).prioridad()).isZero();
	}
}
