package pe.edu.virgenmaria.cuentasclaras.academico.libreta.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf.GeneradorLibretaPdf;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasFamilia;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.8): las libretas en el portal de la familia, dentro de {@code academico} (que no
 * depende de cobranza: la libreta nunca se retiene por deuda). Sin lógica: delega en {@link LibretasFamilia} (rol
 * APODERADO; otra familia: 404).
 */
@Controller
public class PortalLibretasController {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final LibretasFamilia libretas;

	public PortalLibretasController(LibretasFamilia libretas) {
		this.libretas = libretas;
	}

	/** {@code revision}: la fecha de entrega de una libreta en revisión (A3), después de intentar descargarla. */
	@GetMapping("/familia/libretas")
	public String lista(@RequestParam(name = "revision", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate revision, Model model) {
		model.addAttribute("vista", libretas.lista());
		if (revision != null) {
			model.addAttribute("error", "Su libreta está en revisión. El colegio se la entregará a más tardar el "
					+ revision.format(FECHA) + ".");
		}
		return "familia/libretas";
	}

	@GetMapping("/familia/libretas/{id:\\d+}.pdf")
	public ResponseEntity<byte[]> descargar(@PathVariable long id) {
		Descarga d;
		try {
			d = libretas.descargar(id);
		}
		catch (GeneradorLibretaPdf.ColaLlenaException e) {
			return DescargaLibreta.ocupado(e.getMessage());
		}
		if (d.enRevision()) {
			return ResponseEntity.status(303).location(URI.create("/familia/libretas?revision=" + d.enRevisionHasta()))
					.build();
		}
		return DescargaLibreta.pdf(d.nombreArchivo(), d.pdf());
	}
}
