package pe.edu.virgenmaria.cuentasclaras.matricula.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;

/**
 * Campaña de renovación de matrícula del año siguiente (pantalla 9). Sin lógica: delega en
 * {@link ServicioCampanaRenovacion}, que exige el rol de cada acción (Administración abre y registra respuestas en
 * persona; Dirección cambia el destino).
 */
@Controller
public class CampanaRenovacionController {

	private static final String VOLVER = "redirect:/matricula-2027";

	private final ServicioCampanaRenovacion campana;

	public CampanaRenovacionController(ServicioCampanaRenovacion campana) {
		this.campana = campana;
	}

	@GetMapping("/matricula-2027")
	public String campana(@AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		campana.anioPlanificado().ifPresent(anio -> model.addAttribute("campana", campana.campana(anio)));
		model.addAttribute("puedeAbrir", tiene(sesion, Rol.ADMINISTRACION));
		model.addAttribute("puedeRegistrar", tiene(sesion, Rol.ADMINISTRACION));
		model.addAttribute("puedeCambiarDestino", tiene(sesion, Rol.DIRECTOR));
		return "matricula/campana";
	}

	@PostMapping("/matricula-2027/abrir")
	public String abrir(@RequestParam Long anioId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate venceEn,
			RedirectAttributes avisos) {
		try {
			int nuevas = campana.abrir(anioId, venceEn);
			avisos.addFlashAttribute("exito", "Listo: se propusieron " + nuevas + " renovaciones. Cada familia recibe su "
					+ "invitación por WhatsApp o correo.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/matricula-2027/{id:\\d+}/destino")
	public String destino(@PathVariable Long id, @RequestParam(required = false) Long seccionId,
			RedirectAttributes avisos) {
		try {
			campana.cambiarDestino(id, seccionId);
			avisos.addFlashAttribute("exito", "Listo: cambió el grado o la sección propuestos.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	@PostMapping("/matricula-2027/{id:\\d+}/presencial")
	public String presencial(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean continua,
			RedirectAttributes avisos) {
		try {
			campana.registrarPresencial(id, continua);
			avisos.addFlashAttribute("exito", "Listo: registraste la respuesta. La familia recibe un aviso con lo que "
					+ "registraste.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return VOLVER;
	}

	private static boolean tiene(UsuarioAutenticado sesion, Rol rol) {
		return sesion != null && sesion.roles().contains(rol);
	}
}
