package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Años y secciones desde la web: formularios, mensajes en español y botones según el rol. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EstructuraControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	@ComoUsuario(roles = Rol.ADMINISTRACION)
	void administracionCreaAnioYSeccionDesdeLaWeb() throws Exception {
		mvc.perform(get("/colegio")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Todavía no hay años escolares")))
				// Propone el año actual con clases del primer lunes de marzo a mediados de diciembre.
				.andExpect(content().string(containsString("value=\"2026\"")))
				.andExpect(content().string(containsString("value=\"2026-03-02\"")));

		MvcResult creado = mvc.perform(post("/colegio/anios").with(csrf()).param("anio", "2026")
						.param("inicioClases", "2026-03-02").param("finClases", "2026-12-18").param("enCurso", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("exito", "Listo: creaste el año 2026. Ahora agrégale sus secciones."))
				.andReturn();
		String rutaAnio = creado.getResponse().getRedirectedUrl();
		assertThat(rutaAnio).startsWith("/colegio/anios/");
		Long anio = Long.valueOf(rutaAnio.substring(rutaAnio.lastIndexOf('/') + 1));

		mvc.perform(post(rutaAnio + "/secciones").with(csrf()).param("grado", "PRIMARIA_5").param("nombre", "a"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("exito", "Listo: agregaste una sección a 5.° Primaria."));
		mvc.perform(get(rutaAnio)).andExpect(status().isOk())
				.andExpect(content().string(containsString("Año escolar 2026")))
				.andExpect(content().string(containsString("data-seccion=\"5.° Primaria A\"")))
				.andExpect(content().string(containsString("Desactivar sección")));
		mvc.perform(get("/colegio")).andExpect(content().string(containsString("data-anio=\"2026\"")))
				.andExpect(content().string(containsString("1 sección · 0 alumnos matriculados")))
				.andExpect(content().string(containsString("Ya hay un año en curso")));

		Long seccion = jdbc.queryForObject("SELECT id FROM seccion WHERE anio_escolar_id = ?", Long.class, anio);
		mvc.perform(post("/colegio/secciones/" + seccion + "/desactivar").with(csrf()).param("anioId", anio.toString())
						.param("motivo", "corto"))
				.andExpect(flash().attribute("error", "El motivo debe tener entre 10 y 500 caracteres."));
		mvc.perform(post("/colegio/secciones/" + seccion + "/desactivar").with(csrf()).param("anioId", anio.toString())
						.param("motivo", "No se abrirá por falta de alumnos"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT activa FROM seccion WHERE id = ?", Boolean.class, seccion)).isFalse();
	}

	@Test
	@ComoUsuario(roles = Rol.DIRECTOR)
	void datosInvalidosMuestranMensajesEnEspanol() throws Exception {
		mvc.perform(post("/colegio/anios").with(csrf()).param("anio", "").param("inicioClases", "2026-12-01")
						.param("finClases", "2026-03-01"))
				.andExpect(status().isOk()).andExpect(view().name("colegio/resumen"))
				.andExpect(content().string(containsString("Escribe el año.")));
		mvc.perform(post("/colegio/anios").with(csrf()).param("anio", "2026").param("inicioClases", "2026-12-01")
						.param("finClases", "2026-03-01"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("El inicio de clases debe ser antes del fin de clases.")));
		mvc.perform(post("/colegio/anios").with(csrf()).param("anio", "dos mil").param("inicioClases", "2026-03-02")
						.param("finClases", "2026-12-18"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Escribe solo números.")))
				.andExpect(content().string(not(containsString("NumberFormatException"))));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anio_escolar", Long.class)).isZero();
	}

	@Test
	void promotoriaVeLosAniosSinFormularios() throws Exception {
		mvc.perform(post("/colegio/anios").with(UsuariosDePrueba.como(Rol.DIRECTOR)).with(csrf()).param("anio", "2026")
				.param("inicioClases", "2026-03-02").param("finClases", "2026-12-18").param("enCurso", "true"));

		mvc.perform(get("/colegio").with(UsuariosDePrueba.como(Rol.PROMOTOR)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-anio=\"2026\"")))
				.andExpect(content().string(not(containsString("Nuevo año escolar"))));
	}
}
