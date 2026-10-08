package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDate;

/** Pantalla 11: feriados nacionales y días no laborables del colegio. Sin lógica: delega en {@link ServicioFeriados}. */
@Controller
public class FeriadosController {

	private final ServicioFeriados feriados;

	private final Clock reloj;

	public FeriadosController(ServicioFeriados feriados, Clock reloj) {
		this.feriados = feriados;
		this.reloj = reloj;
	}

	@GetMapping("/feriados")
	public String lista(@RequestParam(required = false) Integer anio, Model model) {
		int elegido = anio == null || anio < 2026 || anio > 2100 ? LocalDate.now(reloj).getYear() : anio;
		model.addAttribute("vista", feriados.vista(elegido));
		return "feriados/lista";
	}

	@PostMapping("/feriados")
	public String registrar(@Valid FeriadoRequest pedido, BindingResult errores, RedirectAttributes mensajes) {
		if (errores.hasErrors()) {
			mensajes.addFlashAttribute("error", errores.getAllErrors().getFirst().getDefaultMessage());
			return "redirect:/feriados";
		}
		try {
			feriados.registrar(pedido);
			mensajes.addFlashAttribute("exito", "Listo: el día quedó PROPUESTO. Cuenta como no laborable cuando lo apruebe otra persona de "
					+ "Promotoría o Dirección (se le avisó por mensaje).");
		}
		catch (ReglaNegocioException e) {
			mensajes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/feriados?anio=" + pedido.fecha().getYear();
	}

	@PostMapping("/feriados/{id:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, RedirectAttributes mensajes) {
		try {
			feriados.aprobar(id);
			mensajes.addFlashAttribute("exito", "Listo: ese día ya no cuenta como hábil y quedó en la bitácora.");
		}
		catch (ReglaNegocioException e) {
			mensajes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/feriados";
	}

	@PostMapping("/feriados/{id:\\d+}/anular")
	public String anular(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes mensajes) {
		try {
			feriados.anular(id, motivo);
			mensajes.addFlashAttribute("exito", "Listo: el día vuelve a ser hábil.");
		}
		catch (ReglaNegocioException e) {
			mensajes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/feriados";
	}
}
