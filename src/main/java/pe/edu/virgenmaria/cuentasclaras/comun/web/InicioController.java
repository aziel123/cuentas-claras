package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioInicio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Locale;

/**
 * Página de inicio según el rol principal del usuario (prioridad: PROMOTOR, DIRECTOR,
 * ADMINISTRACION, CAJA, DOCENTE, APODERADO). El menú y las tarjetas los agrega {@code ModeloSesionAdvice}.
 */
@Controller
public class InicioController {

	private final ServicioInicio servicioInicio;

	private final ObjectProvider<AlertasRevision> alertas;

	private final ObjectProvider<IndicadoresInicio> indicadores;

	public InicioController(ServicioInicio servicioInicio, ObjectProvider<AlertasRevision> alertas,
			ObjectProvider<IndicadoresInicio> indicadores) {
		this.servicioInicio = servicioInicio;
		this.alertas = alertas;
		this.indicadores = indicadores;
	}

	@GetMapping("/")
	public String raiz() {
		return "redirect:/inicio";
	}

	@GetMapping("/inicio")
	public String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("avisoClave", servicioInicio.avisoClaveRestablecida(usuario.usuarioId()));
		Rol principal = usuario.rolPrincipal();
		if (principal == Rol.PROMOTOR) {
			model.addAttribute("paraRevisar", servicioInicio.paraRevisar());
			// Las críticas primero (un faltante de caja antes que una matrícula sin cronograma).
			model.addAttribute("alertasRevision", alertas.orderedStream().flatMap(a -> a.alertas().stream())
					.sorted(AlertaRevision.POR_GRAVEDAD).toList());
			model.addAttribute("indicadores", indicadores.orderedStream().toList());
		}
		return "inicio/" + principal.name().toLowerCase(Locale.ROOT);
	}
}
