package pe.edu.virgenmaria.cuentasclaras.academico.notas.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.NotasDeSeccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

/**
 * Sprint 10, tanda 2 (secciones 3.5 y 3.6): las notas de toda una sección, de solo lectura (su tutor y Dirección), y la
 * solicitud de corrección de una nota de un periodo CERRADO (el docente del área o Dirección; la aprueba otra persona). Sin
 * lógica: delega en {@link NotasDeSeccion} y {@link CorreccionesNota}, que exigen quién puede (404 si no).
 */
@Controller
@RequestMapping("/academico/notas")
public class NotasSeccionController {

	private final NotasDeSeccion notas;

	private final CorreccionesNota correcciones;

	public NotasSeccionController(NotasDeSeccion notas, CorreccionesNota correcciones) {
		this.notas = notas;
		this.correcciones = correcciones;
	}

	@GetMapping("/secciones")
	public String secciones(Model model) {
		model.addAttribute("secciones", notas.secciones());
		return "academico/notas/secciones";
	}

	@GetMapping("/secciones/{seccionId:\\d+}")
	@RegistraAcceso(TipoAcceso.NOTAS_SECCION)
	public String seccion(@PathVariable long seccionId, @RequestParam(name = "periodo", required = false) Long periodoId,
			Model model) {
		model.addAttribute("vista", notas.vista(seccionId, periodoId));
		return "academico/notas/seccion";
	}

	@GetMapping("/correcciones/{calificacionId:\\d+}")
	@RegistraAcceso(TipoAcceso.NOTAS_SECCION)
	public String formulario(@PathVariable long calificacionId, Model model) {
		model.addAttribute("f", correcciones.formulario(calificacionId));
		return "academico/notas/correccion";
	}

	@PostMapping("/correcciones/{calificacionId:\\d+}")
	public String pedir(@PathVariable long calificacionId, @RequestParam(required = false) String nivel,
			@RequestParam(required = false) String conclusion, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			correcciones.pedir(calificacionId, nivel, conclusion, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste la corrección. La nota cambia cuando otra persona de "
					+ "Dirección o Promotoría la apruebe.");
			return "redirect:/academico/notas/correcciones/" + calificacionId;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/academico/notas/correcciones/" + calificacionId;
		}
	}
}
