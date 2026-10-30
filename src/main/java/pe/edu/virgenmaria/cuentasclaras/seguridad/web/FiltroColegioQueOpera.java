package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 11, tanda 1 (F10): en cada pedido, si quien está en sesión es del personal de un colegio que dejó de operar
 * (suspendido o dado de baja), la sesión se invalida (su sesión de la base ya la cerró la aplicación al suspender) y vuelve
 * al ingreso. Una familia sigue en modo consulta en SUSPENDIDO y en CERRADO hasta {@code conservar_hasta} (tanda 2). No es un bean: lo crea la cadena de seguridad.
 */
public class FiltroColegioQueOpera extends OncePerRequestFilter {

	private final ColegioRepository colegios;

	private final Clock reloj;

	public FiltroColegioQueOpera(ColegioRepository colegios, Clock reloj) {
		this.colegios = colegios;
		this.reloj = reloj;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
				&& !puedeSeguir(usuario)) {
			HttpSession sesion = peticion.getSession(false);
			if (sesion != null) {
				sesion.invalidate();
			}
			SecurityContextHolder.clearContext();
			respuesta.sendRedirect(peticion.getContextPath() + "/login?error");
			return;
		}
		cadena.doFilter(peticion, respuesta);
	}

	private boolean puedeSeguir(UsuarioAutenticado usuario) {
		Colegio colegio = colegios.findById(usuario.colegioId()).orElse(null);
		if (colegio == null || colegio.esPlataforma() || colegio.getEstado().opera()) {
			return true;
		}
		return usuario.apoderadoId() != null && colegio.consultaFamilias(LocalDate.now(reloj));
	}
}
