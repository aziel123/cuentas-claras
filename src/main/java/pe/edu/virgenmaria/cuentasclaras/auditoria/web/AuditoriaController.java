package pe.edu.virgenmaria.cuentasclaras.auditoria.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
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

	private final ConsultaAuditoriaService consulta;

	private final VerificadorIntegridadAuditoria verificador;

	public AuditoriaController(ConsultaAuditoriaService consulta, VerificadorIntegridadAuditoria verificador) {
		this.consulta = consulta;
		this.verificador = verificador;
	}

	@GetMapping
	public String bitacora(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "0") int pagina, @AuthenticationPrincipal UsuarioAutenticado usuario,
			Model model) {
		ConsultaAuditoriaService.RangoFechas porDefecto = consulta.rangoPorDefecto();
		LocalDate inicio = desde == null ? porDefecto.desde() : desde;
		LocalDate fin = hasta == null ? porDefecto.hasta() : hasta;
		model.addAttribute("desde", inicio);
		model.addAttribute("hasta", fin);
		model.addAttribute("puedeVerificar", usuario.roles().contains(Rol.PROMOTOR));
		try {
			model.addAttribute("eventos", consulta.listar(inicio, fin, pagina));
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return "auditoria/lista";
	}

	@PostMapping("/verificar-integridad")
	public String verificarIntegridad(RedirectAttributes avisos) {
		avisos.addFlashAttribute("verificacion", verificador.verificar());
		return "redirect:/auditoria";
	}
}
