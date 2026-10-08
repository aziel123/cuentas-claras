package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.ContactoPersonalRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Sprint 6, tanda 2 (P6): pedir el cambio del celular o el correo del personal. {@code /cuenta/contacto}: el titular, el
 * suyo; {@code POST /usuarios/{id}/contacto}: Promotoría, el de otra persona. Sin lógica: delega en
 * {@link ServicioContactoPersonal} (que exige el rol y crea la solicitud que aprueba otra persona).
 */
@Controller
public class ContactoPersonalController {

	private static final String VISTA = "cuenta/contacto";

	private final ServicioContactoPersonal servicio;

	public ContactoPersonalController(ServicioContactoPersonal servicio) {
		this.servicio = servicio;
	}

	@GetMapping(ModuloApp.RUTA_MI_CONTACTO)
	public String formulario(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("contacto", servicio.actual(usuario.usuarioId()));
		if (!model.containsAttribute("solicitud")) {
			model.addAttribute("solicitud", ContactoPersonalRequest.vacio());
		}
		return VISTA;
	}

	@PostMapping(ModuloApp.RUTA_MI_CONTACTO)
	public String solicitar(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @ModelAttribute("solicitud") ContactoPersonalRequest solicitud, BindingResult validacion, Model model,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			model.addAttribute("contacto", servicio.actual(usuario.usuarioId()));
			return VISTA;
		}
		try {
			servicio.solicitar(usuario.usuarioId(), solicitud.telefono(), solicitud.correo(), solicitud.motivo());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("contacto", servicio.actual(usuario.usuarioId()));
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", "Listo: tu pedido quedó registrado. Lo aprueba otra persona de Promotoría o "
				+ "Dirección y te avisaremos al contacto actual.");
		return "redirect:" + ModuloApp.RUTA_MI_CONTACTO;
	}

	@PostMapping("/usuarios/{id:\\d+}/contacto")
	public String solicitarParaOtro(@PathVariable Long id, @Valid @ModelAttribute ContactoPersonalRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", validacion.getAllErrors().getFirst().getDefaultMessage());
			return "redirect:/usuarios/" + id;
		}
		try {
			servicio.solicitar(id, solicitud.telefono(), solicitud.correo(), solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: el cambio de contacto quedó como solicitud. Lo aprueba otra persona "
					+ "de Promotoría o Dirección (nunca el titular) y se avisará al contacto anterior.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/usuarios/" + id;
	}
}
