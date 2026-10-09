package pe.edu.virgenmaria.cuentasclaras.operacion.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ServicioEstadoTecnico;

/**
 * Sprint 7, tanda 1: el estado técnico para Promotoría ({@code /panel/sistema}) y la ruta pública del vigilante externo
 * ({@code /salud/respaldo}: solo {@code OK}, {@code ATRASADO} o {@code REVISAR}, sin fechas ni datos).
 */
@Controller
public class EstadoTecnicoController {

	private final ServicioEstadoTecnico servicio;

	public EstadoTecnicoController(ServicioEstadoTecnico servicio) {
		this.servicio = servicio;
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
