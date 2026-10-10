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
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.List;

/**
 * Bandeja de aprobaciones. Sin lógica: delega en {@link BandejaAprobaciones} (que exige el rol). Sprint 6, tanda 2
 * (decisión 72): la vista para el celular ({@code ?vista=movil}) y el detalle de una solicitud ({@code GET /{id}}) usan la
 * MISMA bandeja y los mismos POST con CSRF: ningún enlace (GET) aprueba nada.
 */
@Controller
@RequestMapping("/aprobaciones")
public class AprobacionesController {

	static final String MOVIL = "movil";

	private final BandejaAprobaciones bandeja;

	public AprobacionesController(BandejaAprobaciones bandeja) {
		this.bandeja = bandeja;
	}

	@GetMapping
	public String bandeja(@RequestParam(required = false) String vista, Model model) {
		model.addAttribute("bandeja", bandeja.bandeja());
		return MOVIL.equals(vista) ? "aprobaciones/movil" : "aprobaciones/bandeja";
	}

	@GetMapping("/{id:\\d+}")
	@RegistraAcceso(TipoAcceso.APROBACION_CONTACTO)
	public String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("s", bandeja.detalle(id));
		// Ley 29733: si es el cambio de contacto de un apoderado, queda registrado quién lo abrió (lo decide privacidad).
		AccesoMostrado.solicitud(id);
		return "aprobaciones/detalle";
	}

	@PostMapping("/{id:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, @RequestParam(required = false) String comentario,
			@RequestParam(defaultValue = "false") boolean hablo, @RequestParam(required = false) List<String> telefonos,
			@RequestParam(required = false) String volver, RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.aprobar(id, comentario, hablo, telefonos),
				"Listo: aprobaste la solicitud y el cambio se aplicó.", volver);
	}

	@PostMapping("/{id:\\d+}/rechazar")
	public String rechazar(@PathVariable Long id, @RequestParam(required = false) String motivo,
			@RequestParam(required = false) String volver, RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.rechazar(id, motivo), "Listo: rechazaste la solicitud. No se cambió nada.",
				volver);
	}

	/** Vuelve a la vista del celular solo si se pidió; cualquier otro valor vuelve a la bandeja (sin redirección abierta). */
	private static String resolver(RedirectAttributes avisos, Runnable accion, String exito, String volver) {
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
		return MOVIL.equals(volver) ? "redirect:/aprobaciones?vista=" + MOVIL : "redirect:/aprobaciones";
	}
}
