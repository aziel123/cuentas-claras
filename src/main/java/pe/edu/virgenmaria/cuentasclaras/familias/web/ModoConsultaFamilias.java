package pe.edu.virgenmaria.cuentasclaras.familias.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Set;

/**
 * Sprint 11, tanda 1 (sección 3.8): en un colegio SUSPENDIDO (o CERRADO) las familias entran en modo consulta: ven su
 * estado de cuenta, sus boletas, las libretas y los comunicados, pero no pagan ni cambian nada. Todo pedido que escribe
 * (POST, PUT, PATCH o DELETE) de una cuenta de familia responde 403, salvo cambiar su clave ({@code /salir} lo atiende la
 * cadena de seguridad antes de llegar aquí; activar el portal y restablecer la clave son rutas públicas). El personal ni
 * siquiera llega: lo corta {@code FiltroColegioQueOpera}.
 */
public class ModoConsultaFamilias implements HandlerInterceptor {

	/** Lo que una familia sí puede escribir en modo consulta. */
	static final Set<String> PERMITIDAS = Set.of("/cuenta/cambiar-clave", "/salir");

	private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");

	private final ObjectProvider<ColegioRepository> colegios;

	public ModoConsultaFamilias(ObjectProvider<ColegioRepository> colegios) {
		this.colegios = colegios;
	}

	@Override
	public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador)
			throws Exception {
		if (LECTURA.contains(peticion.getMethod())) {
			return true;
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)
				|| usuario.apoderadoId() == null) {
			return true;
		}
		String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
		if (PERMITIDAS.contains(ruta)) {
			return true;
		}
		ColegioRepository repositorio = colegios.getIfAvailable();
		Colegio colegio = repositorio == null ? null : repositorio.findById(usuario.colegioId()).orElse(null);
		if (colegio == null || colegio.getEstado().opera()) {
			return true;
		}
		respuesta.sendError(HttpStatus.FORBIDDEN.value(), "El portal está en modo consulta.");
		return false;
	}
}
