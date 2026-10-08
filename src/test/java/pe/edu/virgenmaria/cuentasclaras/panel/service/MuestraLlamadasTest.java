package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 6, tanda 3 (decisión 77, P17): la muestra de la llamada de control es estable con la misma semilla, prioriza a
 * las familias sin portal y con un solo apoderado, y aun así cualquier familia que paga en efectivo puede salir.
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

	@Test
	void dosPlazasVanALasDeMayorPrioridadYLaUltimaEsAlAzarEntreTodas() {
		Set<Long> ultimas = new HashSet<>();
		for (long semilla = 1; semilla <= 300; semilla++) {
			List<FamiliaParaLlamada> muestra = MuestraLlamadas.elegir(semilla, candidatas(), 3);
			assertThat(muestra).hasSize(3);
			assertThat(muestra.subList(0, 2)).as("semilla " + semilla).allMatch(f -> f.prioridad() == 2);
			ultimas.add(muestra.get(2).familiaId());
		}
		assertThat(ultimas).as("la plaza abierta llega también a familias que usan el portal")
				.contains(1L, 4L, 6L, 7L, 10L, 12L);
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
