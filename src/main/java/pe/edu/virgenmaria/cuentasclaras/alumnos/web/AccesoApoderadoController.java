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

/**
 * Cuenta en línea del apoderado (sprint 4): «Dar acceso en línea» y «Quitar acceso» desde su ficha. La clave temporal se
 * muestra UNA vez (no pasa por la sesión) y se entrega en persona. Sin lógica: delega en {@link ServicioAccesoApoderados}.
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
			model.addAttribute("creado", accesos.darAcceso(id));
			model.addAttribute("apoderadoId", id);
			return "alumnos/acceso-creado";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/alumnos/apoderados/" + id;
		}
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
