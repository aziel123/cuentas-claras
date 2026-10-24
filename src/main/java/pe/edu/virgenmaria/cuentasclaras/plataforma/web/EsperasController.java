package pe.edu.virgenmaria.cuentasclaras.plataforma.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FrenosOperador;

/**
 * Sprint 11, tanda 1c (decisiones 218, 221 y 223): lo que el operador hace por la Promotoría que aplica directo. Frena lo
 * que espera 24 horas (nunca lo aplica) y propone el contacto de una cuenta de Promotoría o Dirección que lo perdió (lo
 * aplica OTRO operador). Sin lógica: delega en {@link FrenosOperador} y {@link AltaColegio}.
 */
@Controller
@RequestMapping("/plataforma")
public class EsperasController {

	private final FrenosOperador frenos;

	private final AltaColegio alta;

	public EsperasController(FrenosOperador frenos, AltaColegio alta) {
		this.frenos = frenos;
		this.alta = alta;
	}

	@GetMapping("/esperas")
	public String esperas(Model model) {
		model.addAttribute("esperas", frenos.esperas());
		return "plataforma/esperas";
	}

	@PostMapping("/colegios/{id:\\d+}/esperas/conectores/{conector:\\d+}/frenar")
	public String frenarConector(@PathVariable Long id, @PathVariable Long conector,
			@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			frenos.frenarConector(id, conector, motivo);
			avisos.addFlashAttribute("exito", "Listo: lo frenaste. No entra en vigencia; se avisó a todas las Promotorías y al "
					+ "correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/esperas";
	}

	@PostMapping("/colegios/{id:\\d+}/esperas/solicitudes/{solicitud:\\d+}/frenar")
	public String frenarSolicitud(@PathVariable Long id, @PathVariable Long solicitud,
			@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			frenos.frenarSolicitud(id, solicitud, motivo);
			avisos.addFlashAttribute("exito", "Listo: lo frenaste. No se aplica; se avisó a todas las Promotorías, a la persona "
					+ "afectada y al correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/esperas";
	}

	/** A1c-1: el contacto de una cuenta directiva que lo perdió (lo propone un operador y lo aplica otro). */
	@PostMapping("/colegios/{id:\\d+}/contacto-directivo")
	public String contactoDirectivo(@PathVariable Long id, @RequestParam(required = false) String cuenta,
			@RequestParam(required = false) String telefonoWhatsapp, @RequestParam(required = false) String correo,
			@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			alta.proponerContactoDirectivo(id, cuenta, new ContactoTitularRequest(telefonoWhatsapp, correo, motivo));
			avisos.addFlashAttribute("exito", "Listo: propusiste el cambio del contacto de " + cuenta + ". Lo aplica otro "
					+ "operador, con su propia sesión; se avisa al contacto anterior.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id;
	}
}
