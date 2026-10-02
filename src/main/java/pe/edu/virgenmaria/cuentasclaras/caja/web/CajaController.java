package pe.edu.virgenmaria.cuentasclaras.caja.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Caja: buscar → elegir cuotas y medio → revisar (el sistema calcula el total) → cobrar → confirmación e impresión de la
 * boleta. Sin lógica: delega en {@link ServicioCobro}. El formulario de cobro no tiene campo de monto; si alguien lo
 * agrega a mano, se ignora.
 */
@Controller
public class CajaController {

	private final ServicioCobro cobro;

	public CajaController(ServicioCobro cobro) {
		this.cobro = cobro;
	}

	@GetMapping("/caja")
	public String buscar(@RequestParam(name = "q", required = false) String texto, Model model) {
		model.addAttribute("busqueda", cobro.buscar(texto));
		return "caja/buscar";
	}

	@GetMapping("/caja/familias/{id:\\d+}")
	public String familia(@PathVariable Long id, Model model) {
		model.addAttribute("cuenta", cobro.cuentaDeFamilia(id));
		return "caja/familia";
	}

	@PostMapping("/caja/familias/{id:\\d+}/revisar")
	public String revisar(@PathVariable Long id, @Valid SeleccionCobroRequest seleccion, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		if (seleccion == null || validacion.hasErrors()) {
			avisos.addFlashAttribute("error", validacion.hasErrors() ? Formularios.primerError(validacion)
					: "Elige al menos una cuota y el medio de pago.");
			return "redirect:/caja/familias/" + id;
		}
		try {
			model.addAttribute("revision", cobro.revisar(id, seleccion, null));
			return "caja/revisar";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/caja/familias/" + id;
		}
	}

	@PostMapping("/caja/pagos")
	public String cobrar(@Valid CobroRequest solicitud, BindingResult validacion, Model model,
			RedirectAttributes avisos) {
		if (solicitud == null || validacion.hasErrors()) {
			return volverARevisar(solicitud, validacion.hasErrors() ? Formularios.primerError(validacion)
					: "Vuelve a revisar el cobro.", model, avisos);
		}
		try {
			return "redirect:/caja/pagos/" + cobro.cobrar(solicitud);
		}
		catch (ReglaNegocioException e) {
			return volverARevisar(solicitud, e.getMessage(), model, avisos);
		}
	}

	/** Vuelve a la revisión con el error y lo que ya escribió la cajera (misma clave: no se duplica). */
	private String volverARevisar(CobroRequest solicitud, String error, Model model, RedirectAttributes avisos) {
		if (solicitud == null || solicitud.familiaId() == null) {
			avisos.addFlashAttribute("error", error);
			return "redirect:/caja";
		}
		if (solicitud.cuotaIds() == null || solicitud.cuotaIds().isEmpty() || solicitud.medio() == null) {
			avisos.addFlashAttribute("error", error);
			return "redirect:/caja/familias/" + solicitud.familiaId();
		}
		try {
			model.addAttribute("revision", cobro.revisar(solicitud.familiaId(),
					new SeleccionCobroRequest(solicitud.cuotaIds(), solicitud.medio()), solicitud.clave()));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", error);
			return "redirect:/caja/familias/" + solicitud.familiaId();
		}
		model.addAttribute("datos", solicitud);
		model.addAttribute("error", error);
		return "caja/revisar";
	}

	@GetMapping("/caja/pagos/{id:\\d+}")
	public String confirmacion(@PathVariable Long id, Model model) {
		model.addAttribute("pago", cobro.confirmacion(id));
		return "caja/confirmacion";
	}

	@GetMapping("/caja/pagos/{id:\\d+}/comprobante")
	public String comprobante(@PathVariable Long id,
			@RequestParam(name = "imprimir", required = false, defaultValue = "false") boolean imprimir, Model model) {
		model.addAttribute("comprobante", cobro.imprimible(id));
		model.addAttribute("imprimirAlAbrir", imprimir);
		return "caja/comprobante";
	}

	@GetMapping("/caja/hoy")
	public String hoy(Model model) {
		model.addAttribute("dia", cobro.pagosDelDia());
		return "caja/hoy";
	}
}
