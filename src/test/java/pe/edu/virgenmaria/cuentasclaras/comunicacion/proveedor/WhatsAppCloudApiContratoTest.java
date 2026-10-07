package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Sprint 5: contrato con la Cloud API de WhatsApp (Meta), con {@code MockRestServiceServer}: la plantilla aprobada con sus
 * parámetros y el botón con sufijo dinámico; número inválido o rechazado, definitivo; 5xx, reintento; fuera de prod solo
 * a los números de prueba. Los nombres exactos de la API deben confirmarse con Meta antes de activarlo.
 */
class WhatsAppCloudApiContratoTest {

	private static final String URL = "https://graph.facebook.com/v21.0/1098765/messages";

	private static PropiedadesMensajeria.Whatsapp cuenta(String api, boolean permitir, String prueba) {
		return new PropiedadesMensajeria.Whatsapp("WHATSAPP_CLOUD", api, "v21.0", "1098765", "token-meta", "secreto",
				"verifica", "graph.facebook.com", permitir, prueba, "es");
	}

	private record Cliente(MockRestServiceServer servidor, WhatsAppCloudApi api) {
	}

	private static Cliente cliente(boolean produccion, String prueba) {
		RestClient.Builder constructor = RestClient.builder();
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
		return new Cliente(servidor, new WhatsAppCloudApi(cuenta("https://graph.facebook.com", true, prueba), produccion,
				constructor));
	}

	@Test
	void plantillaConParametrosYBoton() {
		Cliente c = cliente(true, "");
		c.servidor().expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer token-meta"))
				.andExpect(jsonPath("$.messaging_product").value("whatsapp"))
				.andExpect(jsonPath("$.to").value("51987654321"))
				.andExpect(jsonPath("$.type").value("template"))
				.andExpect(jsonPath("$.template.name").value("cc_activacion"))
				.andExpect(jsonPath("$.template.language.code").value("es"))
				.andExpect(jsonPath("$.template.components[0].type").value("body"))
				.andExpect(jsonPath("$.template.components[0].parameters[0].text").value("07/10 a las 10:40"))
				.andExpect(jsonPath("$.template.components[1].type").value("button"))
				.andExpect(jsonPath("$.template.components[1].sub_type").value("url"))
				.andExpect(jsonPath("$.template.components[1].parameters[0].text").value("1/token-de-prueba"))
				.andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.HBgL\"}]}", MediaType.APPLICATION_JSON));

		ResultadoEnvio r = c.api().enviar(PlantillaMensaje.ACTIVACION, "+51987654321", List.of("07/10 a las 10:40"),
				"/activar/1/token-de-prueba");

		assertThat(r.aceptado()).isTrue();
		assertThat(r.proveedor()).isEqualTo(ProveedorMensajeria.WHATSAPP_CLOUD);
		assertThat(r.idProveedor()).isEqualTo("wamid.HBgL");
		c.servidor().verify();
	}

	@Test
	void numeroInvalidoEsDefinitivo() {
		Cliente c = cliente(true, "");
		c.servidor().expect(never(), requestTo(URL));
		assertThat(c.api().enviar(PlantillaMensaje.HUELLA, "12-34", List.of("a", "b", "c", "d"), null).tipo())
				.isEqualTo(ResultadoEnvio.Tipo.ERROR_DEFINITIVO);

		Cliente rechazo = cliente(true, "");
		rechazo.servidor().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body("{\"error\":{\"code\":131026,\"message\":\"Message undeliverable\"}}")
				.contentType(MediaType.APPLICATION_JSON));
		ResultadoEnvio r = rechazo.api().enviar(PlantillaMensaje.HUELLA, "+51987654321", List.of("a", "b", "c", "d"), null);
		assertThat(r.tipo()).isEqualTo(ResultadoEnvio.Tipo.ERROR_DEFINITIVO);
		assertThat(r.error()).contains("400");
	}

	@Test
	void cincoCientosEsReintento() {
		Cliente c = cliente(true, "");
		c.servidor().expect(requestTo(URL)).andRespond(withServerError());
		assertThat(c.api().enviar(PlantillaMensaje.HUELLA, "+51987654321", List.of("a", "b", "c", "d"), null).tipo())
				.isEqualTo(ResultadoEnvio.Tipo.ERROR_REINTENTABLE);

		Cliente limite = cliente(true, "");
		limite.servidor().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(limite.api().enviar(PlantillaMensaje.HUELLA, "+51987654321", List.of("a", "b", "c", "d"), null).tipo())
				.isEqualTo(ResultadoEnvio.Tipo.ERROR_REINTENTABLE);

		Cliente sinId = cliente(true, "");
		sinId.servidor().expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThat(sinId.api().enviar(PlantillaMensaje.HUELLA, "+51987654321", List.of("a", "b", "c", "d"), null).tipo())
				.isEqualTo(ResultadoEnvio.Tipo.ERROR_REINTENTABLE);
	}

	@Test
	void fueraDeProdSoloANumerosDePrueba() {
		Cliente c = cliente(false, "51966777321");
		c.servidor().expect(requestTo(URL)).andExpect(jsonPath("$.to").value("51966777321"))
				.andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

		// Un padre real: ni siquiera se llama a Meta.
		assertThat(c.api().enviar(PlantillaMensaje.HUELLA, "+51987654321", List.of("a", "b", "c", "d"), null).tipo())
				.isEqualTo(ResultadoEnvio.Tipo.ERROR_DEFINITIVO);
		assertThat(c.api().enviar(PlantillaMensaje.HUELLA, "+51966777321", List.of("a", "b", "c", "d"), null).aceptado())
				.isTrue();
		c.servidor().verify();

		// Sin la marca o sin la lista, el conector real ni se crea fuera de prod; tampoco con otro dominio.
		assertThatThrownBy(() -> new WhatsAppCloudApi(cuenta("https://graph.facebook.com", false, "51966777321"), false,
				RestClient.builder())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new WhatsAppCloudApi(cuenta("https://graph.facebook.com", true, ""), false,
				RestClient.builder())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new WhatsAppCloudApi(cuenta("https://evil.pe", true, "51966777321"), true,
				RestClient.builder())).isInstanceOf(IllegalStateException.class);
	}
}
