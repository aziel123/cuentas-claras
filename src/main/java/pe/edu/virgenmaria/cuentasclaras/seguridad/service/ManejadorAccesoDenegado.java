package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responde 403 (página en español) y audita {@code ACCESO_DENEGADO} con el método y la ruta, sin la
 * query string (puede traer datos).
 * <ul>
 *   <li>Solo audita a usuarios con sesión: un visitante anónimo no puede llenar la bitácora con peticiones
 *       sin token CSRF.</li>
 *   <li>Como máximo un evento por usuario y ruta por minuto (en memoria): repetir la misma petición no
 *       inunda la bitácora ni frena a los demás registros de la cadena.</li>
 * </ul>
 */
@Component
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

	static final Duration INTERVALO = Duration.ofMinutes(1);

	private static final int MAXIMO_ENTRADAS = 10_000;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final AccessDeniedHandler respuesta403 = new AccessDeniedHandlerImpl();

	private final Map<String, Instant> ultimoRegistro = new ConcurrentHashMap<>();

	public ManejadorAccesoDenegado(AuditoriaService auditoria, Clock reloj) {
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException excepcion)
			throws IOException, ServletException {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.isAuthenticated()
				&& !(autenticacion instanceof AnonymousAuthenticationToken)) {
			String ruta = request.getMethod() + " " + request.getRequestURI();
			if (tocaRegistrar(autenticacion.getName() + "|" + ruta)) {
				auditoria.registrar(AccionAuditoria.ACCESO_DENEGADO, null, null, null, null, ruta);
			}
		}
		respuesta403.handle(request, response, excepcion);
	}

	private boolean tocaRegistrar(String clave) {
		Instant ahora = reloj.instant();
		if (ultimoRegistro.size() > MAXIMO_ENTRADAS) {
			ultimoRegistro.values().removeIf(instante -> instante.isBefore(ahora.minus(INTERVALO)));
		}
		boolean[] registrar = { false };
		ultimoRegistro.compute(clave, (k, anterior) -> {
			if (anterior == null || !ahora.isBefore(anterior.plus(INTERVALO))) {
				registrar[0] = true;
				return ahora;
			}
			return anterior;
		});
		return registrar[0];
	}
}
