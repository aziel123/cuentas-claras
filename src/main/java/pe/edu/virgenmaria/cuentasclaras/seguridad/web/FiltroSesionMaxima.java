package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorIngresoExitoso;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Tiempo máximo de una sesión (sprint 7, tanda 3; A07 de OWASP, decisión 83): pasadas 10 horas desde el ingreso, la
 * sesión se cierra aunque haya actividad (un celular olvidado con la sesión abierta deja de servir). Invalidar la sesión
 * HTTP cierra también la sesión de la base (VENCIO), así su secreto ya no firma aprobaciones. Además siguen los 30 minutos
 * sin actividad del contenedor. No es un bean (lo crea {@code ConfiguracionSeguridad} dentro de la cadena de seguridad).
 */
public class FiltroSesionMaxima extends OncePerRequestFilter {

	private final Duration vigenciaMaxima;

	private final Clock reloj;

	public FiltroSesionMaxima(Duration vigenciaMaxima, Clock reloj) {
		this.vigenciaMaxima = vigenciaMaxima;
		this.reloj = reloj;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		HttpSession sesion = peticion.getSession(false);
		if (sesion != null && sesion.getAttribute(ManejadorIngresoExitoso.ATRIBUTO_INGRESO) instanceof Instant ingreso
				&& !reloj.instant().isBefore(ingreso.plus(vigenciaMaxima))) {
			sesion.invalidate();
			SecurityContextHolder.clearContext();
			respuesta.sendRedirect(peticion.getContextPath() + "/login?expirada");
			return;
		}
		cadena.doFilter(peticion, respuesta);
	}
}
