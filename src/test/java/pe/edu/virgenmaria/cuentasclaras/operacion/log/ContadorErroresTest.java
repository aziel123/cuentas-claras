package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 7 (registro de errores): agrupa por huella sin guardar el mensaje, cuenta la ventana y se reinicia cada día. */
class ContadorErroresTest {

	private final RelojAjustable reloj = new RelojAjustable(Instant.parse("2026-10-08T15:00:00Z"),
			ConfiguracionTiempo.ZONA_LIMA);

	private final ContadorErrores contador = new ContadorErrores(reloj);

	@AfterEach
	void soltar() {
		contador.destroy();
	}

	@Test
	void laHuellaEsLaClaseYElPrimerMarcoPropioNuncaElMensaje() {
		LoggerContext contexto = new LoggerContext();
		LoggingEvent evento = new LoggingEvent("x", contexto.getLogger("prueba"), Level.ERROR,
				"No se pudo cobrar a la familia del DNI 78451236", new IllegalStateException("DNI 78451236"), null);

		String huella = ContadorErrores.huella(evento);

		assertThat(huella).isEqualTo("java.lang.IllegalStateException en ContadorErroresTest."
				+ "laHuellaEsLaClaseYElPrimerMarcoPropioNuncaElMensaje").doesNotContain("78451236");
		assertThat(ContadorErrores.huella(new LoggingEvent("x", contexto.getLogger("caja.Servicio"), Level.ERROR,
				"texto con datos", null, null))).isEqualTo("ERROR en caja.Servicio");
	}

	@Test
	void cuentaLosErroresDeHoyYLosDeLaVentana() {
		contador.registrar("A", "id1");
		reloj.avanzar(Duration.ofMinutes(20));
		contador.registrar("A", "id2");
		contador.registrar("B", null);

		assertThat(contador.deHoy()).hasSize(2);
		assertThat(contador.deHoy().stream().filter(e -> e.huella().equals("A")).findFirst().orElseThrow())
				.satisfies(e -> {
					assertThat(e.hoy()).isEqualTo(2);
					assertThat(e.ultimoIdPeticion()).isEqualTo("id2");
				});
		assertThat(contador.enVentana("A", Duration.ofMinutes(15))).isEqualTo(1);

		reloj.avanzar(Duration.ofDays(1));
		assertThat(contador.deHoy()).isEmpty();
	}

	@Test
	void colgadoDelLoggerRaizCuentaLosErroresReales() {
		contador.afterPropertiesSet();
		LoggerFactory.getLogger("pe.edu.virgenmaria.prueba").error("falló con datos 912345678",
				new IllegalArgumentException("x"));
		LoggerFactory.getLogger("pe.edu.virgenmaria.prueba").warn("solo un aviso");

		assertThat(contador.deHoy()).singleElement().satisfies(
				e -> assertThat(e.huella()).startsWith("java.lang.IllegalArgumentException en ContadorErroresTest."));
	}
}
