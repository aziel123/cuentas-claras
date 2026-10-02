package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ConsultaCajas;

import java.time.LocalDate;

/**
 * Cajas del día y su detalle para Promotoría y Dirección (bajo Aprobaciones). Vive en {@code caja}: el módulo
 * {@code aprobaciones} no depende de caja.
 */
@Controller
public class CajasAprobacionController {

	private final ConsultaCajas consulta;

	public CajasAprobacionController(ConsultaCajas consulta) {
		this.consulta = consulta;
	}

	@GetMapping("/aprobaciones/cajas")
	public String delDia(@RequestParam(name = "fecha", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, Model model) {
		model.addAttribute("dia", consulta.delDia(fecha));
		return "aprobaciones/cajas";
	}

	@GetMapping("/aprobaciones/cajas/{id:\\d+}")
	public String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("caja", consulta.detalle(id));
		return "aprobaciones/caja";
	}
}
