package pe.edu.virgenmaria.cuentasclaras.academico.notas.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExonerarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.Exoneraciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

/**
 * Sprint 10, tanda 2: el avance del registro (Dirección y Promotoría, sin alumnos), las correcciones del periodo y el cierre
 * con su resumen (Dirección), y las exoneraciones (Dirección con su firma). Sin lógica: delega en {@link AvanceRegistro} y
 * {@link Exoneraciones}, que exigen el rol (segunda capa, después de la matriz).
 */
@Controller
public class AvanceNotasController {

	private final AvanceRegistro avance;

	private final Exoneraciones exoneraciones;

	public AvanceNotasController(AvanceRegistro avance, Exoneraciones exoneraciones) {
		this.avance = avance;
		this.exoneraciones = exoneraciones;
	}

	@GetMapping("/academico/notas/avance")
	public String avance(@RequestParam(name = "periodo", required = false) Long periodoId, Model model) {
		model.addAttribute("vista", avance.avance(periodoId));
		return "academico/notas/avance";
	}

	@GetMapping("/academico/notas/avance/correcciones")
	public String correcciones(@RequestParam(name = "periodo") long periodoId, Model model) {
		model.addAttribute("vista", avance.avance(periodoId));
		model.addAttribute("correcciones", avance.correccionesDelPeriodo(periodoId));
		return "academico/notas/correcciones";
	}

	@GetMapping("/academico/periodos/{id:\\d+}/cierre")
	public String cierre(@PathVariable long id, Model model) {
		model.addAttribute("vista", avance.resumenCierre(id));
		return "academico/notas/cierre";
	}

	@PostMapping("/academico/periodos/{id:\\d+}/cierre")
	public String cerrar(@PathVariable long id, RedirectAttributes avisos) {
		try {
			avance.cerrar(id);
			avisos.addFlashAttribute("exito", "Listo: el periodo quedó cerrado con tu firma. No se reabre; lo que faltaba "
					+ "va por nota tardía.");
			return "redirect:/academico/periodos";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/academico/periodos/" + id + "/cierre";
		}
	}

	@GetMapping("/academico/notas/exoneraciones")
	@RegistraAcceso(TipoAcceso.NOTAS_SECCION)
	public String exoneraciones(@RequestParam(name = "seccion", required = false) Long seccionId, Model model) {
		model.addAttribute("vista", exoneraciones.vista(seccionId));
		if (!model.containsAttribute("pedido")) {
			model.addAttribute("pedido", ExonerarRequest.vacio());
		}
		return "academico/notas/exoneraciones";
	}

	@PostMapping("/academico/notas/exoneraciones")
	public String exonerar(@Valid @ModelAttribute("pedido") ExonerarRequest pedido, BindingResult errores,
			@RequestParam(name = "seccion", required = false) Long seccionId, Model model, RedirectAttributes avisos) {
		if (errores.hasErrors()) {
			model.addAttribute("vista", exoneraciones.vista(seccionId));
			return "academico/notas/exoneraciones";
		}
		try {
			exoneraciones.exonerar(pedido);
			avisos.addFlashAttribute("exito", "Listo: registraste la exoneración con tu firma. Rige desde el periodo abierto.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return seccionId == null ? "redirect:/academico/notas/exoneraciones"
				: "redirect:/academico/notas/exoneraciones?seccion=" + seccionId;
	}
}
