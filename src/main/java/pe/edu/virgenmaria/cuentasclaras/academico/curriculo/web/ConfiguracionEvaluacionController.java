package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.web;

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
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ConfiguracionEvaluacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ConfiguracionEvaluacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Sprint 10, tanda 1 (sección 8.3): «Evaluación del año». La ven Dirección, Administración y Promotoría; la guarda SOLO
 * Dirección con su firma (lo exige el servicio: 403 para los demás). Sin lógica: valida el formulario y delega.
 */
@Controller
@RequestMapping("/academico/evaluacion/configuracion")
public class ConfiguracionEvaluacionController {

	private static final String VISTA = "academico/evaluacion";

	private final ConfiguracionesEvaluacion configuraciones;

	public ConfiguracionEvaluacionController(ConfiguracionesEvaluacion configuraciones) {
		this.configuraciones = configuraciones;
	}

	@GetMapping
	public String ver(@RequestParam(name = "anio", required = false) Long anio, Model model) {
		ConfiguracionEvaluacionVista vista = configuraciones.vista(anio);
		preparar(model, vista, ConfiguracionEvaluacionRequest.de(vista));
		return VISTA;
	}

	@PostMapping
	public String guardar(@Valid @ModelAttribute("configuracion") ConfiguracionEvaluacionRequest pedido,
			BindingResult validacion, Model model, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			preparar(model, configuraciones.vista(pedido.anioId()), pedido);
			return VISTA;
		}
		boolean guardo;
		try {
			guardo = configuraciones.guardar(pedido.anioId(), pedido.valores());
		}
		catch (ReglaNegocioException e) {
			preparar(model, configuraciones.vista(pedido.anioId()), pedido);
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", guardo ? "Listo: la evaluación del año quedó guardada con tu firma y en la "
				+ "bitácora." : "No había cambios que guardar.");
		return "redirect:/academico/evaluacion/configuracion?anio=" + pedido.anioId();
	}

	private static void preparar(Model model, ConfiguracionEvaluacionVista vista, ConfiguracionEvaluacionRequest pedido) {
		model.addAttribute("vista", vista);
		model.addAttribute("configuracion", pedido);
		model.addAttribute("tipos", TipoPeriodo.values());
	}
}
