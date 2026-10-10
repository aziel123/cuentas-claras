package pe.edu.virgenmaria.cuentasclaras.panel.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ArchivoExportado;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ExportacionContador;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ReportesCobranza;

import java.time.LocalDate;

/**
 * Reportes de cobranza (sprint 6): morosidad por grado, ingresos por medio de pago y familias morosas en pantalla, y el
 * Excel para el contador. Exportar es SOLO POST con CSRF (P13): un enlace o una imagen en un correo no puede disparar una
 * descarga que quede a nombre de otra persona en la bitácora. Sin lógica: delega en los servicios.
 */
@Controller
public class ReportesController {

	static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ReportesCobranza reportes;

	private final ExportacionContador exportacion;

	public ReportesController(ReportesCobranza reportes, ExportacionContador exportacion) {
		this.reportes = reportes;
		this.exportacion = exportacion;
	}

	@GetMapping("/panel/morosos")
	@RegistraAcceso(TipoAcceso.MOROSOS)
	public String morosos(Model model) {
		var familias = reportes.familiasMorosas();
		model.addAttribute("familias", familias);
		AccesoMostrado.filas(familias.size());
		return "panel/morosos";
	}

	@GetMapping("/panel/reportes/morosidad")
	public String morosidad(@RequestParam(required = false) Long anio, Model model) {
		model.addAttribute("reporte", reportes.morosidadPorGrado(anio));
		return "panel/morosidad";
	}

	@GetMapping({ "/panel/reportes", "/panel/reportes/ingresos" })
	public String ingresos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, Model model) {
		try {
			model.addAttribute("reporte", reportes.ingresosPorMedio(desde, hasta));
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("reporte", reportes.ingresosPorMedio(null, null));
		}
		return "panel/ingresos";
	}

	@PostMapping("/panel/reportes/ingresos.xlsx")
	public Object exportarIngresos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, RedirectAttributes avisos) {
		try {
			return descarga(exportacion.exportarIngresos(desde, hasta));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/panel/reportes/ingresos";
		}
	}

	@PostMapping("/panel/reportes/morosidad.xlsx")
	public Object exportarMorosidad(@RequestParam(required = false) Long anio, RedirectAttributes avisos) {
		try {
			return descarga(exportacion.exportarMorosidad(anio));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/panel/reportes/morosidad";
		}
	}

	/** Los bytes salen solo después de que la exportación (y su evento en la bitácora) se confirmó. */
	private static ResponseEntity<byte[]> descarga(ArchivoExportado archivo) {
		return ResponseEntity.ok().contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre())
						.build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(archivo.contenido());
	}
}
