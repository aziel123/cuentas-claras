package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA sprint 5 (tanda 3, G21): la muestra diaria de verificaciones que ve Promotoría. Dada la semilla secreta del día,
 * cuando se elige la muestra, entonces es estable durante el día, no cambia la lista y no se deduce de la fecha.
 */
class MuestraDelDiaQaTest {

	private static final List<Integer> VERIFICACIONES = IntStream.rangeClosed(1, 20).boxed().toList();

	@Test
	void laMismaSemillaDaLaMismaMuestraTodoElDia() {
		long semilla = 8_675_309_123L;

		assertThat(MuestraAlAzar.delDia(semilla, VERIFICACIONES, 3))
				.isEqualTo(MuestraAlAzar.delDia(semilla, VERIFICACIONES, 3)).hasSize(3).doesNotHaveDuplicates();
	}

	@Test
	void noCambiaLaListaOriginalNiPideMasDeLosQueHay() {
		List<Integer> original = new ArrayList<>(VERIFICACIONES);

		assertThat(MuestraAlAzar.delDia(1L, original, 50)).hasSize(20).containsExactlyInAnyOrderElementsOf(VERIFICACIONES);
		assertThat(MuestraAlAzar.delDia(1L, original, -2)).isEmpty();
		assertThat(MuestraAlAzar.delDia(1L, List.<Integer>of(), 3)).isEmpty();
		assertThat(original).containsExactlyElementsOf(VERIFICACIONES);
	}

	@Test
	void laCajeraNoPuedeCalcularLaMuestraConLaFecha() {
		LocalDate hoy = LocalDate.of(2026, 10, 7);
		// Lo que hacía el código del sprint 4: Random(fecha).
		List<Integer> predicha = new ArrayList<>(VERIFICACIONES);
		Collections.shuffle(predicha, new Random(hoy.toEpochDay()));

		// Con 20 semillas secretas distintas, a lo más una coincidencia casual con la predicción.
		long coincidencias = IntStream.range(0, 20).mapToObj(i -> MuestraAlAzar.semilla())
				.filter(s -> MuestraAlAzar.delDia(s, VERIFICACIONES, 3).equals(predicha.subList(0, 3))).count();
		assertThat(coincidencias).isLessThanOrEqualTo(1);
	}

	@Test
	void lasSemillasSecretasNoSeRepiten() {
		assertThat(IntStream.range(0, 1000).mapToObj(i -> MuestraAlAzar.semilla()).distinct().count()).isEqualTo(1000);
	}
}
