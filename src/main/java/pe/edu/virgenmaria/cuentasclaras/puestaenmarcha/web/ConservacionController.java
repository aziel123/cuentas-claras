package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.ConservacionDatos;

/**
 * Sprint 11, tanda 2 (AP-11 y AP2-9): el paso 16 de la Puesta en marcha, la autorización de conservación del titular. Sin
 * lógica: delega en {@link ConservacionDatos} (que exige al titular y su firma).
 */
@Controller
public class ConservacionController {

	private final ConservacionDatos conservacion;

	public ConservacionController(ConservacionDatos conservacion) {
		this.conservacion = conservacion;
	}

	@GetMapping("/colegio/conservacion")
	public String ver(Model model) {
		model.addAttribute("conservacion", conservacion.vista());
		return "colegio/conservacion";
	}

	@PostMapping("/colegio/conservacion/autorizar")
	public String autorizar(RedirectAttributes avisos) {
		try {
			conservacion.autorizar();
			avisos.addFlashAttribute("exito", "Listo: autorizaste con tu firma conservar los datos 12 meses desde una "
					+ "suspensión. Se avisó al correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/colegio/conservacion";
	}

	@PostMapping("/colegio/conservacion/retirar")
	public String retirar(RedirectAttributes avisos) {
		try {
			conservacion.retirar();
			avisos.addFlashAttribute("exito", "Listo: retiraste la autorización. Se avisó al correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/colegio/conservacion";
	}
}
