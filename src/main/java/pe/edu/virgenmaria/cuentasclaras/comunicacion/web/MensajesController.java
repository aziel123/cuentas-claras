package pe.edu.virgenmaria.cuentasclaras.comunicacion.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ConsultaMensajes;

/**
 * Bandeja de envíos del personal ({@code /mensajes}, pantalla 7) e historial de la familia en el portal
 * ({@code /familia/mensajes}, pantalla 4). Sin lógica: delega en {@link ConsultaMensajes}. Nadie edita un destino ni un
 * texto: Administración solo adelanta el reintento de un pendiente.
 */
@Controller
public class MensajesController {

	private final ConsultaMensajes consulta;

	public MensajesController(ConsultaMensajes consulta) {
		this.consulta = consulta;
	}

	@GetMapping("/mensajes")
	public String bandeja(Model model) {
		model.addAttribute("bandeja", consulta.bandeja());
		return "mensajes/bandeja";
	}

	@PostMapping("/mensajes/{id:\\d+}/reintentar")
	public String reintentar(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			consulta.reintentar(id);
			avisos.addFlashAttribute("exito", "Listo: el mensaje se volverá a intentar en el próximo envío.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/mensajes";
	}

	@GetMapping("/familia/mensajes")
	public String historial(Model model) {
		model.addAttribute("mensajes", consulta.historialDeMiFamilia());
		return "familia/mensajes";
	}
}
