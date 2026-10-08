package pe.edu.virgenmaria.cuentasclaras.comprobantes.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: la espera creciente del outbox del OSE (1, 2, 4… hasta 60 minutos). Mata las mutaciones del
 * exponente y del tope que sobrevivían a {@code OutboxComprobantesTest}. Puro.
 */
class EsperaReintentosOseTest {

	private final PropiedadesComprobantes propiedades = new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO,
			AfectacionIgv.INAFECTO, "B001", "F001", "BC01", "FC01");

	@ParameterizedTest
	@CsvSource({ "0, 1", "1, 1", "2, 2", "3, 4", "4, 8", "5, 16", "6, 32", "7, 60", "8, 60", "30, 60", "1000, 60" })
	void debeDuplicarLaEsperaHastaElTopeDeUnaHora(int intentos, long minutos) {
		assertThat(propiedades.esperaTrasIntentos(intentos)).isEqualTo(Duration.ofMinutes(minutos));
	}

	@Test
	void debeRespetarUnTopeIgualAlReintentoInicial() {
		PropiedadesComprobantes plano = new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO, AfectacionIgv.INAFECTO,
				"B001", "F001", "BC01", "FC01", false, null, 3, 4, Duration.ofMinutes(5), Duration.ofMinutes(5),
				Duration.ofMinutes(5));

		assertThat(plano.esperaTrasIntentos(1)).isEqualTo(Duration.ofMinutes(5));
		assertThat(plano.esperaTrasIntentos(9)).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void debeImpedirArrancarConUnTopeMenorQueElReintentoInicial() {
		assertThatThrownBy(() -> new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO, AfectacionIgv.INAFECTO,
				"B001", "F001", "BC01", "FC01", false, null, 3, 4, Duration.ofMinutes(10), Duration.ofMinutes(5),
				Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void debeImpedirArrancarConUnPlazoLegalFueraDeUnoASieteDias() {
		assertThatThrownBy(() -> new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO, AfectacionIgv.INAFECTO,
				"B001", "F001", "BC01", "FC01", false, null, 8, 4, Duration.ofMinutes(1), Duration.ofMinutes(60),
				Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO, AfectacionIgv.INAFECTO,
				"B001", "F001", "BC01", "FC01", false, null, 0, 4, Duration.ofMinutes(1), Duration.ofMinutes(60),
				Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
	}
}
