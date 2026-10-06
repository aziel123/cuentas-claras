package pe.edu.virgenmaria.cuentasclaras.pasarela.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.AplicacionIngresoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ConsultaPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.DevolucionesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioIngresosPorRevisar;

/**
 * Pagos en línea para el personal: lista (por revisar primero) y detalle. Administración pide aplicar o devolver un
 * ingreso por revisar y ejecuta la devolución aprobada por otra persona. Sin lógica: delega en los servicios.
 */
@Controller
public class PagosEnLineaController {

	private final ConsultaPagosEnLinea consulta;

	private final ServicioIngresosPorRevisar ingresos;

	private final DevolucionesPasarela devoluciones;

	public PagosEnLineaController(ConsultaPagosEnLinea consulta, ServicioIngresosPorRevisar ingresos,
			DevolucionesPasarela devoluciones) {
		this.consulta = consulta;
		this.ingresos = ingresos;
		this.devoluciones = devoluciones;
	}

	@GetMapping("/pagos-en-linea")
	public String lista(Model model) {
		model.addAttribute("pagos", consulta.lista());
		return "pagos-en-linea/lista";
	}

	@GetMapping("/pagos-en-linea/{id:\\d+}")
	public String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("orden", consulta.detalle(id));
		return "pagos-en-linea/detalle";
	}

	@PostMapping("/pagos-en-linea/{id:\\d+}/aplicar")
	public String aplicar(@PathVariable Long id, @Valid AplicacionIngresoRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/pagos-en-linea/" + id;
		}
		try {
			ingresos.solicitarAplicacion(id, pedido);
			avisos.addFlashAttribute("exito", "Listo: pediste aplicar el ingreso. Promotoría o Dirección lo aprobarán; el "
					+ "pago lo registra el sistema al aprobarse.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pagos-en-linea/" + id;
	}

	@PostMapping("/pagos-en-linea/{id:\\d+}/devolver")
	public String devolver(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			ingresos.solicitarDevolucion(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste devolver el ingreso. Cuando Promotoría o Dirección lo aprueben, "
					+ "otra persona de Administración la ejecuta.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pagos-en-linea/" + id;
	}

	@PostMapping("/pagos-en-linea/{id:\\d+}/devolucion")
	public String ejecutarDevolucion(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			String reembolso = devoluciones.devolverOrden(id);
			avisos.addFlashAttribute("exito", "Listo: la pasarela devolvió el dinero al mismo medio de origen (reembolso "
					+ reembolso + ").");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pagos-en-linea/" + id;
	}
}
