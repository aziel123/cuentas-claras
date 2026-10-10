package pe.edu.virgenmaria.cuentasclaras.operacion.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ServicioEstadoTecnico;

/**
 * Sprint 7, tanda 1: el estado técnico para Promotoría ({@code /panel/sistema}) y la ruta pública del vigilante externo
 * ({@code /salud/respaldo}: solo {@code OK}, {@code ATRASADO} o {@code REVISAR}, sin fechas ni datos).
 */
@Controller
public class EstadoTecnicoController {

	private final ServicioEstadoTecnico servicio;

	private final ResolucionesRespaldo resoluciones;

	public EstadoTecnicoController(ServicioEstadoTecnico servicio, ResolucionesRespaldo resoluciones) {
		this.servicio = servicio;
		this.resoluciones = resoluciones;
	}

	/** Correcciones del sprint 7 (QA-S7-1): Promotoría resuelve con motivo la alerta «Faltan filas». */
	@PostMapping("/panel/sistema/respaldo/resolver")
	public String resolver(@RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			String archivo = resoluciones.resolver(motivo);
			avisos.addFlashAttribute("exito", "Listo: resolviste la alerta del respaldo " + archivo
					+ ". Tu motivo quedó en la bitácora.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/panel/sistema";
	}

	@GetMapping("/panel/sistema")
	public String sistema(Model modelo) {
		modelo.addAttribute("sistema", servicio.vista());
		return "panel/sistema";
	}

	@GetMapping(value = "/salud/respaldo", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> respaldo() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_PLAIN)
				.body(servicio.respaldoParaVigilante());
	}
}
