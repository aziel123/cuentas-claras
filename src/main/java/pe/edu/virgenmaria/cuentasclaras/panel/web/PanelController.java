package pe.edu.virgenmaria.cuentasclaras.panel.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ResumenesDiarios;

/**
 * {@code GET /panel}: el panel de Promotoría para el celular; {@code GET /panel/resumenes}: los resúmenes diarios enviados
 * con «cambió desde que se envió» (tanda 2). Sin lógica: delega en los servicios (que exigen el rol).
 */
@Controller
public class PanelController {

	private final PanelPromotoria panel;

	private final ResumenesDiarios resumenes;

	public PanelController(PanelPromotoria panel, ResumenesDiarios resumenes) {
		this.panel = panel;
		this.resumenes = resumenes;
	}

	@GetMapping("/panel")
	public String inicio(Model model) {
		model.addAttribute("panel", panel.ver());
		return "panel/inicio";
	}

	@GetMapping("/panel/resumenes")
	public String resumenes(Model model) {
		model.addAttribute("resumenes", resumenes.recientes());
		return "panel/resumenes";
	}
}
