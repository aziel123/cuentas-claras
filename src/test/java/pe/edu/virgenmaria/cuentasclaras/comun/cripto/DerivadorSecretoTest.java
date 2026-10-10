package pe.edu.virgenmaria.cuentasclaras.comun.cripto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 7, tanda 2 (H8): la semilla efectiva del muestreo sale del aleatorio guardado y de la clave del servidor. Quien
 * lee {@code semilla_muestreo} con SELECT (cualquier conexión de la base) no puede calcular la muestra sin la clave.
 */
class DerivadorSecretoTest {

	private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);

	@Test
	void esEstableYDependeDeTodo() {
		DerivadorSecreto derivador = new DerivadorSecreto("una clave del servidor de prueba");
		long semilla = derivador.semilla("CAJA", LUNES, 42L);

		assertThat(derivador.semilla("CAJA", LUNES, 42L)).isEqualTo(semilla);
		assertThat(semilla).isNotEqualTo(42L)
				.isNotEqualTo(derivador.semilla("CAJA", LUNES, 43L))
				.isNotEqualTo(derivador.semilla("LLAMADAS", LUNES, 42L))
				.isNotEqualTo(derivador.semilla("CAJA", LUNES.plusDays(1), 42L))
				.isNotEqualTo(new DerivadorSecreto("otra clave del servidor").semilla("CAJA", LUNES, 42L));
	}

	@Test
	void sinClaveNoArranca() {
		assertThatThrownBy(() -> new DerivadorSecreto(" ")).isInstanceOf(IllegalStateException.class);
	}
}
