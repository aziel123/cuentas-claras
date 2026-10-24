package pe.edu.virgenmaria.cuentasclaras.academico.libreta.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Sprint 10, tanda 3 (sección 3.9, punto 5): cómo se sirve una libreta, como el adjunto del sprint 9. Siempre como descarga
 * ({@code attachment; filename="libreta-{id}-v{n}.pdf"}), {@code nosniff}, una CSP que no deja ejecutar nada y sin caché
 * compartida. La cola llena (E38) responde 503 con «intente en unos minutos».
 */
final class DescargaLibreta {

	private DescargaLibreta() {
	}

	static ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "sandbox; default-src 'none'")
				.cacheControl(CacheControl.noStore().cachePrivate()).body(contenido);
	}

	static ResponseEntity<byte[]> ocupado(String mensaje) {
		return texto(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
	}

	/** Una impresión que no se pudo hacer (409, con el motivo en lenguaje claro). */
	static ResponseEntity<byte[]> rechazada(String mensaje) {
		return texto(HttpStatus.CONFLICT, mensaje);
	}

	private static ResponseEntity<byte[]> texto(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).header(HttpHeaders.RETRY_AFTER, "120")
				.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
				.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
				.body(mensaje.getBytes(StandardCharsets.UTF_8));
	}
}
