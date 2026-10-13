package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesEntorno;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Sprint 8, tanda 3 (sección 8, E20 a E23) con la aplicación completa en el perfil {@code demo} (más {@code test} para la
 * base de pruebas): la portada pública con la franja DEMO y sin indexar; «Crear mi demo» da un colegio ficticio en
 * producción con 6 cuentas (la cajera es Lucía Ramos), 40 familias, planes aprobados, la caja de hoy, una anulación
 * pendiente y los avisos a las familias; con la clave que se ve una vez se ingresa; un visitante no ve el colegio de otro;
 * la importación solo acepta los archivos de ejemplo; y la boleta dice «DEMO · SIN VALOR».
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "demo" })
class DemoWebTest {

	private static final Pattern CLAVE = Pattern.compile("data-clave-demo>.*?class=\"clave-temporal-valor num\">([a-z0-9-]+)<",
			Pattern.DOTALL);

	private static final Pattern USUARIO = Pattern.compile("<td class=\"num\">([a-z0-9]{4}\\.[a-z]+)</td>");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	/** Una demo creada por la web: su colegio, sus usuarios (en el orden de la tabla) y su clave. */
	record Demo(long colegioId, List<String> usuarios, String clave) {

		String usuario(String sufijo) {
			return usuarios.stream().filter(u -> u.endsWith("." + sufijo)).findFirst().orElseThrow();
		}
	}

