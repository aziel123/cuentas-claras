package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.LimiteIngresos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioDetallesUsuario;

import java.io.IOException;
import java.util.Optional;

/**
 * Antes del formulario de ingreso (sprint 7, tanda 3; H9): si esta conexión debe esperar (demasiados intentos fallidos,
 * {@link LimiteIngresos}), responde 429 con la página de ingreso y su aviso, SIN autenticar: el intento no se cuenta
 * contra la cuenta. Solo mira {@code POST /login}. No es un bean (lo crea {@code ConfiguracionSeguridad} dentro de la
 * cadena de seguridad, después del CSRF).
 */
public class FiltroLimiteIngresos extends OncePerRequestFilter {

	/** Atributo de la petición con el aviso que muestra la página de ingreso. */
	public static final String ATRIBUTO_MENSAJE = FiltroLimiteIngresos.class.getName() + ".MENSAJE";

	/** A dónde se reenvía la petición que debe esperar (la atiende {@code LoginController}). */
	public static final String RUTA_ESPERA = "/login/demasiados-intentos";

	private final LimiteIngresos limite;

	public FiltroLimiteIngresos(LimiteIngresos limite) {
		this.limite = limite;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest peticion) {
		return !("POST".equals(peticion.getMethod())
				&& "/login".equals(UrlPathHelper.defaultInstance.getPathWithinApplication(peticion)));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		Optional<LimiteIngresos.Motivo> espera = limite.espera(Actor.normalizarIp(peticion.getRemoteAddr()),
				ServicioDetallesUsuario.normalizar(peticion.getParameter("usuario")));
		if (espera.isEmpty()) {
			cadena.doFilter(peticion, respuesta);
			return;
		}
		peticion.setAttribute(ATRIBUTO_MENSAJE, espera.get().mensaje(limite.minutos()));
		respuesta.setStatus(429);
		peticion.getRequestDispatcher(RUTA_ESPERA).forward(peticion, respuesta);
	}
}
