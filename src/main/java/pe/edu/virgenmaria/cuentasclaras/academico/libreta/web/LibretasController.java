package pe.edu.virgenmaria.cuentasclaras.academico.libreta.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf.GeneradorLibretaPdf;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasColegio;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ReemisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.VerificacionLibreta;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

import java.time.format.DateTimeFormatter;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; M9, A4): las libretas para el colegio. Dirección ve, imprime (una o la sección)
 * y verifica códigos; el tutor imprime las de su sección (lo exige el servicio: otra, 404). Sin lógica: delega en
 * {@link LibretasColegio}, {@link VerificacionLibreta} y {@link ReemisionLibretas}. Cada libreta impresa o verificada queda en
 * «Quién vio datos personales» (LIBRETA; la sección, NOTAS_SECCION con la cantidad).
 */
@Controller
public class LibretasController {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final LibretasColegio libretas;

	private final VerificacionLibreta verificacion;

	private final ReemisionLibretas reemision;

	public LibretasController(LibretasColegio libretas, VerificacionLibreta verificacion, ReemisionLibretas reemision) {
		this.libretas = libretas;
		this.verificacion = verificacion;
		this.reemision = reemision;
	}

	@GetMapping("/academico/libretas")
	public String inicio(@RequestParam(name = "seccion", required = false) Long seccion,
			@RequestParam(name = "periodo", required = false) Long periodo, Model model) {
		model.addAttribute("vista", libretas.inicio(seccion, periodo));
		return "academico/libretas/inicio";
	}

	@GetMapping("/academico/libretas/{id:\\d+}.pdf")
	@RegistraAcceso(TipoAcceso.LIBRETA)
	public ResponseEntity<byte[]> imprimir(@PathVariable long id) {
		Descarga d;
		try {
			d = libretas.imprimir(id);
		}
		catch (GeneradorLibretaPdf.ColaLlenaException e) {
			return DescargaLibreta.ocupado(e.getMessage());
		}
		AccesoMostrado.alumno(d.alumnoId(), d.familiaId());
		if (d.enRevision()) {
			return DescargaLibreta.ocupado("La libreta está en revisión porque no coincide con su huella. Pide su "
					+ "reemisión: el colegio debe entregarla a más tardar el " + d.enRevisionHasta().format(FECHA) + ".");
		}
		return DescargaLibreta.pdf(d.nombreArchivo(), d.pdf());
	}

	@GetMapping("/academico/libretas/seccion.pdf")
	@RegistraAcceso(TipoAcceso.NOTAS_SECCION)
	public ResponseEntity<byte[]> imprimirSeccion(@RequestParam("seccion") long seccion,
			@RequestParam("periodo") long periodo) {
		Descarga d;
		try {
			d = libretas.imprimirSeccion(seccion, periodo);
		}
		catch (GeneradorLibretaPdf.ColaLlenaException e) {
			return DescargaLibreta.ocupado(e.getMessage());
		}
		catch (ReglaNegocioException e) {
			return DescargaLibreta.rechazada(e.getMessage());
		}
		AccesoMostrado.filas(d.libretas());
		return DescargaLibreta.pdf(d.nombreArchivo(), d.pdf());
	}

	@GetMapping("/academico/libretas/verificar")
	@RegistraAcceso(TipoAcceso.LIBRETA)
	public String verificar(@RequestParam(name = "codigo", required = false) String codigo, Model model) {
		if (codigo != null && !codigo.isBlank()) {
			VerificacionVista resultado = verificacion.verificar(codigo.length() > 60 ? codigo.substring(0, 60) : codigo);
			if (resultado.existe()) {
				AccesoMostrado.alumno(resultado.alumnoId(), resultado.familiaId());
			}
			model.addAttribute("resultado", resultado);
		}
		return "academico/libretas/verificar";
	}

	@PostMapping("/academico/libretas/{id:\\d+}/reemision")
	public String pedirReemision(@PathVariable long id, @RequestParam(name = "motivo", required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			reemision.pedir(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste la reemisión. Promotoría la aprueba en su bandeja y el sistema "
					+ "emite la versión nueva con aviso a la familia.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/academico/libretas/verificar";
	}
}
