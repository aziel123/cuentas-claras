package pe.edu.virgenmaria.cuentasclaras.academico.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.service.CompletarAreas;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Sprint 10, tanda 2 (sección 4 del brief): «Completar áreas» de los docentes del sprint 9. Muestra la propuesta de reemplazo
 * ya llenada (una asignación o todas las de una sección) y la propone con lo que la persona confirmó. Sin lógica: delega en
 * {@link CompletarAreas}; la aprueba otra persona en la bandeja.
 */
@Controller
@RequestMapping("/academico/asignaciones/completar")
public class CompletarAreasController {

	private final CompletarAreas completar;

	public CompletarAreasController(CompletarAreas completar) {
		this.completar = completar;
	}

	@GetMapping
	public String formulario(@RequestParam(name = "asignacion", required = false) Long asignacionId,
			@RequestParam(name = "seccion", required = false) Long seccionId, Model model) {
		model.addAttribute("vista", completar.vista(asignacionId, seccionId));
		return "academico/completar-areas";
	}

	@PostMapping
	public String proponer(@RequestParam MultiValueMap<String, String> formulario,
			@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			int creadas = completar.proponer(AreasPorAsignacion.deFormulario(formulario), motivo);
			avisos.addFlashAttribute("exito", "Listo: propusiste " + creadas + (creadas == 1 ? " asignación" : " asignaciones")
					+ " con sus áreas. Otra persona las aprueba en la bandeja; al aprobar, reemplazan a las anteriores.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/academico/asignaciones";
	}
}
