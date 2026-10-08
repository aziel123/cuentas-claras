package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.edu.virgenmaria.cuentasclaras.comun.config.VerificadorConfiguracion;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ProveedorWhatsApp;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp real: Cloud API de Meta ({@code POST {api}/{version}/{numero-id}/messages} con una plantilla «utility»
 * aprobada). APAGADO por defecto: solo existe con {@code cuentasclaras.mensajeria.whatsapp.proveedor: WHATSAPP_CLOUD}.
 * <ul>
 *   <li>La URL es {@code https} y su host está en la lista cerrada {@code dominios-permitidos}; sin token, no se crea.</li>
 *   <li>Fuera de {@code prod} SOLO escribe a los {@code numeros-de-prueba}: desde dev nunca se escribe a un padre real.</li>
 *   <li>429, 401/403, 5xx o error de red: se reintenta. Otro 4xx (número inválido, sin WhatsApp, plantilla
 *       rechazada): definitivo, y sale el respaldo por correo.</li>
 * </ul>
 * A Meta solo van el número, la plantilla y los parámetros (Ley 29733). Los nombres de la API deben confirmarse con la
 * documentación vigente de Meta antes de activarlo (sección 17, decisión 38).
 */
@Component
@ConditionalOnProperty(name = "cuentasclaras.mensajeria.whatsapp.proveedor", havingValue = "WHATSAPP_CLOUD")
public class WhatsAppCloudApi implements ProveedorWhatsApp {

	private static final Logger LOG = LoggerFactory.getLogger(WhatsAppCloudApi.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient cliente;

	private final PropiedadesMensajeria.Whatsapp cuenta;

	private final boolean produccion;

	@Autowired
	public WhatsAppCloudApi(PropiedadesMensajeria propiedades, Environment entorno) {
		this(propiedades.whatsapp(), Arrays.asList(entorno.getActiveProfiles()).contains("prod"),
				RestClient.builder().requestFactory(fabrica()));
	}

	/** Con el constructor del cliente que se quiera (las pruebas de contrato usan {@code MockRestServiceServer}). */
	WhatsAppCloudApi(PropiedadesMensajeria.Whatsapp cuenta, boolean produccion, RestClient.Builder constructor) {
		this.cuenta = cuenta;
		this.produccion = produccion;
		if (!VerificadorConfiguracion.rutaPermitida(cuenta.api(), cuenta.dominiosPermitidos()) || vacio(cuenta.token())
				|| vacio(cuenta.numeroId())) {
			throw new IllegalStateException("WhatsApp necesita una URL https de un dominio permitido, su token y el id de "
					+ "su número.");
		}
		if (!produccion && (!cuenta.permitirRealFueraDeProd() || cuenta.listaDePrueba().isEmpty())) {
			throw new IllegalStateException("Fuera de producción, WhatsApp real exige permitir-real-fuera-de-prod y una "
					+ "lista de números de prueba.");
		}
		this.cliente = constructor.baseUrl(cuenta.api().strip())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cuenta.token().strip())
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	private static SimpleClientHttpRequestFactory fabrica() {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(10));
		fabrica.setReadTimeout(Duration.ofSeconds(20));
		return fabrica;
	}

	@Override
	public ResultadoEnvio enviar(PlantillaMensaje plantilla, String destino, List<String> parametros, String sufijoBoton) {
		String numero = destino == null ? "" : destino.replace("+", "").strip();
		if (!numero.matches("[0-9]{9,15}")) {
			return ResultadoEnvio.definitivo("El número de WhatsApp no es válido.");
		}
		if (!produccion && !cuenta.listaDePrueba().contains(numero) && !cuenta.listaDePrueba().contains("+" + numero)) {
			return ResultadoEnvio.definitivo("Fuera de producción solo se escribe a los números de prueba.");
		}
		Map<String, Object> cuerpo = cuerpo(plantilla, numero, parametros, sufijoBoton);
		try {
			String respuesta = cliente.post().uri("/{version}/{numero}/messages", cuenta.versionApi(), cuenta.numeroId())
					.body(cuerpo).retrieve().body(String.class);
			JsonNode id = JSON.readTree(respuesta == null ? "{}" : respuesta).path("messages").path(0).path("id");
			if (id.isMissingNode() || id.asString("").isBlank()) {
				return ResultadoEnvio.reintentable("WhatsApp no devolvió el id del mensaje.");
			}
			return ResultadoEnvio.aceptado(ProveedorMensajeria.WHATSAPP_CLOUD, id.asString());
		}
		catch (RestClientResponseException e) {
			int estado = e.getStatusCode().value();
			LOG.warn("WhatsApp respondió {} a la plantilla {}.", estado, plantilla.nombreMeta());
			if (estado == 429 || estado == 401 || estado == 403 || estado >= 500) {
				return ResultadoEnvio.reintentable("WhatsApp respondió " + estado + ": se reintentará.");
			}
			return ResultadoEnvio.definitivo("WhatsApp rechazó el mensaje (" + estado + "): número sin WhatsApp, inválido "
					+ "o plantilla no aprobada.");
		}
		catch (RestClientException | tools.jackson.core.JacksonException e) {
			LOG.warn("No se pudo enviar la plantilla {} a WhatsApp: {}", plantilla.nombreMeta(), e.getClass().getSimpleName());
			return ResultadoEnvio.reintentable("Sin respuesta de WhatsApp (" + e.getClass().getSimpleName() + ").");
		}
	}

	Map<String, Object> cuerpo(PlantillaMensaje plantilla, String numero, List<String> parametros, String sufijoBoton) {
		List<Map<String, Object>> componentes = new ArrayList<>();
		componentes.add(Map.of("type", "body", "parameters",
				parametros.stream().map(p -> Map.<String, Object>of("type", "text", "text", p)).toList()));
		if (sufijoBoton != null) {
			String sufijo = sufijoBoton.startsWith("/activar/") ? sufijoBoton.substring("/activar/".length())
					: sufijoBoton.startsWith("/verificar/") ? sufijoBoton.substring("/verificar/".length()) : sufijoBoton;
			componentes.add(Map.of("type", "button", "sub_type", "url", "index", "0", "parameters",
					List.of(Map.of("type", "text", "text", sufijo))));
		}
		Map<String, Object> template = new LinkedHashMap<>();
		template.put("name", plantilla.nombreMeta());
		template.put("language", Map.of("code", cuenta.idiomaPlantillas()));
		template.put("components", componentes);
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("messaging_product", "whatsapp");
		cuerpo.put("to", numero);
		cuerpo.put("type", "template");
		cuerpo.put("template", template);
		return cuerpo;
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}
}
