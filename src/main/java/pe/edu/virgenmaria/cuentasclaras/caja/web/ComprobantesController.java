package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioEstadoCuenta;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioReemision;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ConsultaComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Bandeja de envíos al OSE (sprint 4): rechazados y por vencer el plazo legal primero. Administración reemite un
 * rechazado (con número nuevo) o adelanta un reintento. Sin lógica: delega en los servicios.
 */
@Controller
public class ComprobantesController {

	private final ConsultaComprobantes consulta;

	private final ServicioReemision reemision;

	private final ServicioEstadoCuenta estadoCuenta;

	public ComprobantesController(ConsultaComprobantes consulta, ServicioReemision reemision,
			ServicioEstadoCuenta estadoCuenta) {
		this.consulta = consulta;
		this.reemision = reemision;
		this.estadoCuenta = estadoCuenta;
	}

	@GetMapping("/comprobantes")
	public String bandeja(Model model) {
		model.addAttribute("bandeja", consulta.bandeja());
		return "comprobantes/bandeja";
	}

	@GetMapping("/comprobantes/{id:\\d+}")
	public String comprobante(@PathVariable Long id, Model model) {
		model.addAttribute("comprobante", estadoCuenta.comprobante(id));
		model.addAttribute("imprimirAlAbrir", false);
		model.addAttribute("volverUrl", "/comprobantes");
		model.addAttribute("volverTexto", "Volver a la bandeja");
		model.addAttribute("nuevoCobro", false);
		return "caja/comprobante";
	}

	@PostMapping("/comprobantes/{id:\\d+}/reemitir")
	public String reemitir(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			Long nuevo = reemision.reemitir(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: se reemitió el comprobante con un número nuevo (id " + nuevo
					+ "). Se envía al OSE en un momento.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/comprobantes";
	}

	@PostMapping("/comprobantes/{id:\\d+}/reintentar")
	public String reintentar(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			avisos.addFlashAttribute("exito", "Listo: " + consulta.adelantarReintento(id) + " se volverá a enviar en el "
					+ "próximo minuto.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/comprobantes";
	}
}
