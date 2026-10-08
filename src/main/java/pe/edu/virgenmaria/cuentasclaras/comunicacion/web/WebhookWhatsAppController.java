package pe.edu.virgenmaria.cuentasclaras.comunicacion.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosWhatsApp;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.LimiteWebhookPorIp;

import java.io.IOException;
import java.io.InputStream;

/**
 * Webhook de WhatsApp ({@code /webhooks/whatsapp/{colegio}}), sin sesión ni CSRF (cadena de seguridad de los webhooks):
 * {@code GET} para la verificación de Meta ({@code hub.challenge}) y {@code POST} para los avisos de estado firmados
 * ({@code X-Hub-Signature-256}). Sin firma válida: 401 y nada se toca ni se audita. Más de 64 KB: 413. Límite por IP:
 * 429. Sin lógica: delega en {@link AvisosWhatsApp}.
 */
@Controller
public class WebhookWhatsAppController {

	private final AvisosWhatsApp avisos;

	private final LimiteWebhookPorIp limite;

	public WebhookWhatsAppController(AvisosWhatsApp avisos, LimiteWebhookPorIp limite) {
		this.avisos = avisos;
		this.limite = limite;
	}

	@GetMapping("/webhooks/whatsapp/{colegio:\\d{1,12}}")
	@ResponseBody
	public ResponseEntity<String> verificar(@PathVariable Long colegio,
			@RequestParam(name = "hub.mode", required = false) String modo,
			@RequestParam(name = "hub.verify_token", required = false) String token,
			@RequestParam(name = "hub.challenge", required = false) String desafio) {
		String respuesta = avisos.verificar(modo, token, desafio);
		return respuesta == null ? texto(HttpStatus.FORBIDDEN, "no autorizado") : texto(HttpStatus.OK, respuesta);
	}

	@PostMapping("/webhooks/whatsapp/{colegio:\\d{1,12}}")
	@ResponseBody
	public ResponseEntity<String> recibir(@PathVariable Long colegio, HttpServletRequest peticion) throws IOException {
		if (!limite.permitir(peticion.getRemoteAddr())) {
			return texto(HttpStatus.TOO_MANY_REQUESTS, "demasiados avisos");
		}
		if (peticion.getContentLengthLong() > AvisosWhatsApp.MAXIMO_BYTES) {
			return texto(HttpStatus.CONTENT_TOO_LARGE, "aviso demasiado grande");
		}
		byte[] cuerpo;
		try (InputStream entrada = peticion.getInputStream()) {
			cuerpo = entrada.readNBytes(AvisosWhatsApp.MAXIMO_BYTES + 1);
		}
		return switch (avisos.recibir(colegio, cuerpo, peticion.getHeader("X-Hub-Signature-256"))) {
			case ACEPTADO -> texto(HttpStatus.OK, "recibido");
			case NO_AUTENTICO, NO_ENCONTRADO -> texto(HttpStatus.UNAUTHORIZED, "aviso no autentico");
			case MUY_GRANDE -> texto(HttpStatus.CONTENT_TOO_LARGE, "aviso demasiado grande");
		};
	}

	private static ResponseEntity<String> texto(HttpStatus estado, String cuerpo) {
		return ResponseEntity.status(estado).contentType(MediaType.TEXT_PLAIN).body(cuerpo);
	}
}
