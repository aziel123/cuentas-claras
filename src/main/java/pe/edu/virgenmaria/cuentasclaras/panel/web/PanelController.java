package pe.edu.virgenmaria.cuentasclaras.panel.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria;

/** {@code GET /panel}: el panel de Promotoría para el celular. Sin lógica: delega en {@link PanelPromotoria}. */
@Controller
public class PanelController {

	private final PanelPromotoria panel;

	public PanelController(PanelPromotoria panel) {
		this.panel = panel;
	}

	@GetMapping("/panel")
	public String inicio(Model model) {
		model.addAttribute("panel", panel.ver());
		return "panel/inicio";
	}
}
