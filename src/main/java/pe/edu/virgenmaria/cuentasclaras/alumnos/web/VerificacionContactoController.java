package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioVerificacionContacto;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Página pública del enlace de verificación de un contacto (correcciones del sprint 5, S5-A1). Sin lógica: delega en
 * {@link ServicioVerificacionContacto}.
 */
@Controller
public class VerificacionContactoController {

	private final ServicioVerificacionContacto verificacion;

	public VerificacionContactoController(ServicioVerificacionContacto verificacion) {
		this.verificacion = verificacion;
	}

	@GetMapping("/verificar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}")
	public String pagina(@PathVariable Long colegio, @PathVariable String token, Model model) {
		model.addAttribute("vista", verificacion.vista(colegio, token).orElse(null));
		model.addAttribute("colegio", colegio);
		model.addAttribute("token", token);
		return "alumnos/verificar-contacto";
	}

	@PostMapping("/verificar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}")
	public String verificar(@PathVariable Long colegio, @PathVariable String token,
			@RequestParam(required = false) String documento, RedirectAttributes avisos) {
		try {
			verificacion.verificar(colegio, token, documento);
			avisos.addFlashAttribute("verificado", true);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/verificar/" + colegio + "/" + token;
	}
}
