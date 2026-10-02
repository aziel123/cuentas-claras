package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Interfaz base: menú por rol, inicio por rol, reglas de las plantillas (CSP y accesibilidad)
 * y páginas de error en español, estas últimas con un servidor y un cliente HTTP reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(InterfazBaseTest.ControladorQueFalla.class)
class InterfazBaseTest {

	/** Solo para la prueba: una ruta permitida para Caja que falla de verdad con una excepción. */
	@org.springframework.stereotype.Controller
	static class ControladorQueFalla {

		static final String RUTA = "/caja/prueba-de-falla-interna";

		@org.springframework.web.bind.annotation.GetMapping(RUTA)
		String fallar() {
			throw new IllegalStateException("java.lang.IllegalStateException: detalle técnico secreto");
		}
	}

	private static final Pattern TOKEN_CSRF = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SpringTemplateEngine motorPlantillas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@LocalServerPort
	private int puerto;

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void menuDeCajaNoMuestraUsuariosNiAuditoria() throws Exception {
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(Rol.CAJA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-modulo=\"CAJA_COBRO\"")))
				.andExpect(content().string(not(containsString("data-modulo=\"USUARIOS\""))))
				.andExpect(content().string(not(containsString("data-modulo=\"AUDITORIA\""))))
				.andExpect(content().string(not(containsString("data-modulo=\"APROBACIONES\""))));
	}

	@Test
	void menuDePromotorMuestraUsuariosYAuditoria() throws Exception {
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-modulo=\"USUARIOS\"")))
				.andExpect(content().string(containsString("data-modulo=\"AUDITORIA\"")))
				.andExpect(content().string(not(containsString("data-modulo=\"CAJA_COBRO\""))))
				.andExpect(content().string(containsString("aria-current=\"page\"")))
				.andExpect(content().string(containsString("Próximamente")));
	}

	@Test
	void menuDeAdministracionMuestraColegioYAlumnos() throws Exception {
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(Rol.ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/colegio\" data-modulo=\"COLEGIO\"")))
				.andExpect(content().string(containsString("href=\"/alumnos\" data-modulo=\"ALUMNOS\"")))
				.andExpect(content().string(not(containsString("href=\"/pensiones\""))))
				.andExpect(content().string(not(containsString("data-modulo=\"USUARIOS\""))));
		// En una página del módulo, el menú marca el módulo actual.
		mvc.perform(get("/alumnos").with(UsuariosDePrueba.como(Rol.ADMINISTRACION)))
				.andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
						"(?s).*href=\"/alumnos\" data-modulo=\"ALUMNOS\"\\s+aria-current=\"page\".*")));
	}

	@ParameterizedTest
	@EnumSource(Rol.class)
	void cadaRolVeSuPaginaDeInicio(Rol rol) throws Exception {
		String nombre = rol.name().toLowerCase(Locale.ROOT);
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(rol)))
				.andExpect(status().isOk())
				.andExpect(view().name("inicio/" + nombre))
				.andExpect(content().string(containsString("data-inicio=\"" + nombre + "\"")))
				.andExpect(content().string(containsString("lang=\"es\"")))
				.andExpect(content().string(containsString(rol.etiqueta())))
				.andExpect(content().string(containsString("Cerrar sesión")));
	}

	@Test
	void conVariosRolesManaElDeMayorPrioridadYElMenuLosSuma() throws Exception {
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(Rol.DIRECTOR, Rol.DOCENTE)))
				.andExpect(view().name("inicio/director"))
				.andExpect(content().string(containsString("data-modulo=\"ACADEMICO\"")))
				.andExpect(content().string(containsString("data-modulo=\"APROBACIONES\"")));
	}

	@Test
	void enCelularLaCuentaVaDentroDelMenuDesplegable() throws Exception {
		mvc.perform(get("/inicio").with(UsuariosDePrueba.como(Rol.CAJA)))
				.andExpect(content().string(containsString("<details class=\"menu-movil\">")))
				.andExpect(content().string(containsString("class=\"sesion-movil\"")));
		// Con clave pendiente no hay menú, pero en celular igual debe poder cerrar sesión.
		mvc.perform(get("/cuenta/cambiar-clave").with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(1L, 1L,
						"nuevo", "Nuevo Usuario", true, java.util.EnumSet.of(Rol.DOCENTE)))))
				.andExpect(content().string(containsString("class=\"sesion-movil\"")))
				.andExpect(content().string(not(containsString("class=\"menu-lateral\""))));
	}

	@Test
	void elMenuNoSeDesbordaYLaCabeceraSeCompactaEnCelular() throws IOException {
		String css = new PathMatchingResourcePatternResolver().getResource("classpath:static/css/app.css")
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(css).as("la columna del menú no crece con etiquetas largas")
				.containsPattern("\\.menu \\{[^}]*grid-template-columns: minmax\\(0, 1fr\\)");
		assertThat(css).as("en celular la barra no muestra las acciones de la cuenta")
				.containsPattern("@media \\(max-width: 860px\\)[\\s\\S]*\\.barra \\.sesion \\{ display: none; \\}");
	}

	@Test
	void plantillasNoUsanThUtext() throws IOException {
		assertThat(buscarEnPlantillas(Pattern.compile("th:utext"))).isEmpty();
	}

	@Test
	void plantillasNoUsanConfirmNiOnclick() throws IOException {
		assertThat(buscarEnPlantillas(Pattern.compile("(?i)\\son[a-z]+\\s*=|confirm\\s*\\(|javascript:"))).isEmpty();
	}

	@Test
	void plantillasNoTienenEstilosEnLinea() throws IOException {
		assertThat(buscarEnPlantillas(Pattern.compile("(?i)\\s(th:)?style\\s*=|<style|<script(?![^>]*\\ssrc=)")))
				.isEmpty();
	}

	@Test
	void cssSoloUsaTokens() throws IOException {
		Resource app = new PathMatchingResourcePatternResolver().getResource("classpath:static/css/app.css");
		String css = app.getContentAsString(StandardCharsets.UTF_8);
		assertThat(css).isNotBlank();
		assertThat(Pattern.compile("#[0-9a-fA-F]{3,8}\\b|rgba?\\(|hsla?\\(").matcher(css).find())
				.as("app.css no define colores: usa las variables de tokens.css").isFalse();
	}

	@Test
	void componentesSinJavaScriptConMotivoObligatorio() {
		Context contexto = new Context(Locale.forLanguageTag("es-PE"), Map.of("accion", "/aprobaciones/1/anular"));
		String html = motorPlantillas.process("prueba/componentes", contexto);

		assertThat(html)
				.contains("popovertarget=\"modal-anular\"", "popover", "popovertargetaction=\"hide\"")
				.contains("action=\"/aprobaciones/1/anular\"", "method=\"post\"")
				.contains("name=\"motivo\"", "required", "minlength=\"10\"")
				.contains("class=\"badge badge-exito\">Pagado<")
				.contains("Todavía no hay pagos hoy")
				.contains("role=\"alert\"", "role=\"status\"")
				.doesNotContain("<script", "onclick");
	}

	@Test
	void paginas403y404y500EnEspanolSinDetallesTecnicos() throws Exception {
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		HttpClient cliente = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		ingresar(cliente, "caja", UsuariosDePrueba.CLAVE);

		HttpResponse<String> prohibida = pedir(cliente, "/usuarios");
		assertThat(prohibida.statusCode()).isEqualTo(403);
		assertThat(prohibida.body()).contains("No tienes permiso para ver esta página");

		HttpResponse<String> inexistente = pedir(cliente, "/caja/pagina-que-no-existe");
		assertThat(inexistente.statusCode()).isEqualTo(404);
		assertThat(inexistente.body()).contains("No encontramos esta página");

		HttpResponse<String> falla = pedir(cliente, ControladorQueFalla.RUTA);
		assertThat(falla.statusCode()).isEqualTo(500);
		assertThat(falla.body()).contains("Algo salió mal");

		for (HttpResponse<String> respuesta : List.of(prohibida, inexistente, falla)) {
			assertThat(respuesta.body())
					.contains("lang=\"es\"", "Volver al inicio")
					.doesNotContain("Whitelabel", "Exception", "exception", "org.springframework", "trace", "secreto",
							"ControladorQueFalla",
							"Forbidden", "Not Found", "Internal Server Error", "timestamp");
			assertThat(respuesta.headers().firstValue("Content-Security-Policy")).isPresent();
		}
	}

	private void ingresar(HttpClient cliente, String usuario, String clave) throws Exception {
		HttpResponse<String> formulario = pedir(cliente, "/login");
		Matcher token = TOKEN_CSRF.matcher(formulario.body());
		assertThat(token.find()).as("el formulario de ingreso trae el token CSRF").isTrue();
		String cuerpo = "usuario=" + codificar(usuario) + "&clave=" + codificar(clave) + "&_csrf="
				+ codificar(token.group(1));
		HttpResponse<String> respuesta = cliente.send(HttpRequest.newBuilder(uri("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(respuesta.statusCode()).isEqualTo(302);
		assertThat(respuesta.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/inicio"));
	}

	private HttpResponse<String> pedir(HttpClient cliente, String ruta) throws Exception {
		return cliente.send(HttpRequest.newBuilder(uri(ruta)).header("Accept", "text/html").GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String ruta) {
		return URI.create("http://localhost:" + puerto + ruta);
	}

	private static String codificar(String valor) {
		return URLEncoder.encode(valor, StandardCharsets.UTF_8);
	}

	private static List<String> buscarEnPlantillas(Pattern prohibido) throws IOException {
		Resource[] plantillas = new PathMatchingResourcePatternResolver().getResources("classpath*:templates/**/*.html");
		assertThat(plantillas).as("se encontraron las plantillas").hasSizeGreaterThan(10);
		List<String> hallazgos = new ArrayList<>();
		for (Resource plantilla : plantillas) {
			String texto = plantilla.getContentAsString(StandardCharsets.UTF_8);
			Matcher m = prohibido.matcher(texto);
			while (m.find()) {
				hallazgos.add(plantilla.getFilename() + ": " + m.group());
			}
		}
		return hallazgos;
	}
}
