package pe.edu.virgenmaria.cuentasclaras.exportacion.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.EntregaPublica;

import java.nio.file.Path;

/**
 * Sprint 11, tanda 2 (3.9 y AP2-4): la página PÚBLICA donde el titular recibe la exportación de los datos del colegio. El
 * enlace le llegó solo por correo; aquí escribe su DNI, recibe el código solo por WhatsApp, ve la clave una vez y
 * descarga (hasta 3 veces). Sin lógica: delega en {@link EntregaPublica}. La respuesta nunca se guarda en caché.
 */
@Controller
public class EntregaExportacionController {

	private final EntregaPublica entrega;

	public EntregaExportacionController(EntregaPublica entrega) {
		this.entrega = entrega;
	}

	@GetMapping("/exportacion/{token}")
	public String ver(@PathVariable String token, Model model) {
		model.addAttribute("token", token);
		try {
			model.addAttribute("entrega", entrega.ver(token));
		}
		catch (RecursoNoEncontradoException e) {
			model.addAttribute("entrega", null);
		}
		return "exportacion/entrega";
	}

	@PostMapping("/exportacion/{token}/dni")
	public String dni(@PathVariable String token, @RequestParam(required = false) String dni, RedirectAttributes avisos) {
		try {
			entrega.confirmarDni(token, dni);
			avisos.addFlashAttribute("exito", "Listo: te enviamos un código por WhatsApp. Vale 10 minutos.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/exportacion/" + token;
	}

	@PostMapping("/exportacion/{token}/codigo")
	public String codigo(@PathVariable String token, @RequestParam(required = false) String codigo, Model model,
			RedirectAttributes avisos) {
		try {
			String clave = entrega.confirmarCodigo(token, codigo);
			model.addAttribute("token", token);
			model.addAttribute("entrega", entrega.ver(token));
			model.addAttribute("clave", clave);
			model.addAttribute("codigo", codigo);
			return "exportacion/entrega";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/exportacion/" + token;
		}
	}

	@PostMapping("/exportacion/{token}/descargar")
	public Object descargar(@PathVariable String token, @RequestParam(required = false) String codigo,
			HttpServletRequest peticion, RedirectAttributes avisos) {
		try {
			Path archivo = entrega.descargar(token, codigo, peticion.getRemoteAddr());
			Resource recurso = new FileSystemResource(archivo);
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
					.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
							.filename(archivo.getFileName().toString()).build().toString())
					.header(HttpHeaders.CACHE_CONTROL, "no-store").body(recurso);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/exportacion/" + token;
		}
	}
}
