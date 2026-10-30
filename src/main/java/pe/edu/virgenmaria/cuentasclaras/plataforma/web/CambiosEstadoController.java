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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CambioEstadoRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.TextoRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.CambiosEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.ColegiosDeLaPlataforma;

import java.util.Arrays;

/**
 * Sprint 11, tanda 1 (secciones 3.3 a 3.6): el estado del colegio y la facultad de su titular, que deciden dos operadores.
 * La bandeja {@code /plataforma/cambios-estado} y la pantalla de cada colegio. Sin lógica: delega en
 * {@link CambiosEstadoColegio}. Toda ruta lleva el colegio, y el servicio busca el cambio en ESE colegio.
 */
@Controller
@RequestMapping("/plataforma")
public class CambiosEstadoController {

	private static final String VOLVER_BANDEJA = "redirect:/plataforma/cambios-estado";

	private final CambiosEstadoColegio cambios;

	private final ColegiosDeLaPlataforma colegios;

	public CambiosEstadoController(CambiosEstadoColegio cambios, ColegiosDeLaPlataforma colegios) {
		this.cambios = cambios;
		this.colegios = colegios;
	}

	@GetMapping("/cambios-estado")
	public String bandeja(Model model) {
		model.addAttribute("cambios", cambios.bandeja());
		model.addAttribute("suspensionDisponible", cambios.suspensionDisponible());
		return "plataforma/cambios-estado";
	}

	@GetMapping("/colegios/{id:\\d+}/estado")
	public String estado(@PathVariable Long id, Model model) {
		model.addAttribute("ficha", colegios.ficha(id));
		model.addAttribute("historial", cambios.historial(id));
		model.addAttribute("tipos", Arrays.stream(TipoCambioEstado.values())
				.filter(t -> t != TipoCambioEstado.SUSPENSION || cambios.suspensionDisponible()).toList());
		model.addAttribute("motivos", MotivoCambioEstado.values());
		model.addAttribute("suspensionDisponible", cambios.suspensionDisponible());
		if (!model.containsAttribute("propuesta")) {
			model.addAttribute("propuesta", CambioEstadoRequest.vacio());
		}
		return "plataforma/estado-colegio";
	}

	@PostMapping("/colegios/{id:\\d+}/estado")
	public String proponer(@PathVariable Long id, @Valid @ModelAttribute("propuesta") CambioEstadoRequest propuesta,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			avisos.addFlashAttribute("propuesta", propuesta);
			return "redirect:/plataforma/colegios/" + id + "/estado";
		}
		try {
			cambios.proponer(id, propuesta.tipo(), propuesta.motivoTipo(), propuesta.motivo(), propuesta.referenciaAviso());
			avisos.addFlashAttribute("exito", "Listo: propusiste «" + propuesta.tipo().etiqueta() + "». No cambia nada hasta "
					+ "que lo apruebe otro operador, con su propia sesión. Se avisó al titular, a la Promotoría y al correo de "
					+ "control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			avisos.addFlashAttribute("propuesta", propuesta);
		}
		return "redirect:/plataforma/colegios/" + id + "/estado";
	}

	@PostMapping("/colegios/{id:\\d+}/cambios-estado/{cambio:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, @PathVariable Long cambio, RedirectAttributes avisos) {
		try {
			cambios.aprobar(id, cambio);
			avisos.addFlashAttribute("exito", "Listo: aprobaste el cambio. Si era inmediato, ya se aplicó; si no, se aplica "
					+ "solo en su fecha.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER_BANDEJA;
	}

	@PostMapping("/colegios/{id:\\d+}/cambios-estado/{cambio:\\d+}/rechazar")
	public String rechazar(@PathVariable Long id, @PathVariable Long cambio, RedirectAttributes avisos) {
		try {
			cambios.rechazar(id, cambio);
			avisos.addFlashAttribute("exito", "Listo: rechazaste el cambio. No cambia nada.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER_BANDEJA;
	}

	@PostMapping("/colegios/{id:\\d+}/cambios-estado/{cambio:\\d+}/anular")
	public String anular(@PathVariable Long id, @PathVariable Long cambio, RedirectAttributes avisos) {
		try {
			cambios.anular(id, cambio);
			avisos.addFlashAttribute("exito", "Listo: anulaste el cambio. No cambia nada.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER_BANDEJA;
	}

	@PostMapping("/colegios/{id:\\d+}/cambios-estado/{cambio:\\d+}/objecion")
	public String responderObjecion(@PathVariable Long id, @PathVariable Long cambio,
			@Valid @ModelAttribute TextoRequest respuesta, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return VOLVER_BANDEJA;
		}
		try {
			cambios.resolverObjecion(id, cambio, respuesta.texto());
			avisos.addFlashAttribute("exito", "Listo: respondiste la objeción del titular. Se le avisó, y también a la "
					+ "Promotoría y al correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER_BANDEJA;
	}
}
