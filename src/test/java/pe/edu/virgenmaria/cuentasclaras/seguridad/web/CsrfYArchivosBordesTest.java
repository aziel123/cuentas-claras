package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 7 (E23 y E25; CSRF y subida de archivos hostiles), además de {@code CsrfEnTodoPostTest} y
 * {@code ArchivosHostilesTest}: el token de una sesión no sirve en otra, salir por GET no cierra la sesión, los demás
 * verbos también exigen el token, una subida sin token no guarda nada, los webhooks sin firma no se aceptan y el nombre de
 * un archivo con rutas de Windows o caracteres invisibles no llega al disco ni a la base.
 */
@PruebaIntegracion
class CsrfYArchivosBordesTest {

	private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elTokenCsrfDeUnaSesionNoSirveEnOtra() throws Exception {
		MockHttpSession atacante = new MockHttpSession();
		String token = tokenDe(atacante);
		MockHttpSession victima = new MockHttpSession();
		tokenDe(victima);

		mvc.perform(post("/login").session(victima).param("_csrf", token).param("usuario", "directora")
				.param("clave", UsuariosDePrueba.CLAVE)).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario", Long.class)).isZero();
	}

	@Test
	void salirPorGetNoCierraLaSesion() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(post("/login").session(sesion).param("usuario", "directora").param("clave", UsuariosDePrueba.CLAVE)
				.with(csrf())).andExpect(redirectedUrl("/inicio"));

		mvc.perform(get("/salir").session(sesion));

		mvc.perform(get("/inicio").session(sesion)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE cerrada_en IS NULL", Long.class))
				.isEqualTo(1);
	}

	@Test
	void losDemasVerbosTambienExigenElToken() throws Exception {
		mvc.perform(put("/usuarios/1").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isForbidden());
		mvc.perform(delete("/caja/pagos/1").with(UsuariosDePrueba.como(Rol.CAJA))).andExpect(status().isForbidden());
		mvc.perform(patch("/aprobaciones/1/aprobar").with(UsuariosDePrueba.como(Rol.DIRECTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unaSubidaSinTokenNoGuardaNadaNiDejaVistaPrevia() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		byte[] csv = "fecha;descripcion;monto\n01/10/2026;ABONO;450.00\n".getBytes(StandardCharsets.UTF_8);

		mvc.perform(multipart("/conciliacion/extractos/vista-previa")
				.file(new MockMultipartFile("archivo", "extracto.csv", "text/csv", csv)).session(sesion)
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))).andExpect(status().isForbidden());

		assertThat(java.util.Collections.list(sesion.getAttributeNames())).as("ninguna vista previa en la sesión")
				.noneMatch(n -> n.toLowerCase().contains("previa") || n.toLowerCase().contains("extracto"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM archivo_cargado", Long.class)).isZero();
	}

	@Test
	void unAvisoDeWhatsAppSinFirmaNoSeAceptaAunqueNoLleveToken() throws Exception {
		mvc.perform(post("/webhooks/whatsapp/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.1\",\"status\":\"read\"}]}}]}]}"))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ServicioImportacionAlumnos",
			"pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos",
			"pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion" })
	void elNombreDelArchivoNoLlevaRutasNiCaracteresInvisibles(String clase) throws Exception {
		Method nombreSeguro = Class.forName(clase).getDeclaredMethod("nombreSeguro", String.class);
		nombreSeguro.setAccessible(true);

		for (String hostil : List.of("..\\..\\Windows\\System32\\extracto.csv", "../../etc/extracto.csv",
				"C:\\Users\\caja\\extracto.csv", "extracto.csv\u0000.exe", "extra\u202Evsc.exe", "\u0007\u0008extracto.csv")) {
			String limpio = (String) nombreSeguro.invoke(null, hostil);
			assertThat(limpio).as(hostil).doesNotContain("/").doesNotContain("\\").doesNotContain("..")
					.doesNotContain("\u0000").doesNotContain("\u202E").doesNotContain("\u0007");
			assertThat(limpio.length()).isLessThanOrEqualTo(150);
		}
		assertThat((String) nombreSeguro.invoke(null, "x".repeat(400) + ".csv")).hasSize(150).endsWith(".csv");
	}

	private String tokenDe(MockHttpSession sesion) throws Exception {
		String pagina = mvc.perform(get("/login").session(sesion)).andReturn().getResponse().getContentAsString();
		Matcher m = TOKEN.matcher(pagina);
		assertThat(m.find()).as("la página de ingreso trae el token CSRF").isTrue();
		return m.group(1);
	}
}
