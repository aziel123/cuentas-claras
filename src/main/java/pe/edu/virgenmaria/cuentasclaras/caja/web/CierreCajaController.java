package pe.edu.virgenmaria.cuentasclaras.caja.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Cierre ciego de la cajera: contar → (si no coincide) recontar con explicación → resultado; después, depósito y pedido
 * de reapertura. Sin lógica: delega en {@link ServicioCierreCaja}. El formulario nunca trae el esperado.
 */
@Controller
public class CierreCajaController {

	private static final String VOLVER = "redirect:/caja/cierre";

	private final ServicioCierreCaja cierre;

	public CierreCajaController(ServicioCierreCaja cierre) {
		this.cierre = cierre;
	}

	@GetMapping("/caja/cierre")
	public String estado(Model model) {
		model.addAttribute("cierre", cierre.estado());
		return "caja/cierre";
	}

	@PostMapping("/caja/cierre/conteo")
	public String contar(@Valid ConteoRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			ResultadoConteo resultado = cierre.contar(pedido);
			if (resultado == ResultadoConteo.COINCIDE) {
				avisos.addFlashAttribute("exito", "Tu caja cuadró y quedó cerrada. Promotoría o Dirección revisarán el "
						+ "cierre.");
			}
			else {
				avisos.addFlashAttribute("advertencia", "No coincide con lo registrado. Vuelve a contar con calma, billete "
						+ "por billete, y explica qué pasó.");
			}
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/caja/cierre/reconteo")
	public String recontar(@Valid ReconteoRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			cierre.recontar(pedido);
			avisos.addFlashAttribute("exito", "Tu caja quedó cerrada. Revisa el resultado abajo.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/caja/cierre/deposito")
	public String depositar(@Valid DepositoRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER;
		}
		try {
			cierre.registrarDeposito(pedido);
			avisos.addFlashAttribute("exito", "Depósito registrado. Administración lo verificará con el banco.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/caja/cierre/reapertura")
	public String reabrir(@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			cierre.solicitarReapertura(motivo);
			avisos.addFlashAttribute("exito", "Pediste reabrir tu caja. Promotoría o Dirección lo revisarán; mientras "
					+ "tanto solo puedes cobrar pagos digitales.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}
}
