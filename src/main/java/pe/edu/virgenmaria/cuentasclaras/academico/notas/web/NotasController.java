package pe.edu.virgenmaria.cuentasclaras.academico.notas.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

import java.util.Map;

/**
 * Sprint 10, tanda 2 (sección 3.5): la hoja de notas del DOCENTE por sección, área y periodo (Dirección: 403 por la matriz;
 * no califica). Sin lógica: delega en {@link RegistroNotas} (un POST, un lote, una firma) y en {@link CorreccionesNota}
 * (la nota tardía). Abrir la hoja queda en «Quién vio datos personales» (NOTAS_SECCION).
 */
@Controller
@RequestMapping("/academico/notas")
public class NotasController {

	/** Los niveles de logro en el orden en que se muestran (del mayor al menor). */
	static final java.util.List<String> NIVELES = java.util.List.of("AD", "A", "B", "C");

	private final RegistroNotas registro;

	private final CorreccionesNota correcciones;

	public NotasController(RegistroNotas registro, CorreccionesNota correcciones) {
		this.registro = registro;
		this.correcciones = correcciones;
	}

	@GetMapping
	public String inicio(Model model) {
		model.addAttribute("mias", registro.mias());
		return "academico/notas/inicio";
	}

	@GetMapping("/{seccionId:\\d+}/{areaId:\\d+}")
	@RegistraAcceso(TipoAcceso.NOTAS_SECCION)
	public String hoja(@PathVariable long seccionId, @PathVariable long areaId,
			@RequestParam(name = "periodo", required = false) Long periodoId,
			@RequestParam(name = "solicitud", required = false) Long solicitudId, Model model) {
		model.addAttribute("hoja", registro.hoja(seccionId, areaId, periodoId, solicitudId));
		model.addAttribute("niveles", NIVELES);
		return "academico/notas/hoja";
	}

	@PostMapping("/{seccionId:\\d+}/{areaId:\\d+}")
	public String guardar(@PathVariable long seccionId, @PathVariable long areaId,
			@RequestParam(name = "periodo") long periodoId,
			@RequestParam(name = "solicitud", required = false) Long solicitudId,
			@RequestParam Map<String, String> formulario, RedirectAttributes avisos) {
		String volver = "redirect:/academico/notas/" + seccionId + "/" + areaId + "?periodo=" + periodoId;
		try {
			ResultadoNotas resultado = registro.guardar(seccionId, areaId, periodoId,
					NotasRequest.deFormulario(formulario, solicitudId));
			avisos.addFlashAttribute("exito", "Listo: guardaste las notas con tu firma (" + resultado.resumen() + ").");
			return solicitudId == null ? volver : "redirect:/academico/notas";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return solicitudId == null ? volver : volver + "&solicitud=" + solicitudId;
		}
	}

	@PostMapping("/{seccionId:\\d+}/{areaId:\\d+}/tardia")
	public String pedirTardia(@PathVariable long seccionId, @PathVariable long areaId,
			@RequestParam(name = "periodo") long periodoId, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			correcciones.pedirTardia(seccionId, areaId, periodoId, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste registrar las notas pendientes. Cuando otra persona de "
					+ "Dirección o Promotoría lo apruebe, tienes 48 horas.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/academico/notas/" + seccionId + "/" + areaId + "?periodo=" + periodoId;
	}
}
