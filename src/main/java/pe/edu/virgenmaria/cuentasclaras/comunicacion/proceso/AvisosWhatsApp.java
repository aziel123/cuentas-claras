package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.config.PropiedadesMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Avisos de estado de WhatsApp (webhook de Meta, sprint 5, G14). Primero verifica la firma
 * {@code X-Hub-Signature-256} (HMAC-SHA256 del cuerpo con el secreto de la app) y recién entonces lee el cuerpo; un aviso
 * sin firma válida no se audita ni toca nada. Como {@code sistema.mensajeria} y en el colegio de la URL, actualiza el
 * estado por el id del proveedor: entregado, leído o fallido, nunca hacia atrás (los avisos llegan en desorden) y de
 * forma idempotente. El estado de entrega no mueve dinero.
 */
@Component
public class AvisosWhatsApp {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosWhatsApp.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Tamaño máximo del cuerpo de un aviso. */
	public static final int MAXIMO_BYTES = 64 * 1024;

	public enum Resultado {
		ACEPTADO, NO_AUTENTICO, NO_ENCONTRADO, MUY_GRANDE
	}

	private final MensajeRepository mensajes;

	private final DespachoMensajes despacho;

	private final PropiedadesMensajeria propiedades;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AvisosWhatsApp(MensajeRepository mensajes, DespachoMensajes despacho, PropiedadesMensajeria propiedades,
			RecorridoColegios colegios, PlatformTransactionManager transacciones, Clock reloj) {
		this.mensajes = mensajes;
		this.despacho = despacho;
		this.propiedades = propiedades;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	/** La verificación de Meta al registrar el webhook ({@code hub.challenge} si el token coincide). */
	public String verificar(String modo, String token, String desafio) {
		String esperado = propiedades.whatsapp().tokenVerificacion();
		if (!"subscribe".equals(modo) || esperado == null || esperado.isBlank() || token == null
				|| !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))
				|| desafio == null || !desafio.matches("[A-Za-z0-9_-]{1,100}")) {
			return null;
		}
		return desafio;
	}

	public Resultado recibir(long colegio, byte[] cuerpo, String firma) {
		if (cuerpo == null || cuerpo.length > MAXIMO_BYTES) {
			return Resultado.MUY_GRANDE;
		}
		if (!firmaValida(cuerpo, firma)) {
			return Resultado.NO_AUTENTICO;
		}
		if (!colegios.activos().contains(colegio)) {
			return Resultado.NO_ENCONTRADO;
		}
		JsonNode raiz;
		try {
			raiz = JSON.readTree(cuerpo);
		}
		catch (JacksonException e) {
			return Resultado.NO_AUTENTICO;
		}
		EjecucionComoSistema.como(ActorSistema.MENSAJERIA, colegio, () -> {
			for (JsonNode entrada : raiz.path("entry")) {
				for (JsonNode cambio : entrada.path("changes")) {
					for (JsonNode estado : cambio.path("value").path("statuses")) {
						aplicar(estado.path("id").asString(""), estado.path("status").asString(""),
								estado.path("timestamp").asString(""));
					}
				}
			}
		});
		return Resultado.ACEPTADO;
	}

	private void aplicar(String idProveedor, String estado, String marca) {
		if (idProveedor.isBlank()) {
			return;
		}
		try {
			transaccion.executeWithoutResult(t -> mensajes
					.findByProveedorAndProveedorMensajeId(ProveedorMensajeria.WHATSAPP_CLOUD, idProveedor)
					.flatMap(m -> mensajes.bloquear(m.getId())).ifPresent(m -> cambiar(m, estado, cuando(marca))));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo aplicar un aviso de WhatsApp: {}", e.getClass().getSimpleName());
		}
	}

	private void cambiar(Mensaje mensaje, String estado, LocalDateTime cuando) {
		boolean cambio = switch (estado.toLowerCase(Locale.ROOT)) {
			case "delivered" -> mensaje.entregado(cuando);
			case "read" -> mensaje.leido(cuando);
			case "failed" -> {
				if (!mensaje.getEstado().puedePasarA(EstadoMensaje.FALLIDO)) {
					yield false;
				}
				mensaje.fallar("WhatsApp avisó que no se pudo entregar.", false);
				yield true;
			}
			default -> false;
		};
		if (cambio) {
			mensajes.saveAndFlush(mensaje);
			if (mensaje.getEstado() == EstadoMensaje.FALLIDO) {
				despacho.alFallar(mensaje);
			}
		}
	}

	private LocalDateTime cuando(String marca) {
		try {
			return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(marca)), ZoneId.of("America/Lima"))
					.truncatedTo(ChronoUnit.MICROS);
		}
		catch (RuntimeException e) {
			return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		}
	}

	/** {@code sha256=<hex>} = HMAC-SHA256(cuerpo, secreto de la app), comparado en tiempo constante. */
	boolean firmaValida(byte[] cuerpo, String firma) {
		String secreto = propiedades.whatsapp().secretoApp();
		if (secreto == null || secreto.isBlank() || firma == null || !firma.startsWith("sha256=")) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] esperada = mac.doFinal(cuerpo);
			byte[] recibida = HexFormat.of().parseHex(firma.substring("sha256=".length()).strip().toLowerCase(Locale.ROOT));
			return MessageDigest.isEqual(esperada, recibida);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			return false;
		}
	}
}