	private Demo crearDemo(String ip) throws Exception {
		MvcResult resultado = mvc.perform(post("/demo").with(csrf()).with(r -> {
			r.setRemoteAddr(ip);
			return r;
		})).andExpect(status().isOk()).andExpect(view().name("demo/creada")).andReturn();
		String html = resultado.getResponse().getContentAsString();
		Matcher clave = CLAVE.matcher(html);
		assertThat(clave.find()).as("la clave se muestra").isTrue();
		List<String> usuarios = USUARIO.matcher(html).results().map(m -> m.group(1)).toList();
		long colegio = jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE nombre_usuario = ?", Long.class,
				usuarios.getFirst());
		return new Demo(colegio, usuarios, clave.group(1));
	}

	private MockHttpSession ingresar(String usuario, String clave) throws Exception {
		MvcResult resultado = mvc.perform(formLogin("/login").userParameter("usuario").user(usuario)
				.passwordParam("clave").password(clave)).andExpect(status().is3xxRedirection()).andReturn();
		assertThat(resultado.getResponse().getRedirectedUrl()).as("ingreso de " + usuario).doesNotContain("error");
		return (MockHttpSession) resultado.getRequest().getSession();
	}

	@Test
	void laPortadaEsPublicaConLaFranjaDemoYNingunBuscadorLaIndexa() throws Exception {
		mvc.perform(get("/demo")).andExpect(status().isOk())
				.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
				.andExpect(content().string(allOf(containsString("Crear mi demo"), containsString("No envía mensajes"),
						containsString("No cobra"), containsString("Se borra cada noche"), containsString("No escribas datos reales"),
						containsString(PropiedadesEntorno.FRANJA_DEMO), containsString("<title>[DEMO] "),
						containsString("/demo/guia"))));
		mvc.perform(get("/robots.txt")).andExpect(status().isOk()).andExpect(content().string(containsString("Disallow: /")));
		mvc.perform(get("/demo/guia")).andExpect(status().isOk()).andExpect(content().string(containsString("Lucía Ramos")));
		mvc.perform(get("/login")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
				.andExpect(content().string(containsString("Crea tu demo")));
		mvc.perform(get("/login").param("usuario", "abcd.caja"))
				.andExpect(content().string(containsString("value=\"abcd.caja\"")));
		// Una página con sesión exigida también lleva la cabecera (la redirección al ingreso).
		mvc.perform(get("/caja")).andExpect(status().is3xxRedirection())
				.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
		mvc.perform(get("/demo/ejemplos/ejemplo-importacion.xlsx")).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("ejemplo-importacion.xlsx")));
		mvc.perform(get("/demo/ejemplos/otro.xlsx")).andExpect(status().isNotFound());
	}

	@Test
	void crearMiDemoDaUnColegioFicticioConSeisCuentasYSusDatos() throws Exception {
		Demo demo = crearDemo("203.0.113.10");

		assertThat(demo.usuarios()).hasSize(6).allMatch(u -> u.matches("[a-z0-9]{4}\\.[a-z]+"));
		assertThat(demo.usuarios().stream().map(u -> u.substring(5)).toList()).containsExactly("promotoria", "direccion",
				"administracion", "caja", "docente", "apoderado");
		Map<String, Object> colegio = jdbc.queryForMap("SELECT nombre, estado, tipo, ruc FROM colegio WHERE id = ?",
				demo.colegioId());
		assertThat(colegio).containsEntry("estado", "PRODUCCION").containsEntry("tipo", "COLEGIO");
		assertThat((String) colegio.get("nombre")).startsWith("Colegio Demostración N.° ");
		assertThat(jdbc.queryForObject("SELECT nombre_completo FROM usuario WHERE nombre_usuario = ?", String.class,
				demo.usuario("caja"))).isEqualTo("Lucía Ramos");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM apoderado WHERE colegio_id = ?", Long.class, demo.colegioId()))
				.isEqualTo(40L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_pension WHERE colegio_id = ? AND estado = 'APROBADO'",
				Long.class, demo.colegioId())).isEqualTo(3L);
		// La caja de hoy: 4 cobros de Lucía (uno con su devolución pendiente), cada uno con su boleta y su aviso.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE colegio_id = ? AND cajero = ?", Long.class,
				demo.colegioId(), demo.usuario("caja"))).isEqualTo(4L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE colegio_id = ?", Long.class,
				demo.colegioId())).isGreaterThanOrEqualTo(4L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND tipo = 'PAGO_REGISTRADO'",
				Long.class, demo.colegioId())).isGreaterThanOrEqualTo(4L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE colegio_id = ? AND estado = 'PENDIENTE'",
				Long.class, demo.colegioId())).isGreaterThanOrEqualTo(1L);

		// Con la clave que se mostró una vez, cada rol entra (la cajera ve su caja; la Promotoría, su panel).
		MockHttpSession caja = ingresar(demo.usuario("caja"), demo.clave());
		mvc.perform(get("/caja").session(caja)).andExpect(status().isOk())
				.andExpect(content().string(containsString(PropiedadesEntorno.FRANJA_DEMO)));
		Long pago = jdbc.queryForObject("SELECT MIN(id) FROM pago WHERE colegio_id = ?", Long.class, demo.colegioId());
		mvc.perform(get("/caja/pagos/" + pago + "/comprobante").session(caja)).andExpect(status().isOk())
				.andExpect(content().string(containsString("DEMO · SIN VALOR")));
		MockHttpSession promotoria = ingresar(demo.usuario("promotoria"), demo.clave());
		mvc.perform(get("/panel").session(promotoria)).andExpect(status().isOk());
		mvc.perform(get("/mensajes").session(promotoria)).andExpect(status().isOk());
		ingresar(demo.usuario("apoderado"), demo.clave());
	}

	@Test
	void unVisitanteNoVeElColegioDeOtro() throws Exception {
		Demo a = crearDemo("203.0.113.20");
		Demo b = crearDemo("203.0.113.21");
		Long alumnoDeA = jdbc.queryForObject("SELECT MIN(id) FROM alumno WHERE colegio_id = ?", Long.class, a.colegioId());
		Long pagoDeA = jdbc.queryForObject("SELECT MIN(id) FROM pago WHERE colegio_id = ?", Long.class, a.colegioId());

		MockHttpSession administracionB = ingresar(b.usuario("administracion"), b.clave());
		mvc.perform(get("/alumnos/" + alumnoDeA).session(administracionB)).andExpect(status().isNotFound());
		mvc.perform(get("/alumnos").session(administracionB)).andExpect(status().isOk());
		MockHttpSession cajaB = ingresar(b.usuario("caja"), b.clave());
		mvc.perform(get("/caja/pagos/" + pagoDeA + "/comprobante").session(cajaB)).andExpect(status().isNotFound());
		// La sesión única no choca: la caja de A sigue entrando aunque la de B esté dentro.
		ingresar(a.usuario("caja"), a.clave());
	}

	@Test
	void laImportacionSoloAceptaLosArchivosDeEjemplo() throws Exception {
		Demo demo = crearDemo("203.0.113.30");
		MockHttpSession administracion = ingresar(demo.usuario("administracion"), demo.clave());
		Long anio = jdbc.queryForObject("SELECT id FROM anio_escolar WHERE colegio_id = ?", Long.class, demo.colegioId());
		String xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "mis-alumnos.xlsx", xlsx,
				ArchivoImportacion.archivo(ArchivoImportacion.mateo()))).param("anioId", anio.toString()).with(csrf())
				.session(administracion)).andExpect(status().isOk())
				.andExpect(content().string(containsString("En la demo solo se importa el archivo de ejemplo")));

		byte[] ejemplo = new ClassPathResource("demo/ejemplo-importacion.xlsx").getContentAsByteArray();
		mvc.perform(multipart("/alumnos/importar").file(new MockMultipartFile("archivo", "ejemplo-importacion.xlsx", xlsx,
				ejemplo)).param("anioId", anio.toString()).with(csrf()).session(administracion))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void tresDemosPorConexionAlDia() throws Exception {
		for (int i = 0; i < 3; i++) {
			crearDemo("198.51.100.7");
		}
		mvc.perform(post("/demo").with(csrf()).with(r -> {
			r.setRemoteAddr("198.51.100.7");
			return r;
		})).andExpect(status().isOk()).andExpect(view().name("demo/inicio"))
				.andExpect(content().string(allOf(containsString("ya creaste 3 demos hoy"),
						not(containsString("data-clave-demo")))));
	}
}
