package pe.edu.virgenmaria.cuentasclaras.auditoria.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.FiltroBitacora;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.HuellaBitacora;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;

/**
 * Bitácora de auditoría (solo lectura). El único POST es la verificación de integridad, solo para
 * Promotoría (lo exige también {@code @PreAuthorize} en el verificador).
 */
@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

	private static final String VISTA = "auditoria/lista";

	private final ConsultaAuditoriaService consulta;

	private final VerificadorIntegridadAuditoria verificador;

	public AuditoriaController(ConsultaAuditoriaService consulta, VerificadorIntegridadAuditoria verificador) {
		this.consulta = consulta;
		this.verificador = verificador;
	}

	@GetMapping
	public String bitacora(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) String usuario, @RequestParam(required = false) AccionAuditoria accion,
			@RequestParam(defaultValue = "false") boolean soloRevisar, @RequestParam(defaultValue = "0") int pagina,
			@AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		ConsultaAuditoriaService.RangoFechas porDefecto = consulta.rangoPorDefecto();
		FiltroBitacora filtro = new FiltroBitacora(desde == null ? porDefecto.desde() : desde,
				hasta == null ? porDefecto.hasta() : hasta, usuario, accion, soloRevisar);
		prepararPantalla(model, filtro, sesion);
		try {
			model.addAttribute("eventos", consulta.listar(filtro, pagina));
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return VISTA;
	}

	@PostMapping("/verificar-integridad")
	public String verificarIntegridad(@RequestParam(required = false) String huellaSecuencia,
			@RequestParam(required = false) String huellaCodigo, RedirectAttributes avisos) {
		HuellaBitacora anotada;
		try {
			anotada = HuellaBitacora.anotada(numero(huellaSecuencia), huellaCodigo);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("errorHuella", e.getMessage());
			return "redirect:/auditoria";
		}
		avisos.addFlashAttribute("verificacion", verificador.verificar(anotada));
		return "redirect:/auditoria";
	}

	/** Una fecha o filtro mal escrito en la URL muestra un aviso, no una página de error. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public String filtroMalEscrito(@AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		ConsultaAuditoriaService.RangoFechas porDefecto = consulta.rangoPorDefecto();
		prepararPantalla(model, FiltroBitacora.fechas(porDefecto.desde(), porDefecto.hasta()), sesion);
		model.addAttribute("error", "Revisa los filtros: usa el calendario para elegir las fechas.");
		return VISTA;
	}

	private static void prepararPantalla(Model model, FiltroBitacora filtro, UsuarioAutenticado sesion) {
		model.addAttribute("filtro", filtro);
		model.addAttribute("acciones", AccionAuditoria.values());
		model.addAttribute("puedeVerificar", sesion != null && sesion.roles().contains(Rol.PROMOTOR));
	}

	private static Long numero(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return Long.valueOf(texto.strip());
		}
		catch (NumberFormatException e) {
			throw new ReglaNegocioException("El número de evento de la huella debe ser un número.");
		}
	}
}
