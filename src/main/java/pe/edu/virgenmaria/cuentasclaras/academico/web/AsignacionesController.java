package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CopiarAsignacionesRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoCopia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.MotivoRequest;

/**
 * Sprint 9, tanda 1 (sección 10): Docentes y secciones. Sin lógica: valida el formulario, delega en {@link Asignaciones}
 * (que exige el rol de cada acción y la firma al terminar) y muestra el resultado. Aprobar es de la bandeja.
 */
@Controller
@RequestMapping("/academico/asignaciones")
public class AsignacionesController {

	private static final String VISTA = "academico/asignaciones";

	private final Asignaciones asignaciones;

	public AsignacionesController(Asignaciones asignaciones) {
		this.asignaciones = asignaciones;
	}

	@GetMapping
	public String ver(@RequestParam(name = "anio", required = false) Long anio, Model model) {
		preparar(model, anio, ProponerAsignacionRequest.vacio());
		return VISTA;
	}

	@PostMapping
	public String proponer(@Valid @ModelAttribute("nueva") ProponerAsignacionRequest pedido, BindingResult validacion,
			@RequestParam(name = "anio", required = false) Long anio, Model model, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			preparar(model, anio, pedido);
			return VISTA;
		}
		try {
			asignaciones.proponer(pedido);
		}
		catch (ReglaNegocioException e) {
			preparar(model, anio, pedido);
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", "Listo: la asignación quedó por aprobar. Debe aprobarla otra persona de "
				+ "Dirección o Promotoría en Aprobaciones (nunca el docente asignado).");
		return redireccion(anio);
	}

	@PostMapping("/copiar-anio-anterior")
	public String copiar(@Valid CopiarAsignacionesRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redireccion(pedido.anioId());
		}
		try {
			ResultadoCopia resultado = asignaciones.proponerLasDelAnioAnterior(pedido.anioId(), pedido.motivo());
			avisos.addFlashAttribute("exito", "Listo: se propusieron " + resultado.propuestas() + " asignación(es) del año "
					+ "anterior" + (resultado.saltadas() == 0 ? "" : " (" + resultado.saltadas() + " no se copiaron: ya "
							+ "existían, la sección no está este año o la cuenta ya no es docente)")
					+ ". Cada una se aprueba en Aprobaciones.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redireccion(pedido.anioId());
	}

	@PostMapping("/{id:\\d+}/terminar")
	public String terminar(@PathVariable Long id, @Valid MotivoRequest pedido, BindingResult validacion,
			@RequestParam(name = "anio", required = false) Long anio, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redireccion(anio);
		}
		try {
			asignaciones.terminar(id, pedido.motivo());
			avisos.addFlashAttribute("exito", "Listo: la asignación terminó. Esa persona ya no ve la sección.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redireccion(anio);
	}

	private void preparar(Model model, Long anio, ProponerAsignacionRequest nueva) {
		AsignacionesVista vista = asignaciones.vista(anio);
		model.addAttribute("vista", vista);
		model.addAttribute("tipos", TipoAsignacion.values());
		model.addAttribute("nueva", nueva);
	}

	private static String redireccion(Long anio) {
		return anio == null ? "redirect:/academico/asignaciones" : "redirect:/academico/asignaciones?anio=" + anio;
	}
}
