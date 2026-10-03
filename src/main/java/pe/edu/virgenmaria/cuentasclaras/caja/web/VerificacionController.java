package pe.edu.virgenmaria.cuentasclaras.caja.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReembolsoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioVerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Verificación bancaria a ciegas de pagos digitales y depósitos, y reembolsos de devoluciones (módulo CONCILIACION).
 * Sin lógica.
 */
@Controller
public class VerificacionController {

	private static final String VOLVER = "redirect:/conciliacion";

	private final ServicioVerificacionBancaria verificacion;

	public VerificacionController(ServicioVerificacionBancaria verificacion) {
		this.verificacion = verificacion;
	}

	@GetMapping("/conciliacion")
	public String vista(Model model) {
		model.addAttribute("conciliacion", verificacion.vista());
		return "conciliacion/verificacion";
	}

	@PostMapping("/conciliacion/pagos/{id:\\d+}")
	public String verificarPago(@PathVariable Long id, @Valid VerificacionRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			verificacion.verificarPago(id, pedido);
			avisos.addFlashAttribute("exito", pedido.resultado() == ResultadoVerificacion.ENCONTRADO
					? "Coincide: pago verificado." : "Listo: quedó marcado «No aparece». Promotoría lo verá como alerta.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/conciliacion/depositos/{id:\\d+}")
	public String verificarDeposito(@PathVariable Long id, @Valid VerificacionRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			verificacion.verificarDeposito(id, pedido);
			avisos.addFlashAttribute("exito", pedido.resultado() == ResultadoVerificacion.ENCONTRADO
					? "Coincide: depósito verificado." : "Listo: quedó marcado «No aparece». Promotoría lo verá como alerta.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/conciliacion/devoluciones/{id:\\d+}")
	public String registrarReembolso(@PathVariable Long id, @Valid ReembolsoRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			verificacion.registrarReembolso(id, pedido);
			avisos.addFlashAttribute("exito", "Listo: registraste el reembolso de la devolución.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}
}
