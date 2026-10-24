package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * En desarrollo, los datos de demostración se cargan después de que el servidor ya escucha (son ApplicationRunner) y
 * actúan como las personas de la demo con su sesión de la base ({@code PersonaDemo}). Con la sesión única, si alguien
 * ingresaba como «promotor» durante la carga, la carga le cerraba la sesión. Hasta que Spring Boot declara la aplicación
 * lista (ReadinessState ACCEPTING_TRAFFIC, después del último ApplicationRunner) toda petición recibe un 503 con una
 * página que se recarga sola: nadie ingresa antes de que terminen. Solo en el perfil {@code dev}; va antes de la cadena de
 * seguridad.
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class EsperaDatosDemoDev extends OncePerRequestFilter {

	static final String PAGINA = """
			<!doctype html>
			<html lang="es"><head><meta charset="utf-8"><meta http-equiv="refresh" content="3">
			<meta name="viewport" content="width=device-width, initial-scale=1"><title>Preparando la demostración</title>
			</head><body><h1>Preparando los datos de demostración</h1>
			<p>La aplicación ya arrancó y está cargando los datos de ejemplo. Esta página se recarga sola en unos segundos.</p>
			</body></html>
			""";

	private final ApplicationAvailability disponibilidad;

	public EsperaDatosDemoDev(ApplicationAvailability disponibilidad) {
		this.disponibilidad = disponibilidad;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		if (disponibilidad.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
			cadena.doFilter(peticion, respuesta);
			return;
		}
		respuesta.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		respuesta.setHeader("Retry-After", "3");
		respuesta.setHeader("Cache-Control", "no-store");
		respuesta.setContentType("text/html;charset=UTF-8");
		respuesta.getOutputStream().write(PAGINA.getBytes(StandardCharsets.UTF_8));
	}
}
