package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 3 (A02, A05 y E22 de la auditoría web): las cabeceras de seguridad salen en TODA respuesta, con un
 * servidor y un cliente HTTP reales (también 302, 403, 404, 429 y 500, que no pasan por un controlador normal): CSP sin
 * scripts ni estilos en línea y sin iframes, HSTS de 1 año con subdominios y sin preload (explícito: aunque la petición
 * llegue por http desde el proxy), Cross-Origin-Opener-Policy y Cross-Origin-Resource-Policy, X-Frame-Options, nosniff,
 * Referrer-Policy y Permissions-Policy. La cookie de sesión es HttpOnly y SameSite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(InterfazBaseTest.ControladorQueFalla.class)
class CabecerasSeguridadTest {

	private static final Pattern TOKEN_CSRF = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@LocalServerPort
	private int puerto;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void todasLasRespuestasTraenLasCabecerasDeSeguridad() throws Exception {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		Map<String, HttpResponse<String>> respuestas = new LinkedHashMap<>();
		respuestas.put("200 /login", pedir(cliente, "/login"));
		respuestas.put("200 /privacidad", pedir(cliente, "/privacidad"));
		respuestas.put("302 sin sesión", pedir(cliente, "/inicio"));
		respuestas.put("403 POST sin CSRF", cliente.send(HttpRequest.newBuilder(uri("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("usuario=caja&clave=x")).build(), HttpResponse.BodyHandlers.ofString()));
		ingresar(cliente, "caja", UsuariosDePrueba.CLAVE);
		respuestas.put("200 con sesión", pedir(cliente, "/inicio"));
		respuestas.put("403 sin permiso", pedir(cliente, "/usuarios"));
		respuestas.put("404", pedir(cliente, "/caja/pagina-que-no-existe"));
		respuestas.put("500", pedir(cliente, InterfazBaseTest.ControladorQueFalla.RUTA));
		respuestas.put("200 estático", pedir(cliente, "/css/app.css"));

		assertThat(respuestas.get("200 /login").statusCode()).isEqualTo(200);
		assertThat(respuestas.get("302 sin sesión").statusCode()).isEqualTo(302);
		assertThat(respuestas.get("403 POST sin CSRF").statusCode()).isEqualTo(403);
		assertThat(respuestas.get("403 sin permiso").statusCode()).isEqualTo(403);
		assertThat(respuestas.get("404").statusCode()).isEqualTo(404);
		assertThat(respuestas.get("500").statusCode()).isEqualTo(500);
		respuestas.forEach((caso, r) -> {
			assertThat(r.headers().firstValue("Strict-Transport-Security")).as(caso).hasValueSatisfying(v -> assertThat(v)
					.contains("max-age=31536000").contains("includeSubDomains").doesNotContain("preload"));
			assertThat(r.headers().firstValue("Content-Security-Policy")).as(caso).hasValueSatisfying(v -> assertThat(v)
					.contains("default-src 'self'", "script-src 'self'", "style-src 'self'", "object-src 'none'",
							"frame-ancestors 'none'", "form-action 'self'", "base-uri 'self'")
					.doesNotContain("unsafe-inline", "unsafe-eval", "*"));
			assertThat(r.headers().firstValue("Cross-Origin-Opener-Policy")).as(caso).hasValue("same-origin");
			assertThat(r.headers().firstValue("Cross-Origin-Resource-Policy")).as(caso).hasValue("same-origin");
			assertThat(r.headers().firstValue("X-Frame-Options")).as(caso).hasValue("DENY");
			assertThat(r.headers().firstValue("X-Content-Type-Options")).as(caso).hasValue("nosniff");
			assertThat(r.headers().firstValue("Referrer-Policy")).as(caso).hasValue("strict-origin-when-cross-origin");
			assertThat(r.headers().firstValue("Permissions-Policy")).as(caso).hasValueSatisfying(v -> assertThat(v)
					.contains("camera=()", "microphone=()", "geolocation=()", "payment=()"));
		});
		// Las páginas con datos no se guardan en el caché del navegador ni de un proxy.
		assertThat(respuestas.get("200 con sesión").headers().firstValue("Cache-Control"))
				.hasValueSatisfying(v -> assertThat(v).contains("no-store"));
	}

	@Test
	void laCookieDeSesionEsHttpOnlyYSameSite() throws Exception {
		HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		HttpResponse<String> login = pedir(cliente, "/login");
		assertThat(login.headers().allValues("Set-Cookie")).anySatisfy(c -> assertThat(c)
				.startsWith("CCSESION=").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/")
				.doesNotContainIgnoringCase("Domain="));
	}

	private void ingresar(HttpClient cliente, String usuario, String clave) throws Exception {
		Matcher token = TOKEN_CSRF.matcher(pedir(cliente, "/login").body());
		assertThat(token.find()).isTrue();
		String cuerpo = "usuario=" + codificar(usuario) + "&clave=" + codificar(clave) + "&_csrf=" + codificar(token.group(1));
		HttpResponse<String> respuesta = cliente.send(HttpRequest.newBuilder(uri("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(respuesta.statusCode()).isEqualTo(302);
	}

	private HttpResponse<String> pedir(HttpClient cliente, String ruta) throws Exception {
		return cliente.send(HttpRequest.newBuilder(uri(ruta)).header("Accept", "text/html").GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String ruta) {
		return URI.create("http://localhost:" + puerto + ruta);
	}

	private static String codificar(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8);
	}
}
