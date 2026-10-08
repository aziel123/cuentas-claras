package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Descuentos y becas: Administración los pide (alumno por DNI → cuotas → revisar el antes y el después → enviar) y otra
 * persona los aprueba en la bandeja. Sin lógica: delega en {@link ServicioDescuentos}.
 */
@Controller
public class DescuentoController {

	private final ServicioDescuentos descuentos;

	public DescuentoController(ServicioDescuentos descuentos) {
		this.descuentos = descuentos;
	}

	@GetMapping("/descuentos")
	public String lista(Model model) {
		model.addAttribute("descuentos", descuentos.lista());
		return "descuentos/lista";
	}

	@GetMapping("/descuentos/nuevo")
	public String nuevo(@RequestParam(name = "dni", required = false) String dni, Model model,
			RedirectAttributes avisos) {
		try {
			model.addAttribute("solicitud", descuentos.prepararSolicitud(dni));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/descuentos";
		}
		model.addAttribute("tipos", TipoDescuento.values());
		model.addAttribute("modalidades", ModalidadDescuento.values());
		model.addAttribute("dni", dni);
		return "descuentos/formulario";
	}

	@PostMapping("/descuentos/revisar")
	public String revisar(@Valid DescuentoRequest pedido, BindingResult validacion,
			@RequestParam(name = "dni", required = false) String dni, Model model, RedirectAttributes avisos) {
		if (pedido == null || validacion.hasErrors()) {
			return volver(avisos, dni, validacion.hasErrors() ? Formularios.primerError(validacion)
					: "Completa el formulario.");
		}
		try {
			model.addAttribute("revision", descuentos.revisar(pedido));
			model.addAttribute("dni", dni);
			return "descuentos/revisar";
		}
		catch (ReglaNegocioException e) {
			return volver(avisos, dni, e.getMessage());
		}
	}

	@PostMapping("/descuentos")
	public String solicitar(@Valid DescuentoRequest pedido, BindingResult validacion,
			@RequestParam(name = "dni", required = false) String dni, RedirectAttributes avisos) {
		if (pedido == null || validacion.hasErrors()) {
			return volver(avisos, dni, validacion.hasErrors() ? Formularios.primerError(validacion)
					: "Completa el formulario.");
		}
		try {
			descuentos.solicitar(pedido);
			avisos.addFlashAttribute("exito", "Listo: el descuento quedó por aprobar. Promotoría o Dirección lo revisarán; "
					+ "hasta entonces se sigue cobrando el monto completo.");
			return "redirect:/descuentos";
		}
		catch (ReglaNegocioException e) {
			return volver(avisos, dni, e.getMessage());
		}
	}

	private static String volver(RedirectAttributes avisos, String dni, String error) {
		avisos.addFlashAttribute("error", error);
		return dni == null || dni.isBlank() ? "redirect:/descuentos" : "redirect:/descuentos/nuevo?dni=" + dni.strip();
	}
}
