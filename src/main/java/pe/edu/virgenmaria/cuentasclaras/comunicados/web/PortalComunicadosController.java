package pe.edu.virgenmaria.cuentasclaras.comunicados.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.LecturaFamilia;

/**
 * Sprint 9, tanda 3 (sección 3.10, E23 a E25): los comunicados en el portal de la familia. Sin lógica: delega en
 * {@link LecturaFamilia} (rol APODERADO; de otra familia: 404). Confirmar es un POST con CSRF desde la sesión: no hay enlace
 * que confirme.
 */
@Controller
public class PortalComunicadosController {

	private final LecturaFamilia lectura;

	public PortalComunicadosController(LecturaFamilia lectura) {
		this.lectura = lectura;
	}

	@GetMapping("/familia/comunicados")
	public String lista(Model model) {
		model.addAttribute("comunicados", lectura.mios());
		return "familia/comunicados";
	}

	@GetMapping("/familia/comunicados/{id:\\d+}")
	public String abrir(@PathVariable long id, Model model) {
		model.addAttribute("c", lectura.abrir(id));
		return "familia/comunicado";
	}

	@PostMapping("/familia/comunicados/{id:\\d+}/confirmar")
	public String confirmar(@PathVariable long id, RedirectAttributes avisos) {
		try {
			lectura.confirmar(id);
			avisos.addFlashAttribute("exito", "Gracias: el colegio sabe que leyó este comunicado.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/familia/comunicados/" + id;
	}

	@GetMapping("/familia/comunicados/{id:\\d+}/adjunto")
	public ResponseEntity<byte[]> adjunto(@PathVariable long id) {
		return DescargaPdf.de(id, lectura.adjunto(id));
	}
}
