package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoFichaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsentimientosAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Sprint 9, tanda 2 (E38): Administración o Dirección registra el consentimiento que la familia firmó en la ficha de
 * matrícula, con el código de la ficha. Sin lógica: delega en {@link ConsentimientosAviso}.
 */
@Controller
@RequestMapping("/alumnos/consentimientos")
public class ConsentimientosFichaController {

	private static final String VISTA = "alumnos/consentimientos";

	private final ConsentimientosAviso consentimientos;

	public ConsentimientosFichaController(ConsentimientosAviso consentimientos) {
		this.consentimientos = consentimientos;
	}

	@GetMapping
	public String ver(@RequestParam(name = "apoderado", required = false) Long apoderado, Model model) {
		model.addAttribute("ficha", new ConsentimientoFichaRequest(apoderado, Finalidad.ASISTENCIA, Canal.WHATSAPP, null));
		model.addAttribute("canales", Canal.values());
		return VISTA;
	}

	@PostMapping
	public String registrar(@Valid @ModelAttribute("ficha") ConsentimientoFichaRequest pedido, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		model.addAttribute("canales", Canal.values());
		if (validacion.hasErrors()) {
			return VISTA;
		}
		try {
			consentimientos.registrarDesdeFicha(pedido);
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", "Listo: el consentimiento quedó registrado con el código de la ficha. La familia "
				+ "lo ve y lo puede retirar en su portal.");
		return "redirect:/alumnos/consentimientos";
	}
}
