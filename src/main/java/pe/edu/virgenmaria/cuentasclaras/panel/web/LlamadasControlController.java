package pe.edu.virgenmaria.cuentasclaras.panel.web;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.service.LlamadasControl;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * {@code GET /panel/llamadas}: la llamada de control de la semana; {@code POST /panel/llamadas/{familiaId}}: su resultado
 * (sprint 6, tanda 3). Promotoría y Dirección (Dirección registra solo la semana que Promotoría se la delega:
 * {@code POST /panel/llamadas/delegar}, S6-M2). Sin lógica: delega en {@link LlamadasControl} (que exige el rol, que la
 * familia esté en la muestra de la semana y que la plaza siga abierta).
 */
@Controller
public class LlamadasControlController {

	private final LlamadasControl llamadas;

	public LlamadasControlController(LlamadasControl llamadas) {
		this.llamadas = llamadas;
	}

	@GetMapping("/panel/llamadas")
	public String ver(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("llamadas", llamadas.deEstaSemana());
		model.addAttribute("volver", usuario != null && usuario.roles().contains(Rol.PROMOTOR) ? "/panel" : "/inicio");
		return "panel/llamadas";
	}

	/** S6-M2: Promotoría delega a Dirección las llamadas de esta semana (el servicio exige Promotoría). */
	@PostMapping("/panel/llamadas/delegar")
	public String delegar(RedirectAttributes avisos) {
		try {
			llamadas.delegarADireccion();
			avisos.addFlashAttribute("exito", "Listo: esta semana Dirección también puede registrar las llamadas. Te "
					+ "avisaremos de cada una.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/panel/llamadas";
	}

	@PostMapping("/panel/llamadas/{familiaId:\\d+}")
	public String registrar(@PathVariable Long familiaId, @Valid @ModelAttribute LlamadaRequest solicitud,
			BindingResult errores, RedirectAttributes avisos) {
		if (errores.hasErrors()) {
			avisos.addFlashAttribute("error", errores.getAllErrors().getFirst().getDefaultMessage());
			return "redirect:/panel/llamadas";
		}
		try {
			llamadas.registrar(familiaId, solicitud);
			avisos.addFlashAttribute("exito", "Listo: registraste el resultado de la llamada. No se puede cambiar.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		catch (DataIntegrityViolationException e) {
			avisos.addFlashAttribute("error", "Otra persona ya registró la llamada a esta familia esta semana.");
		}
		return "redirect:/panel/llamadas";
	}
}
