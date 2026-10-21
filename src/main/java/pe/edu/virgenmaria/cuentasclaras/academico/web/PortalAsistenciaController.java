package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.ConsentimientoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsentimientosAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.service.JustificacionesFamilia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

import java.time.YearMonth;

/**
 * Sprint 9, tanda 2 (secciones 3.5 y 3.6): el portal de la familia, sin crecer dentro de {@code familias} (que depende de
 * cobranza): la asistencia del mes de cada hijo, justificar una falta y los avisos (consentimientos). Sin lógica: delega
 * en {@link JustificacionesFamilia} y {@link ConsentimientosAviso} (rol APODERADO; otra familia: 404).
 */
@Controller
public class PortalAsistenciaController {

	private final JustificacionesFamilia justificaciones;

	private final ConsentimientosAviso consentimientos;

	public PortalAsistenciaController(JustificacionesFamilia justificaciones, ConsentimientosAviso consentimientos) {
		this.justificaciones = justificaciones;
		this.consentimientos = consentimientos;
	}

	@GetMapping("/familia/asistencia")
	public String asistencia(@RequestParam(name = "mes", required = false) @DateTimeFormat(pattern = "yyyy-MM")
			YearMonth mes, Model model) {
		model.addAttribute("mes", justificaciones.delMes(mes));
		model.addAttribute("motivos", MotivoJustificacion.values());
		return "familia/asistencia";
	}

	@PostMapping("/familia/asistencia/marcas/{id:\\d+}/justificar")
	public String justificar(@PathVariable long id, @Valid JustificarRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				justificaciones.justificar(id, pedido);
				avisos.addFlashAttribute("exito", "Listo: la falta quedó justificada. El tutor lo verá en su sección.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/familia/asistencia";
	}

	@GetMapping("/familia/avisos")
	public String avisos(Model model) {
		model.addAttribute("apoderados", consentimientos.avisos());
		return "familia/avisos";
	}

	@PostMapping("/familia/avisos")
	public String cambiarAviso(@Valid ConsentimientoRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				consentimientos.registrarEnPortal(pedido);
				avisos.addFlashAttribute("exito", "Listo: guardamos tu preferencia.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/familia/avisos";
	}
}
