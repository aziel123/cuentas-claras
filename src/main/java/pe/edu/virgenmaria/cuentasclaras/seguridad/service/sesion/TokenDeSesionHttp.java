package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * El secreto de la sesión, guardado al ingresar en un atributo de la sesión HTTP (memoria del servidor, nunca la cookie).
 * Sin petición (un proceso) o sin sesión, no hay secreto: nadie firma.
 */
@Component
public class TokenDeSesionHttp implements TokenDeSesion {

	/** Atributo de la sesión HTTP con la {@link SesionAbierta}. */
	public static final String ATRIBUTO = TokenDeSesionHttp.class.getName() + ".SESION";

	/**
	 * La sesión de una persona de la demo mientras dura su operación ({@link PersonaDemo}, perfiles dev y test). Solo
	 * la fija esa clase (es del paquete) y es una sesión real de la base: el trigger la valida igual.
	 */
	private static final ThreadLocal<SesionAbierta> DEMO = new ThreadLocal<>();

	static void fijarDemo(SesionAbierta sesion) {
		DEMO.set(sesion);
	}

	static void olvidarDemo() {
		DEMO.remove();
	}

	@Override
	public Optional<SesionAbierta> actual() {
		SesionAbierta demo = DEMO.get();
		if (demo != null) {
			return Optional.of(demo);
		}
		RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
		if (!(atributos instanceof ServletRequestAttributes servlet)) {
			return Optional.empty();
		}
		HttpSession sesion = servlet.getRequest().getSession(false);
		if (sesion == null) {
			return Optional.empty();
		}
		return sesion.getAttribute(ATRIBUTO) instanceof SesionAbierta abierta ? Optional.of(abierta) : Optional.empty();
	}

	/** Guarda la sesión de la base en la sesión HTTP (al ingresar). */
	public static void guardar(HttpSession sesion, SesionAbierta abierta) {
		sesion.setAttribute(ATRIBUTO, abierta);
	}

	/** La sesión de la base guardada en esa sesión HTTP, si tiene (al salir o al expirar). */
	public static Optional<SesionAbierta> de(HttpSession sesion) {
		try {
			return sesion != null && sesion.getAttribute(ATRIBUTO) instanceof SesionAbierta abierta ? Optional.of(abierta)
					: Optional.empty();
		}
		catch (IllegalStateException invalidada) {
			return Optional.empty();
		}
	}
}
