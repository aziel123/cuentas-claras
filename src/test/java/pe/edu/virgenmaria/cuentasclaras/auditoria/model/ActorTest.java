package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ActorTest {

	@ParameterizedTest
	@ValueSource(strings = { "190.40.1.2", "127.0.0.1", "0.0.0.0", "255.255.255.255", "::1", "0:0:0:0:0:0:0:1",
			"2001:db8::8a2e:370:7334", "fe80::1%eth0", "::ffff:192.168.1.10", "2001:0db8:85a3:0000:0000:8a2e:0370:7334" })
	void guardaLasIpValidas(String ip) {
		assertThat(new Actor(1L, 1L, "caja", null, ip).ip()).isEqualTo(ip);
	}

	@ParameterizedTest
	@ValueSource(strings = { "256.1.1.1", "1.2.3", "01.2.3.4x", "evil.example.com", "<script>", "1.2.3.4, 5.6.7.8",
			"2001:db8:::1", "1::2::3", "12345::1", "1:2:3:4:5:6:7:8:9", "' OR 1=1 --" })
	void reemplazaLoQueNoEsUnaIp(String texto) {
		assertThat(new Actor(1L, 1L, "caja", null, texto).ip()).isEqualTo(Actor.IP_NO_VALIDA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void sinIpQuedaVacia(String texto) {
		assertThat(new Actor(1L, 1L, "caja", null, texto).ip()).isNull();
	}
}
