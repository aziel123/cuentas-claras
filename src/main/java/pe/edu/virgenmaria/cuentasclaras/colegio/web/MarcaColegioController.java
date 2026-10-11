package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.MarcaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.MarcaColegioVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioMarcaColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

import java.io.IOException;

/**
 * Sprint 8, tanda 1 (sección 3.7): la marca del colegio. {@code /colegio/marca} (solo Promotoría, por la matriz y por el
 * servicio) y {@code /marca/logo} (el logo del colegio de la sesión, para cualquier persona con sesión: sin id, sin IDOR).
 * Sin lógica: delega en {@link ServicioMarcaColegio} y {@link LogosColegio}.
 */
@Controller
public class MarcaColegioController {

	private final ServicioMarcaColegio marca;

	private final LogosColegio logos;

	public MarcaColegioController(ServicioMarcaColegio marca, LogosColegio logos) {
		this.marca = marca;
		this.logos = logos;
	}

	@GetMapping("/colegio/marca")
	public String formulario(Model model) {
		MarcaColegioVista vista = marca.ver();
		model.addAttribute("marca", vista);
		if (!model.containsAttribute("datos")) {
			model.addAttribute("datos", new MarcaColegioRequest(vista.nombre(), vista.razonSocial(), vista.direccion(),
					vista.correoInstitucional()));
		}
		return "colegio/marca";
	}

	@PostMapping("/colegio/marca")
	public String guardar(@Valid @ModelAttribute("datos") MarcaColegioRequest datos, BindingResult validacion,
			@RequestParam(name = "logo", required = false) MultipartFile logo, RedirectAttributes avisos)
			throws IOException {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/colegio/marca";
		}
		try {
			boolean conArchivo = logo != null && !logo.isEmpty();
			marca.cambiar(datos, conArchivo ? logo.getBytes() : null, conArchivo ? logo.getOriginalFilename() : null);
			avisos.addFlashAttribute("exito", "Listo: la marca del colegio quedó guardada"
					+ (conArchivo ? " con su logo nuevo." : ".") + " Las familias la verán en su portal y en sus boletas.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/colegio/marca";
	}

	/**
	 * El logo vigente del colegio de la sesión como PNG. Sin logo: 404. Cabeceras: {@code nosniff} y una CSP sin nada
	 * (el PNG se reescribió, pero aun así el navegador no lo trata como documento), caché privada de un día.
	 */
	@GetMapping(ModuloApp.RUTA_LOGO)
	public ResponseEntity<byte[]> logo() {
		return logos.vigente().map(logo -> {
			byte[] png = logo.getContenido();
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(png.length)
					.header("X-Content-Type-Options", "nosniff")
					.header("Content-Security-Policy", "default-src 'none'")
					.header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
					.header("Content-Disposition", "inline; filename=\"logo.png\"")
					.body(png);
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}
}
