package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 10: los pasos 9 y 10 de la guía de la demo ({@code /demo/guia}), recorridos con las cuentas de una demo
 * creada por la web en octubre. El docente abre sus notas (el I bimestre cerrado y el II abierto), la familia de la cuenta
 * «apoderado» descarga su libreta en PDF, y Dirección verifica un código y descarga el Excel para el SIAGIE (marcado DEMO).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "demo" })
@Import(ConfiguracionRelojAjustable.class)
class QaSprint10DemoGuiaTest {

	private static final Pattern CLAVE = Pattern.compile("data-clave-demo>.*?class=\"clave-temporal-valor num\">([a-z0-9-]+)<",
			Pattern.DOTALL);

	private static final Pattern USUARIO = Pattern.compile("<td class=\"num\">([a-z0-9]{4}\\.[a-z]+)</td>");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RelojAjustable reloj;

	@Test
	void debeRecorrerLosPasosNueveYDiezDeLaGuiaConLasCuentasDeLaDemo() throws Exception {
		reloj.fijar(Instant.parse("2026-10-21T14:00:00Z"));
		MvcResult creada = mvc.perform(post("/demo").with(csrf()).with(r -> {
			r.setRemoteAddr("198.51.100.77");
			return r;
		})).andExpect(status().isOk()).andReturn();
		String html = creada.getResponse().getContentAsString();
		Matcher m = CLAVE.matcher(html);
		assertThat(m.find()).isTrue();
		String clave = m.group(1);
		List<String> usuarios = USUARIO.matcher(html).results().map(r -> r.group(1)).toList();
		long colegio = jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE nombre_usuario = ?", Long.class,
				usuarios.getFirst());

		// Paso 9: el docente abre «Notas» y la hoja de Matemática de 4.° Primaria A.
		MockHttpSession docente = ingresar(usuario(usuarios, "docente"), clave);
		String notas = mvc.perform(get("/academico/notas").session(docente)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		Matcher hoja = Pattern.compile("/academico/notas/(\\d+)/(\\d+)").matcher(notas);
		assertThat(hoja.find()).as("el docente tiene su hoja de Matemática").isTrue();
		mvc.perform(get(hoja.group()).session(docente)).andExpect(status().isOk());

		// Paso 10: la familia de la cuenta «apoderado» descarga su libreta en PDF.
		MockHttpSession familia = ingresar(usuario(usuarios, "apoderado"), clave);
		String lista = mvc.perform(get("/familia/libretas").session(familia)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		Matcher pdf = Pattern.compile("/familia/libretas/(\\d+)\\.pdf").matcher(lista);
		assertThat(pdf.find()).as("la familia ve su libreta").isTrue();
		MockHttpServletResponse descarga = mvc.perform(get(pdf.group()).session(familia)).andReturn().getResponse();
		assertThat(descarga.getStatus()).isEqualTo(200);
		assertThat(descarga.getContentType()).contains("application/pdf");

		// Paso 10: Dirección verifica el código de una libreta vigente y descarga el Excel para el SIAGIE.
		MockHttpSession direccion = ingresar(usuario(usuarios, "direccion"), clave);
		Long libreta = jdbc.queryForObject("SELECT MIN(l.id) FROM libreta_emitida l WHERE l.colegio_id = ? AND NOT EXISTS "
				+ "(SELECT 1 FROM libreta_emitida x WHERE x.reemplaza_id = l.id) AND l.numero_version = 2", Long.class, colegio);
		String huella = jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?", String.class,
				libreta);
		String verificada = mvc.perform(get("/academico/libretas/verificar").param("codigo", "LIB-" + libreta + "-"
				+ huella.substring(0, 16)).session(direccion)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		assertThat(verificada).contains("Es la versión vigente");
		Long seccion = jdbc.queryForObject("SELECT seccion_id FROM libreta_emitida WHERE id = ?", Long.class, libreta);
		Long periodo = jdbc.queryForObject("SELECT periodo_id FROM libreta_emitida WHERE id = ?", Long.class, libreta);
		MockHttpServletResponse excel = mvc.perform(get("/academico/siagie/calificaciones").param("seccion",
				seccion.toString()).param("periodo", periodo.toString()).session(direccion)).andReturn().getResponse();
		assertThat(excel.getStatus()).isEqualTo(200);
		assertThat(excel.getHeader("Content-Disposition")).contains("DEMO-PROVISIONAL-calificaciones");
	}

	private MockHttpSession ingresar(String usuario, String clave) throws Exception {
		MvcResult resultado = mvc.perform(formLogin("/login").userParameter("usuario").user(usuario)
				.passwordParam("clave").password(clave)).andExpect(status().is3xxRedirection()).andReturn();
		assertThat(resultado.getResponse().getRedirectedUrl()).as("ingreso de " + usuario).doesNotContain("error");
		return (MockHttpSession) resultado.getRequest().getSession();
	}

	private static String usuario(List<String> usuarios, String sufijo) {
		return usuarios.stream().filter(u -> u.endsWith("." + sufijo)).findFirst().orElseThrow();
	}
}
