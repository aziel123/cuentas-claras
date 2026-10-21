package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsistenciaDireccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.MotivoRequest;

import java.time.LocalDate;

/**
 * Sprint 9, tanda 2 (E5, E13): Dirección ve los cambios de la asistencia del día y revierte una justificación con motivo
 * y su firma. No registra ni corrige marcas (decisión 144). Sin lógica: delega en {@link AsistenciaDireccion}.
 */
@Controller
@RequestMapping("/academico/asistencia")
public class AsistenciaDireccionController {

	private final AsistenciaDireccion direccion;

	public AsistenciaDireccionController(AsistenciaDireccion direccion) {
		this.direccion = direccion;
	}

	@GetMapping("/cambios")
	public String cambios(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
			Model model) {
		model.addAttribute("cambios", direccion.cambiosDelDia(fecha));
		model.addAttribute("fecha", fecha);
		return "academico/cambios";
	}

	@PostMapping("/marcas/{id:\\d+}/revertir-justificacion")
	public String revertir(@PathVariable long id, @Valid MotivoRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				direccion.revertir(id, pedido.motivo());
				avisos.addFlashAttribute("exito", "Listo: la justificación quedó revertida. La familia recibe el aviso.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/academico/asistencia/cambios";
	}
}
