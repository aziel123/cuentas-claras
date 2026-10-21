package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.RegistroTardioRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ServicioAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

import java.util.List;
import java.util.Map;

/**
 * Sprint 9, tanda 2 (sección 10, decisión 144): la asistencia del día, SOLO del docente en sus secciones (Dirección: 403
 * por la matriz, E49). Sin lógica: delega en {@link ServicioAsistencia}, que exige la asignación vigente (404 si no es
 * suya), el día de clases, la firma y el resto de las reglas. Abrir la hoja queda en «Quién vio datos personales».
 */
@Controller
@RequestMapping("/academico/asistencia")
public class AsistenciaController {

	private static final String HOJA = "academico/hoja";

	private final ServicioAsistencia asistencia;

	public AsistenciaController(ServicioAsistencia asistencia) {
		this.asistencia = asistencia;
	}

	@GetMapping
	public String deHoy(Model model) {
		model.addAttribute("hoy", asistencia.deHoy());
		model.addAttribute("tardios", asistencia.tardiosPorRegistrar());
		return "academico/asistencia";
	}

	@GetMapping("/{seccionId:\\d+}/hoy")
	@RegistraAcceso(TipoAcceso.LISTA_SECCION)
	public String hoja(@PathVariable long seccionId, Model model) {
		model.addAttribute("hoja", asistencia.hoja(seccionId));
		preparar(model);
		return HOJA;
	}

	@PostMapping("/{seccionId:\\d+}/hoy")
	public String guardar(@PathVariable long seccionId, @RequestParam Map<String, String> formulario,
			RedirectAttributes avisos) {
		return guardarHoja(seccionId, HojaRequest.deFormulario(formulario, null), "redirect:/academico/asistencia/"
				+ seccionId + "/hoy", avisos);
	}

	@GetMapping("/{seccionId:\\d+}/tardio/{solicitudId:\\d+}")
	@RegistraAcceso(TipoAcceso.LISTA_SECCION)
	public String hojaTardia(@PathVariable long seccionId, @PathVariable long solicitudId, Model model) {
		model.addAttribute("hoja", asistencia.hojaTardia(seccionId, solicitudId));
		preparar(model);
		return HOJA;
	}

	@PostMapping("/{seccionId:\\d+}/tardio/{solicitudId:\\d+}")
	public String guardarTardio(@PathVariable long seccionId, @PathVariable long solicitudId,
			@RequestParam Map<String, String> formulario, RedirectAttributes avisos) {
		return guardarHoja(seccionId, HojaRequest.deFormulario(formulario, solicitudId), "redirect:/academico/asistencia",
				avisos);
	}

	@PostMapping("/marcas/{id:\\d+}/corregir-hoy")
	public String corregirHoy(@PathVariable long id, @Valid CorreccionRequest pedido, BindingResult validacion,
			@RequestParam long seccion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				asistencia.corregirHoy(id, pedido);
				avisos.addFlashAttribute("exito", "Listo: la marca quedó corregida a tu nombre. Si el aviso ya salió, la "
						+ "familia recibe la corrección.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/academico/asistencia/" + seccion + "/hoy";
	}

	@PostMapping("/marcas/{id:\\d+}/pedir-correccion")
	public String pedirCorreccion(@PathVariable long id, @Valid CorreccionRequest pedido, BindingResult validacion,
			@RequestParam long seccion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				asistencia.pedirCorreccion(id, pedido);
				avisos.addFlashAttribute("exito", "Listo: pediste la corrección. La aprueba otra persona de Dirección; "
						+ "mientras tanto la marca sigue igual.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/academico/asistencia/" + seccion + "/hoy";
	}

	@PostMapping("/{seccionId:\\d+}/pedir-registro-tardio")
	public String pedirRegistroTardio(@PathVariable long seccionId, @Valid RegistroTardioRequest pedido,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				asistencia.pedirRegistroTardio(seccionId, pedido);
				avisos.addFlashAttribute("exito", "Listo: pediste registrar ese día. Cuando Dirección lo apruebe, lo verás "
						+ "en Asistencia de hoy para registrarlo.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/academico/asistencia/" + seccionId + "/hoy";
	}

	private String guardarHoja(long seccionId, HojaRequest pedido, String destino, RedirectAttributes avisos) {
		try {
			ResultadoRegistro resultado = asistencia.guardar(seccionId, pedido);
			avisos.addFlashAttribute("exito", "Listo: asistencia guardada (" + resultado.resumen() + ").");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return destino;
	}

	private static void preparar(Model model) {
		model.addAttribute("estados", List.of(EstadoAsistencia.PRESENTE, EstadoAsistencia.FALTA, EstadoAsistencia.TARDE));
		model.addAttribute("estadosCorreccion", EstadoAsistencia.values());
	}
}
