package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroDireccion;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroPromotoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.GuiaPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioInicio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Página de inicio según el rol principal del usuario (prioridad: PROMOTOR, DIRECTOR,
 * ADMINISTRACION, CAJA, DOCENTE, APODERADO). El menú y las tarjetas los agrega {@code ModeloSesionAdvice}.
 */
@Controller
public class InicioController {

	private final ServicioInicio servicioInicio;

	private final ObjectProvider<AlertasRevision> alertas;

	private final ObjectProvider<IndicadoresInicio> indicadores;

	private final ObjectProvider<TableroPromotoria> tableroPromotoria;

	private final ObjectProvider<TableroDireccion> tableroDireccion;

	private final ColegioService colegios;

	private final GuiaPuestaEnMarcha guia;

	public InicioController(ServicioInicio servicioInicio, ObjectProvider<AlertasRevision> alertas,
			ObjectProvider<IndicadoresInicio> indicadores, ObjectProvider<TableroPromotoria> tableroPromotoria,
			ObjectProvider<TableroDireccion> tableroDireccion, ColegioService colegios, GuiaPuestaEnMarcha guia) {
		this.colegios = colegios;
		this.guia = guia;
		this.servicioInicio = servicioInicio;
		this.alertas = alertas;
		this.indicadores = indicadores;
		this.tableroPromotoria = tableroPromotoria;
		this.tableroDireccion = tableroDireccion;
	}

	@GetMapping("/")
	public String raiz() {
		return "redirect:/inicio";
	}

	@GetMapping("/inicio")
	public String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		Rol principal = usuario.rolPrincipal();
		// Sprint 8, tanda 1: el operador de la plataforma no tiene inicio de colegio: su pantalla es /plataforma.
		if (principal == Rol.OPERADOR_PLATAFORMA) {
			return "redirect:/plataforma";
		}
		model.addAttribute("avisoClave", servicioInicio.avisoClaveRestablecida(usuario.usuarioId()));
		// Sprint 11, tanda 1b (secciones 3.14 y decisión 210): en PREPARACION domina la guía; el tablero (cifras y gráficos)
		// no se calcula: estaría en ceros. Las familias nunca ven la preparación.
		boolean preparacion = principal != Rol.APODERADO && colegios.enPreparacion(ContextoColegio.actual());
		if (preparacion) {
			model.addAttribute("guia", guia.guiaPara(usuario.roles()));
		}
		if (principal == Rol.PROMOTOR) {
			model.addAttribute("paraRevisar", servicioInicio.paraRevisar());
			// Las críticas primero (un faltante de caja antes que una matrícula sin cronograma).
			List<AlertaRevision> todas = alertas.orderedStream().flatMap(a -> a.alertas().stream())
					.sorted(AlertaRevision.POR_GRAVEDAD).toList();
			model.addAttribute("alertasRevision", todas);
			// Rediseño (tablero): las críticas siempre a la vista; los avisos, agrupados por área y desplegables.
			model.addAttribute("criticasRevision", todas.stream()
					.filter(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA).toList());
			model.addAttribute("avisosPorArea", porArea(todas));
			model.addAttribute("indicadores", preparacion ? List.of() : indicadores.orderedStream().toList());
			TableroPromotoria tablero = tableroPromotoria.getIfAvailable();
			if (tablero != null && !preparacion) {
				model.addAttribute("tablero", tablero.tablero());
			}
		}
		if (principal == Rol.DIRECTOR) {
			TableroDireccion tablero = tableroDireccion.getIfAvailable();
			if (tablero != null && !preparacion) {
				model.addAttribute("tablero", tablero.tablero());
			}
		}
		return "inicio/" + principal.name().toLowerCase(Locale.ROOT);
	}

	/** Los avisos (todo lo que no es crítico) por área, en el orden en que aparece cada área. */
	static Map<String, List<AlertaRevision>> porArea(List<AlertaRevision> todas) {
		Map<String, List<AlertaRevision>> porArea = new LinkedHashMap<>();
		todas.stream().filter(a -> a.gravedad() != AlertaRevision.Gravedad.CRITICA)
				.forEach(a -> porArea.computeIfAbsent(a.modulo(), m -> new java.util.ArrayList<>()).add(a));
		return porArea;
	}
}
