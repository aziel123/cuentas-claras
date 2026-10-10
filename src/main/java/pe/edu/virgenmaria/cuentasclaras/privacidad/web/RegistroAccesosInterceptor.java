package pe.edu.virgenmaria.cuentasclaras.privacidad.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AccesosDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Registra quién ve datos personales (sprint 7, tanda 3; Ley 29733, sección 8.2) en las pantallas marcadas con
 * {@link RegistraAcceso}. Corre después del controlador y ANTES de mostrar la página: si el registro falla, la página no
 * se muestra (falla cerrado). No registra redirecciones (errores de formulario, acciones), respuestas que no son 200 ni a
 * las familias que ven sus propios datos.
 */
public class RegistroAccesosInterceptor implements HandlerInterceptor {

	private final AccesosDatosPersonales accesos;

	public RegistroAccesosInterceptor(AccesosDatosPersonales accesos) {
		this.accesos = accesos;
	}

	@Override
	public void postHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador,
			ModelAndView vista) {
		if (!(manejador instanceof HandlerMethod metodo)) {
			return;
		}
		RegistraAcceso marca = metodo.getMethodAnnotation(RegistraAcceso.class);
		if (marca == null || respuesta.getStatus() != HttpServletResponse.SC_OK || esRedireccion(vista)
				|| !esPersonal()) {
			return;
		}
		AccesoMostrado mostrado = peticion.getAttribute(AccesoMostrado.ATRIBUTO) instanceof AccesoMostrado a ? a : null;
		accesos.registrar(marca.value(), mostrado, Actor.normalizarIp(peticion.getRemoteAddr()));
	}

	private static boolean esRedireccion(ModelAndView vista) {
		if (vista == null) {
			return false;
		}
		String nombre = vista.getViewName();
		return (nombre != null && (nombre.startsWith("redirect:") || nombre.startsWith("forward:")))
				|| vista.getView() instanceof RedirectView;
	}

	private static boolean esPersonal() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
				&& !usuario.roles().isEmpty() && !usuario.roles().contains(Rol.APODERADO);
	}
}
