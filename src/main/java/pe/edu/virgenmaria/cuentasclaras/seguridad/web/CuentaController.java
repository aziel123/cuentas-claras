package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarClaveRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ClaveActualIncorrectaException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioCuenta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Cambio de la propia clave. Es la única pantalla de quien ingresó con una clave temporal.
 * Después del cambio se cierra la sesión y el usuario vuelve a ingresar con la clave nueva.
 */
@Controller
@RequestMapping(ModuloApp.RUTA_CAMBIAR_CLAVE)
public class CuentaController {

	private static final String VISTA = "cuenta/cambiar-clave";

	private final ServicioCuenta servicioCuenta;

	private final LogoutHandler cierreSesion = new SecurityContextLogoutHandler();

	public CuentaController(ServicioCuenta servicioCuenta) {
		this.servicioCuenta = servicioCuenta;
	}

	@GetMapping
	public String formulario(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("solicitud", CambiarClaveRequest.vacio());
		model.addAttribute("clavePendiente", usuario.debeCambiarClave());
		return VISTA;
	}

	@PostMapping
	public String cambiar(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @ModelAttribute("solicitud") CambiarClaveRequest solicitud, BindingResult validacion, Model model,
			Authentication autenticacion, HttpServletRequest request, HttpServletResponse response) {
		model.addAttribute("clavePendiente", usuario.debeCambiarClave());
		if (validacion.hasErrors()) {
			return VISTA;
		}
		try {
			servicioCuenta.cambiarClave(usuario.usuarioId(), solicitud);
		}
		catch (ClaveActualIncorrectaException e) {
			if (e.cuentaBloqueada()) {
				cierreSesion.logout(request, response, autenticacion);
				return "redirect:/login?bloqueada";
			}
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		cierreSesion.logout(request, response, autenticacion);
		return "redirect:/login?clave-cambiada";
	}
}
