package pe.edu.virgenmaria.cuentasclaras.plataforma.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FinDeColegio;

import java.time.LocalDate;

/**
 * Sprint 11, tanda 2 (3.9 a 3.11): el fin de un colegio para el operador: sus exportaciones (pedirla, la de cierre, volver
 * a mandar el enlace), la retención legal y el acta de supresión (proponer, aprobar, verificar la cadena e imprimirla). Sin
 * lógica: delega en {@link FinDeColegio}.
 */
@Controller
@RequestMapping("/plataforma/colegios/{id:\\d+}/fin")
public class FinColegioController {

	private final FinDeColegio fin;

	public FinColegioController(FinDeColegio fin) {
		this.fin = fin;
	}

	@GetMapping
	public String ver(@PathVariable Long id, Model model) {
		model.addAttribute("fin", fin.ver(id));
		return "plataforma/fin-colegio";
	}

	@PostMapping("/exportacion")
	public String pedir(@PathVariable Long id, @RequestParam(required = false) String referencia,
			RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.pedirExportacion(id, referencia), "Listo: se generó la exportación. El enlace "
				+ "salió solo por correo al titular y las huellas, al correo de control.");
	}

	@PostMapping("/exportacion-cierre")
	public String cierre(@PathVariable Long id, RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.exportarParaCierre(id), "Listo: se generó la exportación de cierre. El cierre "
				+ "se propone cuando el titular la descargue o pasen 30 días.");
	}

	@PostMapping("/exportacion/{exportacion:\\d+}/reenviar")
	public String reenviar(@PathVariable Long id, @PathVariable Long exportacion, RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.reenviarEntrega(id, exportacion), "Listo: salió un enlace nuevo por correo al "
				+ "titular; el anterior ya no sirve.");
	}

	@PostMapping("/retencion")
	public String retencion(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.ponerRetencion(id, motivo), "Listo: el colegio tiene retención legal. La quita "
				+ "otro operador.");
	}

	@PostMapping("/retencion/quitar")
	public String quitarRetencion(@PathVariable Long id, RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.quitarRetencion(id), "Listo: quitaste la retención legal.");
	}

	@PostMapping("/acta")
	public String proponerActa(@PathVariable Long id, @RequestParam(required = false) String motivo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate respaldosVencenEl,
			RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.proponerActa(id, motivo, respaldosVencenEl), "Listo: propusiste el acta. La "
				+ "aprueba otro operador, con su propia sesión.");
	}

	@PostMapping("/acta/{acta:\\d+}/aprobar")
	public String aprobarActa(@PathVariable Long id, @PathVariable Long acta, RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.aprobarActa(id, acta), "Listo: aprobaste el acta. La ejecuta el DBA con "
				+ "suprimir.sql cuando venza el plazo.");
	}

	@PostMapping("/acta/cadena")
	public String verificarCadena(@PathVariable Long id, RedirectAttributes avisos) {
		return hacer(id, avisos, () -> fin.verificarCadena(id), "Listo: la cadena salió íntegra. El DBA tiene 1 hora para "
				+ "ejecutar el acta.");
	}

	@GetMapping("/acta.pdf")
	public ResponseEntity<byte[]> actaPdf(@PathVariable Long id) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename("acta-supresion-" + id + ".pdf").build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(fin.actaPdf(id));
	}

	private static String hacer(Long id, RedirectAttributes avisos, Runnable accion, String exito) {
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id + "/fin";
	}
}
