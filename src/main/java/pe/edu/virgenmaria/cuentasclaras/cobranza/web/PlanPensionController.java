package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
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
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MotivoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Pensiones: resumen por año y planes por nivel. Sin lógica: valida el formulario, delega en
 * {@link ServicioPlanesPension} (que exige los roles) y muestra el resultado.
 */
@Controller
@RequestMapping("/pensiones")
public class PlanPensionController {

	private static final String VISTA_FORMULARIO = "pensiones/plan-formulario";

	static final String CAMBIO_SIMULTANEO = "Otra persona acaba de cambiar o resolver esto al mismo tiempo; revísalo de nuevo.";

	private final ServicioPlanesPension servicio;

	public PlanPensionController(ServicioPlanesPension servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public String resumen(@RequestParam(name = "anio", required = false) Long anioId, Model model) {
		model.addAttribute("resumen", servicio.resumen(anioId));
		return "pensiones/resumen";
	}

	@GetMapping("/planes/nuevo")
	public String nuevo(@RequestParam("anio") Long anioId, @RequestParam Nivel nivel, Model model) {
		model.addAttribute("solicitud", servicio.propuestaPorDefecto(anioId, nivel));
		prepararNuevo(model, anioId, nivel);
		return VISTA_FORMULARIO;
	}

	@PostMapping("/planes/nuevo")
	public String crear(@RequestParam("anio") Long anioId, @RequestParam Nivel nivel,
			@Valid @ModelAttribute("solicitud") PlanRequest solicitud, BindingResult validacion, Model model,
			RedirectAttributes avisos) {
		if (!validacion.hasErrors()) {
			try {
				Long id = servicio.crearBorrador(anioId, nivel, solicitud);
				avisos.addFlashAttribute("exito", "Listo: propusiste el plan. Ahora debe aprobarlo otra persona de "
						+ "Promotoría o Dirección.");
				return "redirect:/pensiones/planes/" + id;
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararNuevo(model, anioId, nivel);
		return VISTA_FORMULARIO;
	}

	@GetMapping("/planes/{id:\\d+}")
	public String plan(@PathVariable Long id, Model model) {
		model.addAttribute("plan", servicio.obtener(id));
		return "pensiones/plan";
	}

	@GetMapping("/planes/{id:\\d+}/editar")
	public String editar(@PathVariable Long id, Model model, RedirectAttributes avisos) {
		try {
			model.addAttribute("solicitud", servicio.datosParaEditar(id));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/pensiones/planes/" + id;
		}
		prepararEdicion(model, id);
		return VISTA_FORMULARIO;
	}

	@PostMapping("/planes/{id:\\d+}")
	public String guardar(@PathVariable Long id, @Valid @ModelAttribute("solicitud") PlanRequest solicitud,
			BindingResult validacion, Model model, RedirectAttributes avisos) {
		if (!validacion.hasErrors()) {
			try {
				servicio.editarBorrador(id, solicitud);
				avisos.addFlashAttribute("exito", "Listo: guardaste los cambios del borrador.");
				return "redirect:/pensiones/planes/" + id;
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararEdicion(model, id);
		return VISTA_FORMULARIO;
	}

	@PostMapping("/planes/{id:\\d+}/nueva-version")
	public String nuevaVersion(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/pensiones/planes/" + id;
		}
		try {
			Long nueva = servicio.nuevaVersion(id, solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: se creó una versión nueva en borrador. Cambia los montos y "
					+ "guárdala; el plan actual sigue vigente hasta que otra persona la apruebe.");
			return "redirect:/pensiones/planes/" + nueva + "/editar";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/pensiones/planes/" + id;
		}
	}

	@PostMapping("/planes/{id:\\d+}/enviar")
	public String enviar(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			servicio.enviar(id);
			avisos.addFlashAttribute("exito", "Listo: enviaste el plan. Ya no se puede editar; lo aprueba otra persona "
					+ "de Promotoría o Dirección.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pensiones/planes/" + id;
	}

	@PostMapping("/planes/{id:\\d+}/devolver")
	public String devolver(@PathVariable Long id, @RequestParam(required = false) Long version,
			@Valid MotivoRequest solicitud, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/pensiones/planes/" + id;
		}
		try {
			servicio.devolver(id, version, solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: devolviste el plan a Administración para que lo corrija.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		catch (OptimisticLockingFailureException e) {
			avisos.addFlashAttribute("error", CAMBIO_SIMULTANEO);
		}
		return "redirect:/pensiones/planes/" + id;
	}

	@PostMapping("/planes/{id:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, @RequestParam(required = false) Long version,
			RedirectAttributes avisos) {
		try {
			ResultadoGeneracion resultado = servicio.aprobar(id, version);
			avisos.addFlashAttribute("exito", "Listo: aprobaste el plan. Se generaron " + resultado.cuotas()
					+ " cuotas para " + resultado.matriculas() + " matrículas (" + Dinero.formatear(resultado.total())
					+ ").");
			if (!resultado.omitidas().isEmpty()) {
				avisos.addFlashAttribute("advertencia", resultado.omitidas().size()
						+ " cuotas no se generaron porque esa deuda ya existía (por ejemplo, como saldo inicial).");
			}
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		catch (OptimisticLockingFailureException e) {
			avisos.addFlashAttribute("error", CAMBIO_SIMULTANEO);
		}
		return "redirect:/pensiones/planes/" + id;
	}

	@PostMapping("/planes/{id:\\d+}/descartar")
	public String descartar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/pensiones/planes/" + id;
		}
		try {
			servicio.descartar(id, solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: descartaste el borrador.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pensiones/planes/" + id;
	}

	private void prepararNuevo(Model model, Long anioId, Nivel nivel) {
		model.addAttribute("anio", servicio.anio(anioId));
		model.addAttribute("nivel", nivel);
		model.addAttribute("planId", null);
	}

	private void prepararEdicion(Model model, Long id) {
		model.addAttribute("plan", servicio.obtener(id));
		model.addAttribute("planId", id);
	}
}
