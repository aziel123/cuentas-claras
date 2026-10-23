package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.web;

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
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.FechasPeriodoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.PeriodosVista;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ProgramarPeriodoRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Sprint 10, tanda 1 (sección 8.3): «Periodos» del año. Solo Dirección (lo exigen la matriz y el servicio); cada acción con
 * su firma (programar, mover fechas, abrir antes de tiempo y cerrar). Sin lógica: valida el formulario y delega en
 * {@link Periodos}.
 */
@Controller
@RequestMapping("/academico/periodos")
public class PeriodosController {

	private static final String VISTA = "academico/periodos";

	private final Periodos periodos;

	public PeriodosController(Periodos periodos) {
		this.periodos = periodos;
	}

	@GetMapping
	public String ver(@RequestParam(name = "anio", required = false) Long anio, Model model) {
		PeriodosVista vista = periodos.vista(anio);
		model.addAttribute("vista", vista);
		model.addAttribute("nuevo", ProgramarPeriodoRequest.vacio(vista.anioId()));
		return VISTA;
	}

	@PostMapping
	public String programar(@Valid @ModelAttribute("nuevo") ProgramarPeriodoRequest pedido, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			model.addAttribute("vista", periodos.vista(pedido.anioId()));
			return VISTA;
		}
		try {
			periodos.programar(pedido.anioId(), pedido.inicio(), pedido.fin());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("vista", periodos.vista(pedido.anioId()));
			model.addAttribute("error", e.getMessage());
			return VISTA;
		}
		avisos.addFlashAttribute("exito", "Listo: el periodo quedó programado con tu firma. Abre solo en su fecha.");
		return redireccion(pedido.anioId());
	}

	@PostMapping("/{id:\\d+}/fechas")
	public String fechas(@PathVariable Long id, @Valid FechasPeriodoRequest pedido, BindingResult validacion,
			@RequestParam(name = "anio", required = false) Long anio, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redireccion(anio);
		}
		try {
			periodos.moverFechas(id, pedido.inicio(), pedido.fin());
			avisos.addFlashAttribute("exito", "Listo: las fechas cambiaron con tu firma y el cambio quedó en la bitácora.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redireccion(anio);
	}

	@PostMapping("/{id:\\d+}/abrir")
	public String abrir(@PathVariable Long id, @RequestParam(name = "anio", required = false) Long anio,
			RedirectAttributes avisos) {
		try {
			periodos.abrir(id);
			avisos.addFlashAttribute("exito", "Listo: el periodo quedó abierto con tu firma. Los docentes ya pueden registrar.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redireccion(anio);
	}

	@PostMapping("/{id:\\d+}/cerrar")
	public String cerrar(@PathVariable Long id, @RequestParam(name = "anio", required = false) Long anio,
			RedirectAttributes avisos) {
		try {
			periodos.cerrar(id);
			avisos.addFlashAttribute("exito", "Listo: el periodo quedó cerrado con tu firma. No se reabre.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redireccion(anio);
	}

	private static String redireccion(Long anio) {
		return anio == null ? "redirect:/academico/periodos" : "redirect:/academico/periodos?anio=" + anio;
	}
}
