package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mientras se cargan los datos de demostración (la aplicación aún no está lista) nadie ingresa: la carga actúa con la
 * sesión de las personas de la demo y, con la sesión única, cerraba la de quien entraba con la misma cuenta.
 */
class EsperaDatosDemoDevTest {

	private final ApplicationAvailability disponibilidad = mock(ApplicationAvailability.class);

	private final EsperaDatosDemoDev filtro = new EsperaDatosDemoDev(disponibilidad);

	@Test
	void duranteLaCargaElIngresoRecibe503SinLlegarALaSeguridad() throws Exception {
		when(disponibilidad.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
		MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/login");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		FilterChain cadena = mock(FilterChain.class);

		filtro.doFilter(peticion, respuesta, cadena);

		assertThat(respuesta.getStatus()).isEqualTo(503);
		assertThat(respuesta.getHeader("Retry-After")).isEqualTo("3");
		assertThat(respuesta.getContentAsString()).contains("Preparando los datos de demostración");
		verify(cadena, never()).doFilter(any(), any());
	}

	@Test
	void conLaAplicacionListaPasaALaCadena() throws Exception {
		when(disponibilidad.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/login");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		FilterChain cadena = mock(FilterChain.class);

		filtro.doFilter(peticion, respuesta, cadena);

		assertThat(respuesta.getStatus()).isEqualTo(200);
		verify(cadena).doFilter(peticion, respuesta);
	}
}
