package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Sprint 7 (logs): cada petición recibe un id corto ({@code id_peticion}, 12 hexadecimales) que va en cada línea de log
 * de esa petición y en la página de error como «código de error». Con él, el operador encuentra lo que pasó sin que la
 * persona tenga que describir datos. Corre primero (antes de la seguridad) y también en el despacho de error, con el
 * mismo id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroIdPeticion extends OncePerRequestFilter {

	/** Atributo de la petición con el id (lo lee {@code CodigoErrorAdvice} para la página de error). */
	public static final String ATRIBUTO = FiltroIdPeticion.class.getName() + ".id";

	public static final String MDC_ID = "id_peticion";

	private static final SecureRandom AZAR = new SecureRandom();

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		Object existente = peticion.getAttribute(ATRIBUTO);
		String id = existente instanceof String texto ? texto : nuevoId();
		peticion.setAttribute(ATRIBUTO, id);
		String anterior = MDC.get(MDC_ID);
		MDC.put(MDC_ID, id);
		try {
			cadena.doFilter(peticion, respuesta);
		}
		finally {
			if (anterior == null) {
				MDC.remove(MDC_ID);
			}
			else {
				MDC.put(MDC_ID, anterior);
			}
		}
	}

	static String nuevoId() {
		byte[] bytes = new byte[6];
		AZAR.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
