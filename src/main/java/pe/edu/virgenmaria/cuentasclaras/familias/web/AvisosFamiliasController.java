package pe.edu.virgenmaria.cuentasclaras.familias.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;

/**
 * Bandeja de avisos de las familias (pantalla 8): SOLO Promotoría y Dirección (Caja y Administración reciben 403). Sin
 * lógica: delega en {@link ServicioAvisosFamilia}.
 */
@Controller
public class AvisosFamiliasController {

	private final ServicioAvisosFamilia avisos;

	public AvisosFamiliasController(ServicioAvisosFamilia avisos) {
		this.avisos = avisos;
	}

	@GetMapping("/avisos-familias")
	public String bandeja(Model model) {
		model.addAttribute("avisos", avisos.bandeja());
		return "avisos-familias/bandeja";
	}

	@PostMapping("/avisos-familias/{id:\\d+}/atender")
	public String atender(@PathVariable Long id, @RequestParam(required = false) String respuesta,
			RedirectAttributes mensajes) {
		try {
			avisos.atender(id, respuesta);
			mensajes.addFlashAttribute("exito", "Listo: la familia recibe un mensaje y ve tu respuesta en el portal.");
		}
		catch (ReglaNegocioException e) {
			mensajes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/avisos-familias";
	}
}
