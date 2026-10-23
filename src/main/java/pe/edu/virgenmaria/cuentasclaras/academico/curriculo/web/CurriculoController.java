package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;

/**
 * Sprint 10, tanda 1 (sección 8.3): el catálogo nacional del currículo, de SOLO LECTURA, para todo el personal. Sin lógica:
 * lo arma {@link Catalogo}. No tiene datos personales.
 */
@Controller
@RequestMapping("/academico/curriculo")
public class CurriculoController {

	private static final String VISTA = "academico/curriculo";

	private final Catalogo catalogo;

	public CurriculoController(Catalogo catalogo) {
		this.catalogo = catalogo;
	}

	@GetMapping
	public String ver(Model model) {
		model.addAttribute("vista", catalogo.vistaCatalogo(null));
		return VISTA;
	}

	@GetMapping("/{codigo:[A-Z0-9.-]{4,40}}")
	public String version(@PathVariable String codigo, Model model) {
		model.addAttribute("vista", catalogo.vistaCatalogo(codigo));
		return VISTA;
	}
}
