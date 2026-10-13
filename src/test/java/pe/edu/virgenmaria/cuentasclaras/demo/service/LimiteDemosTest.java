package pe.edu.virgenmaria.cuentasclaras.demo.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 8, tanda 3 (decisión 126, E23): 3 demos por conexión al día y 50 en total; al día siguiente, de nuevo. */
class LimiteDemosTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Test
	void tresPorConexionAlDia() {
		LimiteDemos limite = new LimiteDemos(3, 6, 50, Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), LIMA));
		for (int i = 0; i < 3; i++) {
			limite.reservar("200.1.2.3");
		}
		assertThatThrownBy(() -> limite.reservar("200.1.2.3")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Desde esta conexión ya creaste 3 demos hoy");
		assertThatCode(() -> limite.reservar("200.1.2.4")).as("otra conexión").doesNotThrowAnyException();
	}

	@Test
	void cincuentaEnTotalAlDia() {
		LimiteDemos limite = new LimiteDemos(3, 6, 50, Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), LIMA));
		for (int i = 0; i < 50; i++) {
			limite.reservar("10.0." + i / 3 + "." + i % 3);
		}
		assertThatThrownBy(() -> limite.reservar("181.65.1.1")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("todas las demos disponibles (50)");
	}

	@Test
	void unaCreacionFallidaNoCuentaYAlDiaSiguienteSeReinicia() {
		Clock hoy = Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), LIMA);
		LimiteDemos limite = new LimiteDemos(1, 6, 50, hoy);
		limite.reservar("200.1.2.3");
		limite.devolver("200.1.2.3");
		assertThatCode(() -> limite.reservar("200.1.2.3")).doesNotThrowAnyException();
		assertThatThrownBy(() -> limite.reservar("200.1.2.3")).isInstanceOf(ReglaNegocioException.class);

		// Las 23:30 de Lima del 12 son ya el 13 en UTC: el día que cuenta es el de Lima.
		LimiteDemos tarde = new LimiteDemos(1, 6, 50, Clock.fixed(Instant.parse("2026-10-13T04:30:00Z"), LIMA));
		tarde.reservar("200.1.2.3");
		assertThatThrownBy(() -> tarde.reservar("200.1.2.3")).isInstanceOf(ReglaNegocioException.class);
	}

	/**
	 * S8-B3: con 3 por conexión y 50 al día, 17 direcciones de una misma red agotaban la demo de todos. Ahora una red (/24 o
	 * /48) crea a lo más 6 al día y el total por defecto es 120: 17 IP de un mismo proveedor ya no la agotan.
	 */
	@Test
	void diecisieteIpDeUnaMismaRedNoAgotanLaDemo() {
		LimiteDemos limite = new LimiteDemos(3, 6, 120, Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), LIMA));
		int creadas = 0;
		for (int ip = 1; ip <= 17; ip++) {
			for (int i = 0; i < 3; i++) {
				try {
					limite.reservar("200.1.2." + ip);
					creadas++;
				}
				catch (ReglaNegocioException e) {
					assertThat(e.getMessage()).containsAnyOf("Desde tu red", "Desde esta conexión");
				}
			}
		}
		assertThat(creadas).isEqualTo(6);
		assertThatCode(() -> limite.reservar("181.65.1.1")).as("otro visitante").doesNotThrowAnyException();
		assertThat(LimiteDemos.red("2001:db8:1:2::5")).isEqualTo("2001:db8:1");
	}
}
