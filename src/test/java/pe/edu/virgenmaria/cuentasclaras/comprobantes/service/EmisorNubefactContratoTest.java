package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contrato con Nubefact (sin red): lo que se envía (JSON, token, ruta de la lista cerrada) y cómo se interpreta cada
 * respuesta. Aceptado sin hash no cuenta como aceptado; 429 y 5xx se reintentan; «ya existe» se consulta.
 */
class EmisorNubefactContratoTest {

	private static final String RUTA = "https://api.nubefact.com/api/v1/abc-123";

	private MockRestServiceServer servidor;

	private EmisorNubefact emisor;

	@BeforeEach
	void preparar() {
		RestClient.Builder constructor = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(constructor).build();
		emisor = new EmisorNubefact(propiedades(RUTA, "token-secreto"), constructor);
	}

	private static PropiedadesComprobantes propiedades(String ruta, String token) {
		return new PropiedadesComprobantes(ProveedorComprobantes.NUBEFACT, AfectacionIgv.INAFECTO, "B001", "F001", "BC01",
				"FC01", true, new PropiedadesComprobantes.Nubefact(ruta, token, "api.nubefact.com", Duration.ofSeconds(5),
						Duration.ofSeconds(20)), 3, 4, Duration.ofMinutes(1), Duration.ofMinutes(60), Duration.ofMinutes(5));
	}

	private static DocumentoElectronico boleta() {
		return new DocumentoElectronico(TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 10, 2),
				Receptor.de(DocumentoReceptor.DNI, "45678912", "ROSA HUAMAN CCORI"), "PEN", new BigDecimal("450.00"),
				AfectacionIgv.INAFECTO, List.of(new LineaDocumento("Pensión marzo 2027 - Mateo", new BigDecimal("450.00"))),
				null);
	}

	@Test
	void enviaElJsonEsperadoConElTokenYLeeUnAceptado() {
		servidor.expect(requestTo(RUTA)).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Token token=\"token-secreto\""))
				.andExpect(jsonPath("$.operacion").value("generar_comprobante"))
				.andExpect(jsonPath("$.tipo_de_comprobante").value(2))
				.andExpect(jsonPath("$.serie").value("B001"))
				.andExpect(jsonPath("$.numero").value(7))
				.andExpect(jsonPath("$.cliente_tipo_de_documento").value("1"))
				.andExpect(jsonPath("$.cliente_numero_de_documento").value("45678912"))
				.andExpect(jsonPath("$.fecha_de_emision").value("02-10-2026"))
				.andExpect(jsonPath("$.total_inafecta").value(450.00))
				.andExpect(jsonPath("$.total").value(450.00))
				.andExpect(jsonPath("$.items[0].tipo_de_igv").value(9))
				.andRespond(withSuccess("""
						{"aceptada_por_sunat": true, "sunat_description": "La Boleta B001-7 ha sido aceptada",
						 "sunat_responsecode": "0", "codigo_hash": "abc=", "enlace_del_pdf": "https://pdf"}""",
						MediaType.APPLICATION_JSON));

		ResultadoEnvio r = emisor.enviar(boleta());

		assertThat(r.estado()).isEqualTo(EstadoEnvio.ACEPTADO);
		assertThat(r.codigoHash()).isEqualTo("abc=");
		assertThat(r.codigoRespuesta()).isEqualTo("0");
		servidor.verify();
	}

	@Test
	void interpretaRechazoObservacionYPendiente() {
		assertThat(EmisorNubefact.interpretar(java.util.Map.of("aceptada_por_sunat", false, "sunat_responsecode", "2017",
				"sunat_description", "DNI no válido")).estado()).isEqualTo(EstadoEnvio.RECHAZADO);
		assertThat(EmisorNubefact.interpretar(java.util.Map.of("aceptada_por_sunat", true, "sunat_responsecode", "4252",
				"codigo_hash", "h")).estado()).isEqualTo(EstadoEnvio.OBSERVADO);
		assertThat(EmisorNubefact.interpretar(java.util.Map.of("aceptada_por_sunat", false)).estado())
				.isEqualTo(EstadoEnvio.ENVIADO);
	}

	@Test
	void yaExisteSeConsultaEnVezDeDuplicar() {
		servidor.expect(requestTo(RUTA)).andExpect(jsonPath("$.operacion").value("generar_comprobante"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"errors\": \"Este documento ya existe\", \"codigo\": 23}"));
		servidor.expect(requestTo(RUTA)).andExpect(jsonPath("$.operacion").value("consultar_comprobante"))
				.andExpect(jsonPath("$.numero").value(7))
				.andRespond(withSuccess("{\"aceptada_por_sunat\": true, \"sunat_responsecode\": \"0\", "
						+ "\"codigo_hash\": \"h\"}", MediaType.APPLICATION_JSON));

		assertThat(emisor.enviar(boleta()).estado()).isEqualTo(EstadoEnvio.ACEPTADO);
		servidor.verify();
	}

	@Test
	void saturacionOErrorDelServidorSeReintentan() {
		servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> emisor.enviar(boleta())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("429");
		servidor.reset();
		servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		assertThatThrownBy(() -> emisor.enviar(boleta())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("502");
	}

	@Test
	void unaConsultaDeAlgoQueElOseNoTieneVuelveAPendiente() {
		servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"errors\": \"No existe\", \"codigo\": 24}"));

		assertThat(emisor.consultar(TipoComprobante.BOLETA, "B001", 7).estado()).isEqualTo(EstadoEnvio.PENDIENTE);
	}

	@Test
	void sinRutaDeLaListaCerradaOSinTokenNoSeCrea() {
		assertThatThrownBy(() -> new EmisorNubefact(propiedades("https://evil.pe/api", "t"), RestClient.builder()))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new EmisorNubefact(propiedades("http://api.nubefact.com/api", "t"), RestClient.builder()))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new EmisorNubefact(propiedades(RUTA, " "), RestClient.builder()))
				.isInstanceOf(IllegalStateException.class);
	}
}
