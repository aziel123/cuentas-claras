package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GeneradorClaveTemporalTest {

	private final GeneradorClaveTemporal generador = new GeneradorClaveTemporal();

	@Test
	void generaDoceCaracteresSinCaracteresAmbiguos() {
		for (int i = 0; i < 200; i++) {
			String clave = generador.generar();
			assertThat(clave).hasSize(12).matches("[" + GeneradorClaveTemporal.ALFABETO + "]+")
					.doesNotContain("0", "O", "1", "l", "I");
		}
	}

	@Test
	void cumpleLaPoliticaDeClaves() {
		assertThatCode(() -> PoliticaClaves.validar(generador.generar(), "caja")).doesNotThrowAnyException();
	}

	@Test
	void noSeRepite() {
		Set<String> claves = new HashSet<>();
		IntStream.range(0, 1000).forEach(i -> claves.add(generador.generar()));
		assertThat(claves).hasSize(1000);
	}
}
