package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSesion;

/**
 * Formulario de ingreso. El POST a {@code /login} lo procesa Spring Security.
 */
@Controller
public class LoginController {

	private final PropiedadesSeguridad propiedades;

	private final PropiedadesSesion sesion;

	public LoginController(PropiedadesSeguridad propiedades, PropiedadesSesion sesion) {
		this.propiedades = propiedades;
		this.sesion = sesion;
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
				Math.min(propiedades.intentosMaximos(), sesion.intentosPorCuentaEIp()), propiedades.duracionBloqueo().toMinutes(),
				propiedades.vigenciaClaveTemporal().toHours()));
		return "seguridad/login";
	}

	/**
	 * Sprint 7, tanda 3 (H9): la conexión que debe esperar por demasiados intentos fallidos ve la página de ingreso con el
	 * aviso y un 429. Solo llega aquí reenviada por {@code FiltroLimiteIngresos} (la ruta no es pública).
	 */
	@RequestMapping(FiltroLimiteIngresos.RUTA_ESPERA)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public String demasiadosIntentos(HttpServletRequest peticion, Model model) {
		Object mensaje = peticion.getAttribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE);
		model.addAttribute("mensaje", new MensajeLogin("error", mensaje instanceof String texto ? texto
				: "Demasiados intentos desde esta conexión. Espera unos minutos."));
		return "seguridad/login";
	}
}
