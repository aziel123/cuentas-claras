package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Correcciones del sprint 4 (S4-B4): el webhook limita los avisos por IP. Puro. */
class LimiteAvisosPorIpTest {

	@Test
	void cadaIpTieneSuLimitePorMinuto() {
		RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-06T14:00:00Z"), ZoneId.of("America/Lima"));
		LimiteAvisosPorIp limite = new LimiteAvisosPorIp(3, reloj);

		assertThat(limite.permitir("203.0.113.7")).isTrue();
		assertThat(limite.permitir("203.0.113.7")).isTrue();
		assertThat(limite.permitir("203.0.113.7")).isTrue();
		assertThat(limite.permitir("203.0.113.7")).isFalse();
		// Otra IP no se ve afectada.
		assertThat(limite.permitir("198.51.100.1")).isTrue();
		// Pasado el minuto, vuelve a poder.
		reloj.avanzar(Duration.ofSeconds(61));
		assertThat(limite.permitir("203.0.113.7")).isTrue();
	}

	@Test
	void unLimiteImposibleNoArranca() {
		RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-06T14:00:00Z"), ZoneId.of("America/Lima"));
		assertThatThrownBy(() -> new LimiteAvisosPorIp(0, reloj)).isInstanceOf(IllegalArgumentException.class);
	}
}
