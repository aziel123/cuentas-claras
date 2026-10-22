package pe.edu.virgenmaria.cuentasclaras.comunicados.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Sprint 9, tanda 3 (sección 3.9, punto 5): cómo se sirve el PDF saneado de un comunicado. Siempre como descarga
 * ({@code attachment}, con un nombre fijo: el original no se usa), {@code nosniff}, una CSP que no deja ejecutar nada y sin
 * caché compartida.
 */
final class DescargaPdf {

	private DescargaPdf() {
	}

	static ResponseEntity<byte[]> de(long comunicadoId, byte[] contenido) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("comunicado-" + comunicadoId + ".pdf").build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "sandbox; default-src 'none'")
				.cacheControl(CacheControl.noStore().cachePrivate()).body(contenido);
	}
}
