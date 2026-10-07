package pe.edu.virgenmaria.cuentasclaras.pasarela.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ComprobantesDeFamilia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.EstadoOrdenVista;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;

/**
 * El portal del apoderado (celular primero): estado de cuenta de SU familia → revisar → crear la orden (PRG) → nuestra
 * página con un ENLACE (GET) a la página segura de la pasarela. No se redirige a otro dominio después de un POST (la CSP
 * {@code form-action 'self'} lo bloquearía) y volver de la pasarela no marca nada como pagado. Sin lógica: delega en
 * {@link ServicioPagoEnLinea}. Sprint 5: el inicio del portal ({@code GET /familia}) pasó a {@code familias}.
 */
@Controller
public class PagoEnLineaController {

	/** Segundos entre recargas mientras se confirma un pago (meta refresh: sin JavaScript en línea). */
	static final int RECARGA_SEGUNDOS = 5;

	private final ServicioPagoEnLinea pagos;

	private final ComprobantesDeFamilia comprobantes;

	public PagoEnLineaController(ServicioPagoEnLinea pagos, ComprobantesDeFamilia comprobantes) {
		this.pagos = pagos;
		this.comprobantes = comprobantes;
	}

	@PostMapping("/familia/pagar/revisar")
	public String revisar(@Valid SeleccionPagoRequest seleccion, BindingResult validacion, Model model,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/familia";
		}
		try {
			model.addAttribute("revision", pagos.revisar(seleccion));
			return "familia/revisar";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/familia";
		}
	}

	@PostMapping("/familia/pagar")
	public String pagar(@Valid PagoEnLineaRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/familia";
		}
		try {
			return "redirect:/familia/pagos/" + pagos.crearOrden(pedido);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/familia";
		}
	}

	@GetMapping("/familia/pagos/{referencia:[0-9a-f-]{36}}")
	public String orden(@PathVariable String referencia, Model model) {
		EstadoOrdenVista orden = pagos.estado(referencia);
		model.addAttribute("orden", orden);
		if (orden.enCurso()) {
			model.addAttribute("recargarCada", RECARGA_SEGUNDOS);
		}
		return "familia/orden";
	}

	@GetMapping("/familia/comprobantes/{id:\\d+}")
	public String comprobante(@PathVariable Long id, Model model) {
		model.addAttribute("comprobante", comprobantes.imprimible(id));
		model.addAttribute("imprimirAlAbrir", false);
		model.addAttribute("volverUrl", "/familia");
		model.addAttribute("volverTexto", "Volver a mi familia");
		model.addAttribute("nuevoCobro", false);
		return "caja/comprobante";
	}
}
