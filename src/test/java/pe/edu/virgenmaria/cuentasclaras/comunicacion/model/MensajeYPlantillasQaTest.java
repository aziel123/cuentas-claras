package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA sprint 5 (tanda 1): la máquina de estados del mensaje, la espera creciente de los reintentos y las plantillas.
 * Dado un mensaje del outbox, cuando el proveedor o el despacho lo mueven, entonces nunca vuelve atrás ni se reenvía.
 */
class MensajeYPlantillasQaTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 7, 10, 0);

	private static Mensaje pendiente() {
		return Mensaje.nuevo("PAGO_REGISTRADO:pago:1:APODERADO:3:WHATSAPP", TipoMensaje.PAGO_REGISTRADO,
				PlantillaMensaje.PAGO_REGISTRADO, new Mensaje.Destinatario(DestinatarioTipo.APODERADO, 3L, 7L, null,
						CanalMensaje.WHATSAPP, "+51987654321"),
				List.of("S/ 450.00", "Pensión de marzo de Mateo", "B001-00000001", "02/10/2026 10:00", "Efectivo",
						"Lucía R."), "pago", 1L, null, null);
	}

	@Test
	void unMensajeLeidoNoRetrocedeAEntregadoNiFalla() {
		Mensaje m = pendiente();
		m.marcarEnviado(ProveedorMensajeria.WHATSAPP_CLOUD, "wamid.1", AHORA);
		assertThat(m.leido(AHORA.plusMinutes(1))).isTrue();

		assertThat(m.entregado(AHORA.plusMinutes(2))).isFalse();
		assertThat(m.getEstado()).isEqualTo(EstadoMensaje.LEIDO);
		assertThatThrownBy(() -> m.fallar("tarde", false)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unMensajeFallidoNoSeVuelveAEnviarNiSePospone() {
		Mensaje m = pendiente();
		m.fallar("Número inválido", true);

		assertThatThrownBy(() -> m.marcarEnviado(ProveedorMensajeria.WHATSAPP_CLOUD, "wamid.2", AHORA))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> m.reintentarEn("red", AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> m.posponerHasta(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> m.adelantar(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThat(m.getIntentos()).isEqualTo(1);
	}

	@Test
	void posponerUnRecordatorioNoCuentaComoIntento() {
		Mensaje m = pendiente();
		m.posponerHasta(AHORA.plusHours(10));

		assertThat(m.getIntentos()).isZero();
		assertThat(m.getProximoIntentoEn()).isEqualTo(AHORA.plusHours(10));
		assertThat(m.getEstado()).isEqualTo(EstadoMensaje.PENDIENTE);
	}

	@Test
	void unErrorDeRedSumaUnIntentoYGuardaElErrorRecortado() {
		Mensaje m = pendiente();
		m.reintentarEn("x".repeat(400), AHORA.plusMinutes(1));

		assertThat(m.getIntentos()).isEqualTo(1);
		assertThat(m.getUltimoError()).hasSize(250);
		assertThat(m.getEstado()).isEqualTo(EstadoMensaje.PENDIENTE);
	}

	@Test
	void unParametroConSaltoDeLineaNoCreaUnParametroExtra() {
		Mensaje m = Mensaje.nuevo("c", TipoMensaje.PAGO_ANULADO, PlantillaMensaje.PAGO_ANULADO,
				new Mensaje.Destinatario(DestinatarioTipo.APODERADO, 3L, 7L, null, CanalMensaje.CORREO, "a@b.pe"),
				List.of("B001-1", "S/ 10.00", "Efectivo", "línea 1\nlínea 2\r\nlínea 3", "Dirección"), "pago", 1L, null,
				null);

		assertThat(m.parametrosLista()).hasSize(5);
		assertThat(PlantillaMensaje.PAGO_ANULADO.componer(m.parametrosLista())).contains("línea 1 línea 2");
	}

	@Test
	void laEsperaEntreReintentosCreceDe1a60Minutos() {
		PropiedadesMensajeria p = propiedades();

		assertThat(p.esperaTrasIntentos(1)).isEqualTo(Duration.ofMinutes(1));
		assertThat(p.esperaTrasIntentos(2)).isEqualTo(Duration.ofMinutes(2));
		assertThat(p.esperaTrasIntentos(3)).isEqualTo(Duration.ofMinutes(4));
		assertThat(p.esperaTrasIntentos(6)).isEqualTo(Duration.ofMinutes(32));
		assertThat(p.esperaTrasIntentos(7)).isEqualTo(Duration.ofMinutes(60));
		assertThat(p.esperaTrasIntentos(500)).isEqualTo(Duration.ofMinutes(60));
	}

	@Test
	void laVigenciaDelEnlaceNoPasaDe71Horas() {
		assertThatThrownBy(() -> new PropiedadesMensajeria(whatsapp(), correo(), Duration.ofSeconds(30),
				Duration.ofMinutes(1), Duration.ofMinutes(60), 8, 15, 60, 12, "http://localhost", Duration.ofHours(72)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * Dado que el motivo de una anulación lo escribe una persona, cuando contiene un marcador como «{{5}}», entonces
	 * el texto que recibe la familia debe mostrarlo tal cual, sin reemplazarlo por otro parámetro.
	 */
	@Test
	void unMarcadorDentroDelMotivoNoSeReemplaza() {
		String texto = PlantillaMensaje.PAGO_ANULADO.componer(List.of("B001-00000007", "S/ 450.00", "Efectivo",
				"error de digitación {{5}}", "Dirección"));

		assertThat(texto).contains("Motivo: error de digitación {{5}}.");
	}

	private static PropiedadesMensajeria propiedades() {
		return new PropiedadesMensajeria(whatsapp(), correo(), Duration.ofSeconds(30), Duration.ofMinutes(1),
				Duration.ofMinutes(60), 8, 15, 60, 12, "http://localhost", Duration.ofHours(48));
	}

	private static PropiedadesMensajeria.Whatsapp whatsapp() {
		return new PropiedadesMensajeria.Whatsapp("SIMULADO", "https://graph.facebook.com", "v21.0", "", "", "", "",
				"graph.facebook.com", false, "", "es");
	}

	private static PropiedadesMensajeria.Correo correo() {
		return new PropiedadesMensajeria.Correo("SIMULADO", "", false, "");
	}
}
