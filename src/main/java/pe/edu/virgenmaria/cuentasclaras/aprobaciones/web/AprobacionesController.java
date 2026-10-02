package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** Bandeja de aprobaciones. Sin lógica: delega en {@link BandejaAprobaciones} (que exige el rol). */
@Controller
@RequestMapping("/aprobaciones")
public class AprobacionesController {

	private final BandejaAprobaciones bandeja;

	public AprobacionesController(BandejaAprobaciones bandeja) {
		this.bandeja = bandeja;
	}

	@GetMapping
	public String bandeja(Model model) {
		model.addAttribute("bandeja", bandeja.bandeja());
		return "aprobaciones/bandeja";
	}

	@PostMapping("/{id:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, @RequestParam(required = false) String comentario,
			RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.aprobar(id, comentario), "Listo: aprobaste la solicitud y el cambio se aplicó.");
	}

	@PostMapping("/{id:\\d+}/rechazar")
	public String rechazar(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.rechazar(id, motivo), "Listo: rechazaste la solicitud. No se cambió nada.");
	}

	private static String resolver(RedirectAttributes avisos, Runnable accion, String exito) {
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		catch (OptimisticLockingFailureException e) {
			avisos.addFlashAttribute("error", "Otra persona cambió esto al mismo tiempo; revísalo de nuevo.");
		}
		return "redirect:/aprobaciones";
	}
}
