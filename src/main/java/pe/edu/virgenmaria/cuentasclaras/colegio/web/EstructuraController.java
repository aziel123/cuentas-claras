package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.AnioEscolarVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.MotivoRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Años escolares y secciones. Sin lógica: valida el formulario, delega en {@link ServicioEstructura} y muestra el
 * resultado. Los permisos de escritura los exige el servicio (Dirección y Administración).
 */
@Controller
@RequestMapping("/colegio")
public class EstructuraController {

	private static final String VISTA_RESUMEN = "colegio/resumen";

	private static final String VISTA_ANIO = "colegio/anio";

	private final ServicioEstructura servicio;

	private final Clock reloj;

	public EstructuraController(ServicioEstructura servicio, Clock reloj) {
		this.servicio = servicio;
		this.reloj = reloj;
	}

	@GetMapping
	public String resumen(@AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		List<AnioEscolarVista> anios = prepararResumen(model, sesion);
		model.addAttribute("solicitud", CrearAnioEscolarRequest.propuesta(siguienteAnio(anios)));
		return VISTA_RESUMEN;
	}

	@PostMapping("/anios")
	public String crearAnio(@Valid @ModelAttribute("solicitud") CrearAnioEscolarRequest solicitud,
			BindingResult validacion, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			prepararResumen(model, sesion);
			return VISTA_RESUMEN;
		}
		try {
			Long id = servicio.crearAnio(solicitud);
			avisos.addFlashAttribute("exito", "Listo: creaste el año " + solicitud.anio() + ". Ahora agrégale sus secciones.");
			return "redirect:/colegio/anios/" + id;
		}
		catch (ReglaNegocioException e) {
			prepararResumen(model, sesion);
			model.addAttribute("error", e.getMessage());
			return VISTA_RESUMEN;
		}
	}

	@GetMapping("/anios/{id:\\d+}")
	public String anio(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		Object ultimoGrado = model.getAttribute("ultimoGrado");
		model.addAttribute("nuevaSeccion", ultimoGrado instanceof Grado grado ? new CrearSeccionRequest(grado, "")
				: CrearSeccionRequest.vacio());
		prepararAnio(model, id, sesion);
		return VISTA_ANIO;
	}

	@PostMapping("/anios/{id:\\d+}/secciones")
	public String crearSeccion(@PathVariable Long id, @Valid @ModelAttribute("nuevaSeccion") CrearSeccionRequest solicitud,
			BindingResult validacion, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			prepararAnio(model, id, sesion);
			return VISTA_ANIO;
		}
		try {
			servicio.crearSeccion(id, solicitud);
		}
		catch (ReglaNegocioException e) {
			prepararAnio(model, id, sesion);
			model.addAttribute("error", e.getMessage());
			return VISTA_ANIO;
		}
		avisos.addFlashAttribute("exito", "Listo: agregaste una sección a " + solicitud.grado().etiqueta() + ".");
		// Se queda en el mismo grado para agregar la siguiente sección rápido.
		avisos.addFlashAttribute("ultimoGrado", solicitud.grado());
		return "redirect:/colegio/anios/" + id;
	}

	@PostMapping("/secciones/{id:\\d+}/desactivar")
	public String desactivarSeccion(@PathVariable Long id, @RequestParam Long anioId, @Valid MotivoRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/colegio/anios/" + anioId;
		}
		try {
			Long anio = servicio.desactivarSeccion(id, solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: la sección quedó desactivada. Sus matrículas se conservan.");
			return "redirect:/colegio/anios/" + anio;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/colegio/anios/" + anioId;
		}
	}

	private List<AnioEscolarVista> prepararResumen(Model model, UsuarioAutenticado sesion) {
		List<AnioEscolarVista> anios = servicio.listarAnios();
		model.addAttribute("anios", anios);
		model.addAttribute("hayEnCurso", anios.stream().anyMatch(AnioEscolarVista::enCurso));
		model.addAttribute("puedeEditar", Formularios.puedeEditar(sesion));
		return anios;
	}

	private void prepararAnio(Model model, Long id, UsuarioAutenticado sesion) {
		model.addAttribute("detalle", servicio.obtenerAnio(id));
		model.addAttribute("grados", Grado.values());
		model.addAttribute("niveles", Nivel.values());
		model.addAttribute("puedeEditar", Formularios.puedeEditar(sesion));
	}

	private int siguienteAnio(List<AnioEscolarVista> anios) {
		return anios.stream().mapToInt(AnioEscolarVista::anio).max().orElse(LocalDate.now(reloj).getYear() - 1) + 1;
	}
}
