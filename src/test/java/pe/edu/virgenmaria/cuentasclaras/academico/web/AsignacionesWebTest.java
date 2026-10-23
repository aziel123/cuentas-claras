package pe.edu.virgenmaria.cuentasclaras.academico.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 9, tanda 1 (sección 10): Docentes y secciones por la web. La ven Promotoría, Dirección y Administración; Caja,
 * Docente y Apoderado no (403, por la matriz). Administración propone con CSRF; terminar es de Dirección o Promotoría
 * (Administración: 403 por {@code @PreAuthorize}). El menú del docente muestra «Académico» disponible.
 */
@PruebaIntegracion
class AsignacionesWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioEscolar.Estructura escuela;

	private Usuario docente;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		SecurityContextHolder.clearContext();
		docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "docente.web", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "CAJA", "DOCENTE", "APODERADO" })
	void soloLaVenPromotoriaDireccionYAdministracion(Rol rol) throws Exception {
		mvc.perform(get("/academico/asignaciones").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		mvc.perform(post("/academico/asignaciones").with(csrf()).with(UsuariosDePrueba.como(rol))
				.param("seccionId", escuela.primaria5A2026().toString()).param("docenteId", docente.getId().toString())
				.param("tipo", "TUTOR").param("motivo", "Intento sin permiso de asignar")).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_docente", Long.class)).isZero();
	}

	@Test
	void administracionProponeConCsrfYLaPantallaLaMuestraPorAprobar() throws Exception {
		mvc.perform(get("/academico/asignaciones").with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Proponer una asignación")))
				.andExpect(content().string(containsString("Nombre de docente.web (docente.web)")));
		mvc.perform(post("/academico/asignaciones").with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))
				.param("seccionId", escuela.primaria5A2026().toString()).param("docenteId", docente.getId().toString())
				.param("tipo", "TUTOR").param("motivo", "Tutoría del año 2026")).andExpect(status().isForbidden());

		mvc.perform(post("/academico/asignaciones").with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))
				.param("seccionId", escuela.primaria5A2026().toString()).param("docenteId", docente.getId().toString())
				.param("tipo", "TUTOR").param("motivo", "Tutoría del año 2026"))
				.andExpect(redirectedUrl("/academico/asignaciones")).andExpect(flash().attributeExists("exito"));

		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente", String.class)).isEqualTo("PROPUESTA");
		mvc.perform(get("/academico/asignaciones").param("anio", escuela.anio2026().toString())
				.with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Por aprobar")))
				.andExpect(content().string(not(containsString("Proponer una asignación"))));
	}

	@Test
	void sinMotivoNoSePropone() throws Exception {
		mvc.perform(post("/academico/asignaciones").with(csrf()).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION))
				.param("seccionId", escuela.primaria5A2026().toString()).param("docenteId", docente.getId().toString())
				.param("tipo", "TUTOR").param("motivo", "corto")).andExpect(status().isOk())
				.andExpect(content().string(containsString("entre 10 y 500")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_docente", Long.class)).isZero();
	}

	@Test
	void administracionNoTermina() throws Exception {
		jdbc.update("INSERT INTO asignacion_docente (colegio_id, anio_escolar_id, seccion_id, usuario_id, tipo, estado, vigente, "
				+ "tutor_vigente, propuesto_por, resuelto_por, resuelto_en, creado_en, creado_por, actualizado_en) VALUES (1, ?, "
				+ "?, ?, 'TUTOR', 'VIGENTE', TRUE, TRUE, 'administracion', 'director', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
				+ "'administracion', CURRENT_TIMESTAMP)", escuela.anio2026(), escuela.primaria5A2026(), docente.getId());
		Long id = jdbc.queryForObject("SELECT MAX(id) FROM asignacion_docente", Long.class);

		mvc.perform(post("/academico/asignaciones/" + id + "/terminar").with(csrf())
				.with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)).param("motivo", "Ya no enseña en esta sección"))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, id))
				.isEqualTo("VIGENTE");
	}

	@Test
	void elDocenteVeAcademicoEnSuMenuYSuInicio() throws Exception {
		mvc.perform(get("/academico").with(UsuariosDePrueba.como(docente))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Mis secciones")))
				.andExpect(content().string(containsString("Todavía no tienes secciones asignadas")))
				.andExpect(content().string(containsString("href=\"/academico\" data-modulo=\"ACADEMICO\"")))
				.andExpect(content().string(not(containsString("data-modulo=\"ASIGNACIONES\""))));
	}
}
