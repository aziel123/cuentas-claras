package pe.edu.virgenmaria.cuentasclaras.demo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivosPermitidosImportacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.demo.service.DemoColegios;

import java.io.IOException;
import java.io.InputStream;

/**
 * Sprint 8, tanda 3 (sección 8.2): las páginas públicas de la demo. Solo existe en el perfil {@code demo} (las rutas las
 * abre {@code ConfiguracionSeguridad} solo en ese perfil). Sin lógica: la creación es de {@link DemoColegios}.
 * <ul>
 *   <li>{@code GET /demo}: qué es y qué no hace, y el botón «Crear mi demo»;</li>
 *   <li>{@code POST /demo} (con CSRF): crea el colegio ficticio y muestra UNA vez sus cuentas y su clave;</li>
 *   <li>{@code GET /demo/guia}: el recorrido de 6 pasos;</li>
 *   <li>{@code GET /demo/ejemplos/{archivo}}: los dos archivos de ejemplo de la importación;</li>
 *   <li>{@code GET /robots.txt}: ningún buscador la indexa (además de la cabecera {@code X-Robots-Tag}).</li>
 * </ul>
 */
@Controller
@Profile("demo")
public class DemoController {

	private final DemoColegios demos;

	public DemoController(DemoColegios demos) {
		this.demos = demos;
	}

	@GetMapping("/demo")
	public String inicio() {
		return "demo/inicio";
	}

	@PostMapping("/demo")
	public String crear(HttpServletRequest peticion, Model modelo) {
		try {
			modelo.addAttribute("demo", demos.crear(peticion.getRemoteAddr()));
			return "demo/creada";
		}
		catch (ReglaNegocioException e) {
			modelo.addAttribute("error", e.getMessage());
			return "demo/inicio";
		}
	}

	@GetMapping("/demo/guia")
	public String guia() {
		return "demo/guia";
	}

	@GetMapping("/demo/ejemplos/{archivo}")
	public ResponseEntity<byte[]> ejemplo(@PathVariable String archivo) throws IOException {
		if (!ArchivosPermitidosImportacion.EJEMPLOS.contains(archivo)) {
			throw new RecursoNoEncontradoException("Archivo de ejemplo no encontrado");
		}
		try (InputStream entrada = new ClassPathResource("demo/" + archivo).getInputStream()) {
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build()
							.toString())
					.cacheControl(CacheControl.noStore()).body(entrada.readAllBytes());
		}
	}

	@GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public String robots() {
		return "User-agent: *\nDisallow: /\n";
	}
}
