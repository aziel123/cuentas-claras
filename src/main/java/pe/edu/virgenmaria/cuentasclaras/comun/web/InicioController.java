package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioInicio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Locale;

/**
 * Página de inicio según el rol principal del usuario (prioridad: PROMOTOR, DIRECTOR,
 * ADMINISTRACION, CAJA, DOCENTE, APODERADO). El menú y las tarjetas los agrega {@code ModeloSesionAdvice}.
 */
@Controller
public class InicioController {

	private final ServicioInicio servicioInicio;

	public InicioController(ServicioInicio servicioInicio) {
		this.servicioInicio = servicioInicio;
	}

	@GetMapping("/")
	public String raiz() {
		return "redirect:/inicio";
	}

	@GetMapping("/inicio")
	public String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("avisoClave", servicioInicio.avisoClaveRestablecida(usuario.usuarioId()));
		Rol principal = usuario.rolPrincipal();
		if (principal == Rol.PROMOTOR) {
			model.addAttribute("paraRevisar", servicioInicio.paraRevisar());
		}
		return "inicio/" + principal.name().toLowerCase(Locale.ROOT);
	}
}
