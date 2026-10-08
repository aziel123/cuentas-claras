package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con Tomcat REAL y un cliente HTTP real (MockMvc no lo demuestra): el filtro CSRF lee el token {@code _csrf} de un
 * formulario multipart, y un archivo más grande que el límite del contenedor (5 MB) recibe
 * con una página clara en español (413) en vez de cortar la conexión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImportacionTomcatRealTest {

	private static final Pattern TOKEN_CSRF = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@LocalServerPort
	private int puerto;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	private HttpClient cliente;

	private Long anio2026;

	@BeforeEach
	void preparar() throws Exception {
		LimpiezaBaseDatos.limpiar(jdbc);
		Usuario admin = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "admin.importa", UsuariosDePrueba.CLAVE,
				false, Rol.ADMINISTRACION);
		UsuariosDePrueba.iniciarSesion(admin);
		anio2026 = EscenarioEscolar.crearEstructura(estructura).anio2026();
		SecurityContextHolder.clearContext();
		cliente = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		ingresar("admin.importa");
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void subirConTokenCsrfFuncionaEnTomcatReal() throws Exception {
		HttpResponse<String> respuesta = subir(ArchivoImportacion.archivo(ArchivoImportacion.mateo()), tokenDeSubida());

		assertThat(respuesta.statusCode()).isEqualTo(302);
		assertThat(respuesta.headers().firstValue("Location")).hasValueSatisfying(
				l -> assertThat(l).endsWith("/alumnos/importar/revision"));
		HttpResponse<String> revision = pedir("/alumnos/importar/revision");
		assertThat(revision.statusCode()).isEqualTo(200);
		assertThat(revision.body()).contains("1 fila leídas", "Confirmar e importar");
	}

	@Test
	void subirSinTokenCsrfEsRechazadoSinProcesarElArchivo() throws Exception {
		HttpResponse<String> respuesta = subir(ArchivoImportacion.archivo(ArchivoImportacion.mateo()), null);

		assertThat(respuesta.statusCode()).isEqualTo(403);
		assertThat(respuesta.body()).contains("No tienes permiso");
		assertThat(pedir("/alumnos/importar/revision").headers().firstValue("Location"))
				.as("no quedó ninguna revisión en la sesión").hasValueSatisfying(l -> assertThat(l).endsWith("/alumnos/importar"));
	}

	@Test
	void archivoDe3MbMuestraMensajeClaro() throws Exception {
		byte[] grande = Arrays.copyOf(ArchivoImportacion.archivo(ArchivoImportacion.mateo()), 3 * 1024 * 1024);
		HttpResponse<String> respuesta = subir(grande, tokenDeSubida());

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("El archivo pesa 3 MB y el máximo es 2 MB");
	}

	@Test
	void archivoMayorAlLimiteDelContenedorVuelveConUnAviso() throws Exception {
		byte[] enorme = Arrays.copyOf(ArchivoImportacion.archivo(ArchivoImportacion.mateo()), 8 * 1024 * 1024);
		HttpResponse<String> respuesta = subir(enorme, tokenDeSubida());

		// Tomcat no lee un envío de más de 6 MB: responde 413 con nuestra página en español (sin detalles técnicos) y
		// un enlace para volver a subir. No se procesa nada y la sesión sigue viva.
		assertThat(respuesta.statusCode()).isEqualTo(413);
		assertThat(respuesta.body()).contains("El archivo es demasiado grande", "hasta 2 MB", "Volver a subir")
				.doesNotContain("Exception", "Content Too Large", "timestamp");
		assertThat(pedir("/alumnos/importar").statusCode()).isEqualTo(200);
	}

	private String tokenDeSubida() throws Exception {
		HttpResponse<String> pagina = pedir("/alumnos/importar");
		assertThat(pagina.statusCode()).isEqualTo(200);
		Matcher token = TOKEN_CSRF.matcher(pagina.body().substring(pagina.body().indexOf("multipart/form-data")));
		assertThat(token.find()).as("el formulario multipart trae el token CSRF").isTrue();
		return token.group(1);
	}

	private HttpResponse<String> subir(byte[] archivo, String token) throws IOException, InterruptedException {
		String limite = "----limite" + System.nanoTime();
		ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
		if (token != null) {
			campo(cuerpo, limite, "_csrf", token);
		}
		campo(cuerpo, limite, "anioId", anio2026.toString());
		cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"alumnos.xlsx\"\r\n"
				+ "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8));
		cuerpo.write(archivo);
		cuerpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return cliente.send(HttpRequest.newBuilder(uri("/alumnos/importar"))
						.header("Content-Type", "multipart/form-data; boundary=" + limite).header("Accept", "text/html")
						.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray())).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static void campo(ByteArrayOutputStream cuerpo, String limite, String nombre, String valor) throws IOException {
		cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + nombre + "\"\r\n\r\n" + valor
				+ "\r\n").getBytes(StandardCharsets.UTF_8));
	}

	private void ingresar(String usuario) throws Exception {
		Matcher token = TOKEN_CSRF.matcher(pedir("/login").body());
		assertThat(token.find()).isTrue();
		String cuerpo = "usuario=" + codificar(usuario) + "&clave=" + codificar(UsuariosDePrueba.CLAVE) + "&_csrf="
				+ codificar(token.group(1));
		HttpResponse<String> respuesta = cliente.send(HttpRequest.newBuilder(uri("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(respuesta.statusCode()).isEqualTo(302);
		assertThat(respuesta.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/inicio"));
	}

	private HttpResponse<String> pedir(String ruta) throws Exception {
		return cliente.send(HttpRequest.newBuilder(uri(ruta)).header("Accept", "text/html").GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String ruta) {
		return URI.create("http://localhost:" + puerto + ruta);
	}

	private static String codificar(String valor) {
		return URLEncoder.encode(valor, StandardCharsets.UTF_8);
	}
}
