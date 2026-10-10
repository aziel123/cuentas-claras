package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 7 (QA-S7-2): una pasada por los colegios intenta en todos, pero si alguno falla queda marcada y
 * la tarea programada no deja latido (lo comprueba {@code MonitoreoBordesTest} con {@link ObservadorLatidos}).
 */
class PasadaPorColegiosTest {

	private static final Logger LOG = LoggerFactory.getLogger(PasadaPorColegiosTest.class);

	@AfterEach
	void limpiar() {
		PasadaPorColegios.reiniciar();
	}

	@Test
	void siFallaEnTodosLosColegiosQuedaMarcada() {
		List<Long> intentados = new ArrayList<>();

		int fallas = PasadaPorColegios.recorrer(LOG, "La huella de la hora", List.of(1L, 2L), c -> {
			intentados.add(c);
			throw new IllegalStateException("1142: sin permiso");
		});

		assertThat(fallas).isEqualTo(2);
		assertThat(intentados).containsExactly(1L, 2L);
		assertThat(PasadaPorColegios.huboFallas()).isTrue();
	}

	@Test
	void siFallaEnUnColegioIgualIntentaLosDemasYQuedaMarcada() {
		List<Long> hechos = new ArrayList<>();

		PasadaPorColegios.recorrer(LOG, "El envío de mensajes", List.of(1L, 2L, 3L), c -> {
			if (c == 2L) {
				throw new IllegalStateException("caído");
			}
			hechos.add(c);
		});

		assertThat(hechos).containsExactly(1L, 3L);
		assertThat(PasadaPorColegios.huboFallas()).isTrue();
	}

	@Test
	void siTerminaBienEnTodosNoQuedaMarcada() {
		assertThat(PasadaPorColegios.recorrer(LOG, "El resumen diario", List.of(1L, 2L), c -> {
		})).isZero();
		assertThat(PasadaPorColegios.recorrer(LOG, "El resumen diario", List.of(), c -> {
			throw new IllegalStateException("no se llama");
		})).isZero();
		assertThat(PasadaPorColegios.huboFallas()).isFalse();
	}
}
