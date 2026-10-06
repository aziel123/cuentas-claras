package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.AccesoCreado;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Cuenta en línea del apoderado (sprint 4): «Dar acceso en línea», «Restablecer acceso» (Promotoría) y «Quitar acceso»
 * desde su ficha. Correcciones del sprint 4 (S4-M2): no se muestra ninguna clave, sino un enlace de un solo uso que el
 * colegio entrega en persona o por un canal del titular (no pasa por la sesión). Sin lógica: delega en
 * {@link ServicioAccesoApoderados}.
 */
@Controller
@RequestMapping("/alumnos/apoderados/{id:\\d+}/acceso")
public class AccesoApoderadoController {

	private final ServicioAccesoApoderados accesos;

	public AccesoApoderadoController(ServicioAccesoApoderados accesos) {
		this.accesos = accesos;
	}

	@PostMapping
	public String darAcceso(@PathVariable Long id, Model model, RedirectAttributes avisos) {
		try {
			return mostrar(accesos.darAcceso(id), id, false, model);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/alumnos/apoderados/" + id;
		}
	}

	/** S4-M2: solo Promotoría (desde la familia o la ficha del apoderado). */
	@PostMapping("/restablecer")
	public String restablecer(@PathVariable Long id, @RequestParam(required = false) Long familiaId, Model model,
			RedirectAttributes avisos) {
		String volver = familiaId == null ? "/alumnos/apoderados/" + id : "/alumnos/familias/" + familiaId;
		try {
			String vista = mostrar(accesos.restablecerAcceso(id), id, true, model);
			model.addAttribute("volverA", volver);
			return vista;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + volver;
		}
	}

	private static String mostrar(AccesoCreado creado, Long id, boolean restablecido, Model model) {
		model.addAttribute("creado", creado);
		model.addAttribute("enlace", ServletUriComponentsBuilder.fromCurrentContextPath().path(creado.enlace())
				.toUriString());
		model.addAttribute("restablecido", restablecido);
		model.addAttribute("apoderadoId", id);
		model.addAttribute("volverA", "/alumnos/apoderados/" + id);
		return "alumnos/acceso-creado";
	}

	@PostMapping("/quitar")
	public String quitarAcceso(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			accesos.quitarAcceso(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: el apoderado ya no puede ingresar en línea. Sus sesiones se cerraron.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/alumnos/apoderados/" + id;
	}
}
