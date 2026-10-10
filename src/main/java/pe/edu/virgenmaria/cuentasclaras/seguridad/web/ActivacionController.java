package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioActivacionCuenta;

/**
 * Página pública del enlace de activación del apoderado (S4-M2): confirma su documento y elige su clave. Sin lógica:
 * delega en {@link ServicioActivacionCuenta}.
 */
@Controller
public class ActivacionController {

	private final ServicioActivacionCuenta activacion;

	public ActivacionController(ServicioActivacionCuenta activacion) {
		this.activacion = activacion;
	}

	@GetMapping("/activar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}")
	public String pagina(@PathVariable Long colegio, @PathVariable String token, Model model) {
		model.addAttribute("vista", activacion.vista(colegio, token).orElse(null));
		model.addAttribute("colegio", colegio);
		model.addAttribute("token", token);
		return "seguridad/activar";
	}

	@PostMapping("/activar/{colegio:\\d+}/{token:[A-Za-z0-9_-]+}")
	public String activar(@PathVariable Long colegio, @PathVariable String token,
			@RequestParam(required = false) String documento, @RequestParam(required = false) String clave,
			@RequestParam(required = false) String confirmacion,
			@RequestParam(defaultValue = "false") boolean aceptaPrivacidad, RedirectAttributes avisos) {
		try {
			activacion.activar(colegio, token, documento, clave, confirmacion, aceptaPrivacidad);
			return "redirect:/login?cuenta-activada";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/activar/" + colegio + "/" + token;
		}
	}
}
