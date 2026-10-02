package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaClavesTest {

	@Test
	void rechazaClaveDe9Caracteres() {
		assertThatThrownBy(() -> PoliticaClaves.validar("abcdefghi", "caja"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos 10");
		assertThatCode(() -> PoliticaClaves.validar("pato azul7", "caja")).doesNotThrowAnyException();
	}

	@Test
	void rechazaClaveDeMasDe64Caracteres() {
		assertThatThrownBy(() -> PoliticaClaves.validar("a".repeat(65), "caja"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("64");
	}

	@Test
	void rechazaClaveDeMasDe72Bytes() {
		String conEñes = "ñ".repeat(37);
		assertThat(conEñes.length()).isLessThanOrEqualTo(64);
		assertThat(conEñes.getBytes(StandardCharsets.UTF_8)).hasSize(74);
		assertThatThrownBy(() -> PoliticaClaves.validar(conEñes, "caja"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("demasiado larga");
	}

	@Test
	void rechazaClaveConElNombreDeUsuario() {
		assertThatThrownBy(() -> PoliticaClaves.validar("soy Lucia.Ramos 2026", "lucia.ramos"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("nombre de usuario");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234567890", "Contraseña123", "qwertyuiop", "Virgen Maria", "cuentasclaras" })
	void rechazaClavesComunes(String comun) {
		assertThatThrownBy(() -> PoliticaClaves.validar(comun, "caja"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("muy común");
	}

	@Test
	void aceptaFraseLarga() {
		assertThatCode(() -> PoliticaClaves.validar("mi gato duerme al sol de la tarde", "caja"))
				.doesNotThrowAnyException();
		assertThatCode(() -> PoliticaClaves.validar("año nuevo en Huancayo", "caja")).doesNotThrowAnyException();
	}
}
