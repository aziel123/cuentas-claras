package pe.edu.virgenmaria.cuentasclaras.conciliacion.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Descarga del extracto de EJEMPLO (solo perfil {@code dev}): en desarrollo no hay banca por internet. Dentro del módulo
 * de conciliación (las mismas personas que lo ven).
 */
@Controller
@Profile("dev")
public class EjemploExtractoDevController {

	private final EjemploExtractoDev ejemplo;

	public EjemploExtractoDevController(EjemploExtractoDev ejemplo) {
		this.ejemplo = ejemplo;
	}

	@GetMapping("/conciliacion/extractos/ejemplo.csv")
	public ResponseEntity<byte[]> descargar() {
		if (!ejemplo.disponible()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ejemplo.nombre()).build()
						.toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(ejemplo.contenido());
	}
}
