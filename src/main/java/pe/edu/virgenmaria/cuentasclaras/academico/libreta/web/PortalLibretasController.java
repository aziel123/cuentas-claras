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

	/**
	 * Correcciones del sprint 10 (hallazgo 5, INDECOPI): el aviso de «en revisión» llega SOLO como atributo flash que calcula
	 * el servidor al intentar la descarga; un parámetro de la URL (como el antiguo {@code ?revision=}) no muestra nada, así
	 * que un enlace manipulado no puede decirle a una familia que su libreta está retenida.
	 */
	@GetMapping("/familia/libretas")
	public String lista(Model model) {
		model.addAttribute("vista", libretas.lista());
		return "familia/libretas";
	}

	@GetMapping("/familia/libretas/{id:\\d+}.pdf")
	public ResponseEntity<byte[]> descargar(@PathVariable long id, jakarta.servlet.http.HttpServletRequest peticion,
			jakarta.servlet.http.HttpServletResponse respuesta) {
		Descarga d;
		try {
			d = libretas.descargar(id);
		}
		catch (GeneradorLibretaPdf.ColaLlenaException e) {
			return DescargaLibreta.ocupado(e.getMessage());
		}
		if (d.enRevision()) {
			// El mensaje viaja en el flash de la sesión (lo calculó el servidor), nunca en la URL.
			org.springframework.web.servlet.FlashMap flash = org.springframework.web.servlet.support.RequestContextUtils
					.getOutputFlashMap(peticion);
			flash.put("error", "Su libreta está en revisión. El colegio se la entregará a más tardar el "
					+ d.enRevisionHasta().format(FECHA) + ".");
			flash.setTargetRequestPath("/familia/libretas");
			org.springframework.web.servlet.support.RequestContextUtils.saveOutputFlashMap("/familia/libretas", peticion,
					respuesta);
			return ResponseEntity.status(303).location(URI.create("/familia/libretas")).build();
		}
		return DescargaLibreta.pdf(d.nombreArchivo(), d.pdf());
	}
}
