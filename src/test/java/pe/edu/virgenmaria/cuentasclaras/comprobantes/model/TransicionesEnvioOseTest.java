package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: la máquina de estados del envío al OSE (sección 5): PENDIENTE → ENVIADO → ACEPTADO, OBSERVADO o
 * RECHAZADO; los finales no cambian; ENVIADO que el OSE no encuentra vuelve a PENDIENTE; el plazo legal. Puro.
 */
class TransicionesEnvioOseTest {

	private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);

	private static final LocalDateTime AHORA = LUNES.atTime(10, 0);

	private Comprobante boleta;

	@BeforeEach
	void emitir() {
		SerieComprobante serie = SerieComprobante.nueva(TipoComprobante.BOLETA, "B001", ProveedorComprobantes.SIMULADO);
		boleta = Comprobante.emitir(serie, serie.siguiente(), LUNES, Receptor.de(DocumentoReceptor.DNI, "45678912",
				"Rosa Quispe"), AfectacionIgv.INAFECTO, List.of(new LineaDocumento("Pensión marzo", new BigDecimal("450.00"))));
	}

	private static ResultadoEnvio resultado(EstadoEnvio estado) {
		return new ResultadoEnvio(estado, "Respuesta " + estado, "HASH123", null, "0", null);
	}

	@Test
	void debeNacerPendienteSinIntentos() {
		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.PENDIENTE);
		assertThat(boleta.getIntentos()).isZero();
	}

	@Test
	void debePasarDePendienteAEnviadoYLuegoAAceptado() {
		boleta.registrarEnvio(resultado(EstadoEnvio.ENVIADO), AHORA, AHORA.plusMinutes(5));
		boleta.registrarEnvio(resultado(EstadoEnvio.ACEPTADO), AHORA.plusMinutes(5), null);

		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.ACEPTADO);
		assertThat(boleta.getIntentos()).isEqualTo(2);
	}

	@Test
	void debeRechazarCualquierCambioDeUnComprobanteAceptado() {
		boleta.registrarEnvio(resultado(EstadoEnvio.ACEPTADO), AHORA, null);

		assertThatThrownBy(() -> boleta.registrarEnvio(resultado(EstadoEnvio.RECHAZADO), AHORA, null))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> boleta.registrarFalloEnvio("x", "y", AHORA, AHORA))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> boleta.adelantarReintento(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.ACEPTADO);
	}

	@Test
	void debeRechazarCambiosDeUnComprobanteRechazado() {
		boleta.registrarEnvio(new ResultadoEnvio(EstadoEnvio.RECHAZADO, null, null, null, "2800", null), AHORA, null);

		assertThatThrownBy(() -> boleta.registrarEnvio(resultado(EstadoEnvio.ACEPTADO), AHORA, null))
				.isInstanceOf(IllegalStateException.class);
		assertThat(boleta.getRespuesta()).isEqualTo("Rechazado por el OSE");
	}

	@Test
	void debeExigirElHashParaAceptar() {
		assertThatThrownBy(() -> boleta.registrarEnvio(new ResultadoEnvio(EstadoEnvio.ACEPTADO, "ok", null, null), AHORA,
				null)).isInstanceOf(IllegalStateException.class);
		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.PENDIENTE);
	}

	@Test
	void debeRechazarRegistrarComoEnvioUnaRespuestaPendiente() {
		assertThatThrownBy(() -> boleta.registrarEnvio(resultado(EstadoEnvio.PENDIENTE), AHORA, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void debeVolverAPendienteSoloDesdeEnviado() {
		assertThatThrownBy(() -> boleta.volverAPendiente("no lo encontró", AHORA))
				.isInstanceOf(IllegalStateException.class);
		boleta.registrarEnvio(resultado(EstadoEnvio.ENVIADO), AHORA, AHORA.plusMinutes(5));

		boleta.volverAPendiente("no lo encontró", AHORA.plusMinutes(5));

		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.PENDIENTE);
	}

	@Test
	void debeConservarLaFechaDeEnvioAlConsultarUnEnviado() {
		boleta.registrarEnvio(resultado(EstadoEnvio.ENVIADO), AHORA, AHORA.plusMinutes(5));
		boleta.registrarEnvio(resultado(EstadoEnvio.ACEPTADO), AHORA.plusHours(2), null);

		assertThat(boleta.getEnviadoEn()).isEqualTo(AHORA);
	}

	@Test
	void debeSumarUnIntentoPorCadaFalloYProgramarElSiguiente() {
		boleta.registrarFalloEnvio("Sin respuesta", "timeout", AHORA, AHORA.plusMinutes(1));
		boleta.registrarFalloEnvio("Sin respuesta", "timeout", AHORA.plusMinutes(1), AHORA.plusMinutes(3));

		assertThat(boleta.getIntentos()).isEqualTo(2);
		assertThat(boleta.getProximoIntentoEn()).isEqualTo(AHORA.plusMinutes(3));
		assertThat(boleta.getEstadoEnvio()).isEqualTo(EstadoEnvio.PENDIENTE);
	}

	@Test
	void debeAvisarDelPlazoLegalUnDiaAntesDeVencer() {
		// Emitida el lunes 05/10 con plazo de 3 días: límite el jueves 08/10; aviso desde el miércoles 07/10.
		assertThat(boleta.fechaLimiteEnvio(3)).isEqualTo(LocalDate.of(2026, 10, 8));
		assertThat(boleta.vencePlazo(LocalDate.of(2026, 10, 6), 3)).isFalse();
		assertThat(boleta.vencePlazo(LocalDate.of(2026, 10, 7), 3)).isTrue();
		assertThat(boleta.vencePlazo(LocalDate.of(2026, 10, 9), 3)).isTrue();
	}

	@Test
	void debeNoAvisarDelPlazoDeUnComprobanteYaResuelto() {
		boleta.registrarEnvio(resultado(EstadoEnvio.OBSERVADO), AHORA, null);

		assertThat(boleta.vencePlazo(LocalDate.of(2026, 10, 30), 3)).isFalse();
	}
}
