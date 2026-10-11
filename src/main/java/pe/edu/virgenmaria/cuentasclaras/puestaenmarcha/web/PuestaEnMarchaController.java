package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Sprint 8, tanda 1 (secciones 3.4 y 3.5): la lista de verificación y el paso a producción (solo Promotoría). Sin lógica:
 * delega en {@link PasoAProduccion}.
 */
@Controller
public class PuestaEnMarchaController {

	private final PasoAProduccion paso;

	public PuestaEnMarchaController(PasoAProduccion paso) {
		this.paso = paso;
	}

	@GetMapping("/puesta-en-marcha")
	public String lista(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("lista", paso.lista());
		model.addAttribute("puedePasar", usuario != null && usuario.roles().contains(Rol.PROMOTOR));
		return "puesta-en-marcha/lista";
	}

	@PostMapping("/puesta-en-marcha/produccion")
	public String produccion(RedirectAttributes avisos) {
		try {
			paso.pasar();
			avisos.addFlashAttribute("exito", "Listo: el colegio está en producción. Desde ahora Caja registra pagos y las "
					+ "familias reciben sus avisos.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}
}
