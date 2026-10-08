package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;

/**
 * Formulario de ingreso. El POST a {@code /login} lo procesa Spring Security.
 */
@Controller
public class LoginController {

	private final PropiedadesSeguridad propiedades;

	public LoginController(PropiedadesSeguridad propiedades) {
		this.propiedades = propiedades;
	}

	@GetMapping("/login")
	public String login(Authentication autenticacion,
			@RequestParam(required = false) String error,
			@RequestParam(required = false) String vencida,
			@RequestParam(required = false) String salio,
			@RequestParam(required = false) String expirada,
			@RequestParam(name = "clave-cambiada", required = false) String claveCambiada,
			@RequestParam(name = "cuenta-activada", required = false) String cuentaActivada,
			Model model) {
		if (autenticacion != null && autenticacion.isAuthenticated()
				&& !(autenticacion instanceof AnonymousAuthenticationToken)) {
			return "redirect:/inicio";
		}
		model.addAttribute("mensaje", MensajeLogin.para(error, vencida, salio, expirada, claveCambiada, cuentaActivada,
				propiedades.intentosMaximos(), propiedades.duracionBloqueo().toMinutes(),
				propiedades.vigenciaClaveTemporal().toHours()));
		return "seguridad/login";
	}
}
