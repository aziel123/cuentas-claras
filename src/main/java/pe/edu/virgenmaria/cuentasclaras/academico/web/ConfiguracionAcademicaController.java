package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ConfiguracionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConfiguracionesAcademicas;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Sprint 9, tanda 2 (A6, E15): las horas y plazos de la asistencia, solo Dirección y con su firma (lo exige el servicio).
 * El cambio queda resaltado en la bitácora con el antes y el después.
 */
@Controller
@RequestMapping("/academico/configuracion")
public class ConfiguracionAcademicaController {

	private static final String VISTA = "academico/configuracion";

	private final ConfiguracionesAcademicas configuraciones;

	public ConfiguracionAcademicaController(ConfiguracionesAcademicas configuraciones) {
		this.configuraciones = configuraciones;
	}

	@GetMapping
	public String ver(Model model) {
		var vista = configuraciones.vista();
		model.addAttribute("vista", vista);
		model.addAttribute("configuracion", ConfiguracionRequest.de(vista));
		return VISTA;
	}

	@PostMapping
	public String cambiar(@Valid @ModelAttribute("configuracion") ConfiguracionRequest pedido, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			model.addAttribute("vista", configuraciones.vista());
			return VISTA;
		}
		try {
			configuraciones.cambiar(pedido.valores());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("vista", configuraciones.vista());
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", "Listo: las horas quedaron cambiadas y el cambio quedó en la bitácora.");
		return "redirect:/academico/configuracion";
	}
}
