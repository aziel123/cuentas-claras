package pe.edu.virgenmaria.cuentasclaras.plataforma.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.TextoRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.ObjecionesTitular;

/**
 * Sprint 11, tanda 1 (AP-10): en el colegio, la Promotoría y Dirección ven los cambios de estado que propusieron los
 * operadores, y el titular objeta una baja o una suspensión pendiente. Sin lógica: delega en {@link ObjecionesTitular}.
 */
@Controller
@RequestMapping("/colegio/estado")
public class EstadoColegioController {

	private final ObjecionesTitular objeciones;

	public EstadoColegioController(ObjecionesTitular objeciones) {
		this.objeciones = objeciones;
	}

	@GetMapping
	public String ver(Model model) {
		model.addAttribute("cambios", objeciones.delColegio());
		model.addAttribute("esTitular", objeciones.puedeObjetar());
		return "colegio/estado";
	}

	@PostMapping("/{id:\\d+}/objecion")
	public String objetar(@PathVariable Long id, @Valid @ModelAttribute TextoRequest objecion, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/colegio/estado";
		}
		try {
			objeciones.objetar(id, objecion.texto());
			avisos.addFlashAttribute("exito", "Listo: tu objeción quedó registrada. El cambio no se aplica hasta que la "
					+ "responda un operador que no lo propuso. Se avisó a la Promotoría, al correo de control y a la plataforma.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/colegio/estado";
	}
}
