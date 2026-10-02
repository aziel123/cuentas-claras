package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Locale;

/**
 * Página de inicio según el rol principal del usuario (prioridad: PROMOTOR, DIRECTOR,
 * ADMINISTRACION, CAJA, DOCENTE, APODERADO). El menú y las tarjetas los agrega {@code ModeloSesionAdvice}.
 */
@Controller
public class InicioController {

	@GetMapping("/")
	public String raiz() {
		return "redirect:/inicio";
	}

	@GetMapping("/inicio")
	public String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return "inicio/" + usuario.rolPrincipal().name().toLowerCase(Locale.ROOT);
	}
}
