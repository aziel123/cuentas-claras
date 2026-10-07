package pe.edu.virgenmaria.cuentasclaras.conciliacion.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCierreMensual;

import java.math.BigDecimal;

/**
 * Pantalla 12: cierre bancario mensual a ciegas (Promotoría y Dirección; Administración recibe 403 del servicio). Sin
 * lógica: delega en {@link ServicioCierreMensual}. Mientras el cierre está abierto, la vista no recibe los totales.
 */
@Controller
@RequestMapping("/conciliacion/cierres-mensuales")
public class CierreMensualController {

	private static final String RUTA = "/conciliacion/cierres-mensuales";

	private final ServicioCierreMensual cierres;

	public CierreMensualController(ServicioCierreMensual cierres) {
		this.cierres = cierres;
	}

	@GetMapping
	public String lista(Model model) {
		model.addAttribute("cierres", cierres.lista());
		return "conciliacion/cierres-mensuales";
	}

	@GetMapping("/{id:\\d+}")
	public String cierre(@PathVariable Long id, Model model) {
		model.addAttribute("cierre", cierres.vista(id));
		return "conciliacion/cierre-mensual";
	}

	@PostMapping("/{id:\\d+}")
	public String registrar(@PathVariable Long id, @RequestParam(required = false) Long version,
			@RequestParam(required = false) BigDecimal abonos, @RequestParam(required = false) BigDecimal cargos,
			@RequestParam(required = false) BigDecimal saldo, RedirectAttributes avisos) {
		try {
			cierres.registrar(id, version, abonos, cargos, saldo);
			avisos.addFlashAttribute("exito", "Cuadra: el estado de cuenta oficial coincide con los extractos del mes.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/" + id;
	}
}
