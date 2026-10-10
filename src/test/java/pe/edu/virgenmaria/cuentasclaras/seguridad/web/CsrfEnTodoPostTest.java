package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sprint 7, tanda 3 (E23): TODO POST de la aplicación sin el token CSRF responde 403, también con una persona en sesión con
 * el rol que esa ruta permite (lo único que falta es el token). La única excepción son los avisos firmados de la pasarela y
 * de WhatsApp ({@code /webhooks/**}, cadena aparte sin sesión). Recorre los mapeos reales: una ruta nueva entra sola.
 */
@PruebaIntegracion
class CsrfEnTodoPostTest {

	/** Valores que pasan los patrones de las rutas (el id 1; los tokens, del largo que exigen). */
	private static final Map<String, String> VALORES = RutasDeLaAplicacion.valores("token", "a".repeat(43),
			"referencia", "00000000-0000-0000-0000-000000000001", "accion", "PAGADO", "proveedor", "SIMULADA");

	@Autowired
	private MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void todoPostSinTokenCsrfResponde403SalvoLosAvisosFirmados() throws Exception {
		List<String> errores = new ArrayList<>();
		List<String> webhooks = new ArrayList<>();
		int revisadas = 0;
		long eventosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion <> 'ACCESO_DENEGADO'",
				Long.class);
		List<String> rutas = new ArrayList<>(RutasDeLaAplicacion.todas(mapeos).stream()
				.filter(r -> r.metodo().equals("POST")).map(r -> r.concreta(VALORES)).toList());
		// El ingreso y la salida los atiende Spring Security (no son métodos de un controlador).
		rutas.addAll(List.of("/login", "/salir"));
		for (String ruta : rutas) {
			if (ruta.startsWith("/webhooks/")) {
				webhooks.add(ruta);
				continue;
			}
			List<Rol> roles = RutasDeLaAplicacion.roles(ruta);
			MockHttpServletRequestBuilder pedido = post(ruta).param("motivo", "Prueba de CSRF sin token")
					.param("id", "1").param("anioId", "1").param("familiaId", "1");
			if (!roles.isEmpty()) {
				pedido = pedido.with(UsuariosDePrueba.como(roles.getFirst()));
			}
			int estado = mvc.perform(pedido).andReturn().getResponse().getStatus();
			revisadas++;
			if (estado != 403) {
				errores.add("POST " + ruta + (roles.isEmpty() ? " (sin sesión)" : " como " + roles.getFirst())
						+ " sin token respondió " + estado);
			}
		}
		assertThat(errores).as("todo POST exige el token CSRF").isEmpty();
		assertThat(revisadas).as("se revisaron todas las acciones de la aplicación").isGreaterThan(100);
		assertThat(webhooks).as("las únicas sin CSRF son los avisos firmados (cadena de webhooks)")
				.allMatch(r -> r.startsWith("/webhooks/pasarela/") || r.startsWith("/webhooks/whatsapp/"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion <> 'ACCESO_DENEGADO'",
				Long.class)).as("ninguna acción se hizo").isEqualTo(eventosAntes);
	}

	@Test
	void laCadenaDeAvisosNoPideCsrfPeroSoloAceptaSuRuta() throws Exception {
		// Un POST a /webhooks/** fuera de las rutas de aviso se niega (denyAll), con o sin token.
		assertThat(mvc.perform(post("/webhooks/cualquier-cosa")).andReturn().getResponse().getStatus()).isIn(401, 403);
		assertThat(ModuloApp.RUTAS_WEBHOOK).isEqualTo("/webhooks/**");
	}
}
