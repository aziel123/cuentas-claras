package pe.edu.virgenmaria.cuentasclaras.academico.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.DelegacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Sprint 10, tanda 3 (secciones 3.10 y 3.13; A6, M12): la exportación de calificaciones para el SIAGIE (Dirección o la
 * persona de Administración delegada: lo exige el servicio) y la delegación (solo Dirección, con su firma). Sin lógica:
 * delega en {@link CalificacionesSiagie} y {@link DelegacionesSiagie}. Cada descarga queda en «Quién vio datos personales»
 * (CALIFICACIONES_SIAGIE, con la cantidad).
 */
@Controller
public class CalificacionesSiagieController {

	private final CalificacionesSiagie calificaciones;

	private final DelegacionesSiagie delegaciones;

	public CalificacionesSiagieController(CalificacionesSiagie calificaciones, DelegacionesSiagie delegaciones) {
		this.calificaciones = calificaciones;
		this.delegaciones = delegaciones;
	}

	@GetMapping(path = "/academico/siagie/calificaciones", params = "!seccion")
	public String inicio(Model model) {
		CalificacionesSiagie.Vista vista = calificaciones.vista();
		model.addAttribute("vista", vista);
		if (vista.direccion()) {
			model.addAttribute("delegaciones", delegaciones.vigentes());
			model.addAttribute("candidatas", delegaciones.candidatas());
		}
		return "academico/siagie-calificaciones";
	}

	@GetMapping(path = "/academico/siagie/calificaciones", params = { "seccion", "periodo" })
	@RegistraAcceso(TipoAcceso.CALIFICACIONES_SIAGIE)
	public ResponseEntity<byte[]> descargar(@RequestParam("seccion") long seccion, @RequestParam("periodo") long periodo) {
		CalificacionesSiagie.Archivo archivo;
		try {
			archivo = calificaciones.exportar(seccion, periodo);
		}
		catch (ReglaNegocioException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
					.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
					.body(e.getMessage().getBytes(StandardCharsets.UTF_8));
		}
		AccesoMostrado.filas(archivo.filas());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.nombreArchivo()).build().toString())
				.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore().cachePrivate())
				.body(archivo.contenido());
	}

	@PostMapping("/academico/siagie/delegaciones")
	public String delegar(@RequestParam("usuario") Long usuario,
			@RequestParam("desde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam("hasta") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			RedirectAttributes avisos) {
		try {
			delegaciones.delegar(usuario, desde, hasta);
			avisos.addFlashAttribute("exito", "Listo: delegaste la exportación para el SIAGIE con tu firma. Vence sola.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/academico/siagie/calificaciones";
	}
}
