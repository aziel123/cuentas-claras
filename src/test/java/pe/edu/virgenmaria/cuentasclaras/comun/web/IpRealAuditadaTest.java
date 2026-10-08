package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La IP auditada es la real: con la configuración de producción ({@code forward-headers-strategy: native} y
 * {@code internal-proxies}), una cabecera X-Forwarded-For solo se cree si llega desde un proxy de confianza.
 * Servidor y cliente HTTP reales: la cabecera la procesa Tomcat.
 */
class IpRealAuditadaTest {

	private static final String IP_FALSA = "203.0.113.9";

	/** El visitante (127.0.0.1) NO es un proxy de confianza: su X-Forwarded-For se ignora. */
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=10\\.9\\.9\\.9" })
	@ActiveProfiles("test")
	class VisitanteDirecto extends Escenario {

		@Test
		void unXForwardedForEnviadoPorElClienteNoReemplazaLaIpReal() throws Exception {
			ingresarCon(IP_FALSA);
			assertThat(ipAuditada()).isEqualTo("127.0.0.1");
		}
	}

	/** Detrás de un proxy de confianza (aquí, 127.0.0.1) sí se usa la IP que informa el proxy. */
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
			"server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1" })
	@ActiveProfiles("test")
	class DetrasDeUnProxyDeConfianza extends Escenario {

		@Test
		void seAuditaLaIpQueInformaElProxy() throws Exception {
			ingresarCon(IP_FALSA);
			assertThat(ipAuditada()).isEqualTo(IP_FALSA);
		}
	}

	abstract static class Escenario {

		private static final Pattern TOKEN_CSRF = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

		@LocalServerPort
		private int puerto;

		@Autowired
		private UsuarioRepository usuarios;

		@Autowired
		private PasswordEncoder codificador;

		@Autowired
		private JdbcTemplate jdbc;

		@BeforeEach
		void preparar() {
			LimpiezaBaseDatos.limpiar(jdbc);
			UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.ip", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		}

		@AfterEach
		void limpiar() {
			LimpiezaBaseDatos.limpiar(jdbc);
		}

		void ingresarCon(String xForwardedFor) throws Exception {
			HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
					.followRedirects(HttpClient.Redirect.NEVER).build();
			HttpResponse<String> formulario = cliente.send(HttpRequest.newBuilder(uri("/login")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			Matcher token = TOKEN_CSRF.matcher(formulario.body());
			assertThat(token.find()).isTrue();
			String cuerpo = "usuario=caja.ip&clave=" + URLEncoder.encode(UsuariosDePrueba.CLAVE, StandardCharsets.UTF_8)
					+ "&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
			HttpResponse<String> respuesta = cliente.send(HttpRequest.newBuilder(uri("/login"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("X-Forwarded-For", xForwardedFor)
					.POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
			assertThat(respuesta.statusCode()).isEqualTo(302);
		}

		String ipAuditada() {
			return jdbc.queryForObject("SELECT ip FROM evento_auditoria WHERE accion = 'INGRESO_EXITOSO'", String.class);
		}

		private URI uri(String ruta) {
			return URI.create("http://127.0.0.1:" + puerto + ruta);
		}
	}
}
