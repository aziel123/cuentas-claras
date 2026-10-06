package pe.edu.virgenmaria.cuentasclaras.pasarela.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.RecepcionAvisos;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Avisos (webhooks) de la pasarela: {@code POST /webhooks/pasarela/{proveedor}/{colegio}}, sin sesión ni CSRF (cadena
 * de seguridad aparte). Lee como mucho 16 KB (si no, 413) y delega en {@link RecepcionAvisos}: aviso no auténtico → 401
 * (sin escribir en la bitácora), proveedor o colegio que no existen → 404, recibido o ya recibido → 200. El aviso nunca
 * registra dinero por sí mismo: despierta la consulta a la pasarela.
 */
@Controller
public class WebhookPasarelaController {

	private final RecepcionAvisos recepcion;

	public WebhookPasarelaController(RecepcionAvisos recepcion) {
		this.recepcion = recepcion;
	}

	@PostMapping("/webhooks/pasarela/{proveedor:[A-Za-z]{3,20}}/{colegio:\\d{1,12}}")
	@ResponseBody
	public ResponseEntity<String> recibir(@PathVariable String proveedor, @PathVariable Long colegio,
			HttpServletRequest peticion) throws IOException {
		int maximo = recepcion.maximoBytes();
		if (peticion.getContentLengthLong() > maximo) {
			return respuesta(HttpStatus.CONTENT_TOO_LARGE, "aviso demasiado grande");
		}
		byte[] cuerpo;
		try (InputStream entrada = peticion.getInputStream()) {
			cuerpo = entrada.readNBytes(maximo + 1);
		}
		Map<String, String> cabeceras = new HashMap<>();
		for (String nombre : Collections.list(peticion.getHeaderNames())) {
			cabeceras.put(nombre.toLowerCase(Locale.ROOT), peticion.getHeader(nombre));
		}
		return switch (recepcion.recibir(proveedor, colegio, cuerpo, cabeceras)) {
			case ACEPTADO -> respuesta(HttpStatus.OK, "recibido");
			case YA_RECIBIDO -> respuesta(HttpStatus.OK, "ya recibido");
			case NO_AUTENTICO -> respuesta(HttpStatus.UNAUTHORIZED, "aviso no autentico");
			case NO_ENCONTRADO -> respuesta(HttpStatus.NOT_FOUND, "no encontrado");
			case MUY_GRANDE -> respuesta(HttpStatus.CONTENT_TOO_LARGE, "aviso demasiado grande");
		};
	}

	private static ResponseEntity<String> respuesta(HttpStatus estado, String texto) {
		return ResponseEntity.status(estado).contentType(MediaType.TEXT_PLAIN).body(texto);
	}
}
