package pe.edu.virgenmaria.cuentasclaras.comun.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioEnPreparacionException;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Set;

/**
 * Sprint 11, tanda 1b (sección 3.14, punto 3): lo bloqueado por la preparación se explica, no termina en un error.
 * <ul>
 *   <li>{@code colegioEnPreparacion} en el modelo: las pantallas deshabilitan «Cobrar», «Enviar», «Confirmar» o «Abrir la
 *       renovación» con su motivo al lado, antes de enviar nada;</li>
 *   <li>una {@link ColegioEnPreparacionException} que no atrapó su controlador (o un 1644 de preparación de la base) responde
 *       409 con el motivo y, al personal que puede abrirla, el enlace a la Puesta en marcha y cuántos pasos faltan. A las
 *       familias, solo «El colegio todavía no recibe pagos por aquí», sin enlace ni pasos.</li>
 * </ul>
 */
@ControllerAdvice
public class PreparacionAdvice {

	static final String VISTA = "error/preparacion";

	private final ColegioService colegios;

	private final EstadoPuestaEnMarcha estado;

	public PreparacionAdvice(ColegioService colegios, EstadoPuestaEnMarcha estado) {
		this.colegios = colegios;
		this.estado = estado;
	}

	@ModelAttribute("colegioEnPreparacion")
	public boolean colegioEnPreparacion() {
		Long colegioId = ContextoColegio.actual();
		return colegioId != null && colegioId > 0 && autenticado() && colegios.enPreparacion(colegioId);
	}

	@ExceptionHandler(ColegioEnPreparacionException.class)
	public ModelAndView enPreparacion(ColegioEnPreparacionException error, HttpServletResponse respuesta) {
		return vista(error, respuesta);
	}

	/** Un 1644 de preparación de la base (MySQL) se muestra igual; cualquier otro error sigue su camino. */
	@ExceptionHandler(DataAccessException.class)
	public ModelAndView desdeLaBase(DataAccessException error, HttpServletResponse respuesta) {
		return vista(ColegioEnPreparacionException.desdeBase(error).orElseThrow(() -> error), respuesta);
	}

	private ModelAndView vista(ColegioEnPreparacionException error, HttpServletResponse respuesta) {
		respuesta.setStatus(HttpServletResponse.SC_CONFLICT);
		Set<Rol> roles = roles();
		boolean familia = roles.contains(Rol.APODERADO) || roles.isEmpty();
		ModelAndView vista = new ModelAndView(VISTA);
		vista.setStatus(org.springframework.http.HttpStatus.CONFLICT);
		vista.addObject("mensajePreparacion", familia ? "El colegio todavía no recibe pagos por aquí." : error.getMessage());
		vista.addObject("esFamiliaPreparacion", familia);
		boolean conEnlace = !familia && ModuloApp.puedeEntrar(roles, "/puesta-en-marcha");
		vista.addObject("enlacePuestaEnMarcha", conEnlace);
		vista.addObject("faltanPasos", conEnlace ? estado.actual().pendientes() : null);
		return vista;
	}

	private static boolean autenticado() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a != null && a.isAuthenticated() && !ModuloApp.rolesDe(a.getAuthorities()).isEmpty();
	}

	private static Set<Rol> roles() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a == null ? Set.of() : ModuloApp.rolesDe(a.getAuthorities());
	}
}
