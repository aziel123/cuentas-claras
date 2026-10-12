package pe.edu.virgenmaria.cuentasclaras.demo.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sprint 8, tanda 3 (sección 8.1): en la demo, toda respuesta (también las redirecciones al ingreso y los errores) lleva
 * {@code X-Robots-Tag: noindex, nofollow}: ningún buscador la indexa. Va antes que la cadena de seguridad.
 */
@Component
@Profile("demo")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoIndexarDemo extends OncePerRequestFilter {

	static final String CABECERA = "X-Robots-Tag";

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		respuesta.setHeader(CABECERA, "noindex, nofollow");
		cadena.doFilter(peticion, respuesta);
	}
}
