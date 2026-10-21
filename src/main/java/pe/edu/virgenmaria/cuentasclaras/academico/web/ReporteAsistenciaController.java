package pe.edu.virgenmaria.cuentasclaras.academico.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ReportesAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

import java.time.YearMonth;

/**
 * Sprint 9, tanda 2 (sección 3.4): el reporte mensual de asistencia por sección (tutor y Dirección). Queda en «Quién vio
 * datos personales» ({@code REPORTE_ASISTENCIA}). Sin lógica: delega en {@link ReportesAsistencia}.
 */
@Controller
public class ReporteAsistenciaController {

	private final ReportesAsistencia reportes;

	public ReporteAsistenciaController(ReportesAsistencia reportes) {
		this.reportes = reportes;
	}

	@GetMapping("/academico/asistencia/reporte")
	@RegistraAcceso(TipoAcceso.REPORTE_ASISTENCIA)
	public String reporte(@RequestParam(name = "seccion", required = false) Long seccion,
			@RequestParam(name = "mes", required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		model.addAttribute("secciones", reportes.seccionesDisponibles());
		if (seccion != null) {
			model.addAttribute("reporte", reportes.seccion(seccion, mes));
		}
		model.addAttribute("seccion", seccion);
		model.addAttribute("mes", mes);
		return "academico/reporte";
	}
}
