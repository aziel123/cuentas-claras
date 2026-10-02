package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La Dirección del colegio B no ve ni toca alumnos, familias, años ni secciones del colegio A. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoAlumnosWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuelaA;

	private RegistroResultado mateoA;

	private UsuarioAutenticado directorB;

	private Long alumnoB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuelaA = EscenarioEscolar.crearEstructura(estructura);
		mateoA = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuelaA.primaria5A2026()));

		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		directorB = UsuariosDePrueba.autenticado(colegioB, 99L, "director.b", "Directora B", false,
				EnumSet.of(Rol.DIRECTOR));
		UsuariosDePrueba.iniciarSesion(directorB);
		estructura.crearAnio(new CrearAnioEscolarRequest(2026, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 18), true));
		alumnoB = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("70000001", "Torres", "Lima", "Andrés",
				LocalDate.of(2015, 2, 1), "40000001", "Lima", "Paz", "Gloria", "999888777", null, null)).alumnoId();
		// MockMvc reutiliza el contexto del hilo: se limpia antes de cada petición.
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void directorDelColegioBRecibe404AlAbrirAlumnoDelA() throws Exception {
		for (String ruta : new String[] { "/alumnos/" + mateoA.alumnoId(), "/alumnos/" + mateoA.alumnoId() + "/editar" }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(directorB))).andExpect(status().isNotFound())
					.andExpect(content().string(not(containsString("Quispe"))));
		}
		mvc.perform(post("/alumnos/" + mateoA.alumnoId() + "/retirar").with(UsuariosDePrueba.como(directorB))
						.with(csrf()).param("fecha", "2026-10-01").param("motivo", "Intento desde otro colegio"))
				.andExpect(status().isNotFound());
		mvc.perform(post("/alumnos/" + mateoA.alumnoId() + "/responsable").with(UsuariosDePrueba.como(directorB))
						.with(csrf()).param("documentoApoderado", "40000001").param("motivo", "Intento desde otro colegio"))
				.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno WHERE id = ?", String.class, mateoA.alumnoId()))
				.isEqualTo("ACTIVO");
		assertThat(jdbc.queryForObject("SELECT familia_id FROM alumno WHERE id = ?", Long.class, mateoA.alumnoId()))
				.isEqualTo(mateoA.familiaId());
		// Y su año escolar tampoco.
		mvc.perform(get("/colegio/anios/" + escuelaA.anio2026()).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
	}

	@Test
	void directorDelColegioBRecibe404AlAbrirFamiliaDelA() throws Exception {
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				mateoA.alumnoId());
		mvc.perform(get("/alumnos/familias/" + mateoA.familiaId()).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/alumnos/nuevo").param("familia", mateoA.familiaId().toString())
				.with(UsuariosDePrueba.como(directorB))).andExpect(status().isNotFound());
		mvc.perform(get("/alumnos/apoderados/" + rosa).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
		mvc.perform(post("/alumnos/apoderados/" + rosa).with(UsuariosDePrueba.como(directorB)).with(csrf())
						.param("tipoDocumento", "DNI").param("numeroDocumento", "45678912").param("apellidoPaterno", "Huamán")
						.param("nombres", "Rosa").param("parentesco", "MADRE").param("telefonoWhatsapp", "911111111")
						.param("motivo", "Intento de desviar los avisos"))
				.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, rosa))
				.isEqualTo("+51987654321");
	}

	@Test
	void directorDelColegioBNoPuedeMatricularEnSeccionDelA() throws Exception {
		mvc.perform(post("/alumnos/" + alumnoB + "/matricula").with(UsuariosDePrueba.como(directorB)).with(csrf())
				.param("seccionId", escuelaA.primaria5A2026().toString())).andExpect(status().isNotFound());
		mvc.perform(post("/alumnos/" + mateoA.alumnoId() + "/matricula").with(UsuariosDePrueba.como(directorB))
				.with(csrf()).param("seccionId", escuelaA.primaria6A2027().toString())).andExpect(status().isNotFound());
		mvc.perform(post("/alumnos/nuevo").with(UsuariosDePrueba.como(directorB)).with(csrf())
						.param("tipoDocumento", "DNI").param("numeroDocumento", "70000002").param("apellidoPaterno", "Torres")
						.param("nombres", "Luis").param("fechaNacimiento", "2015-05-05")
						.param("documentoApoderadoExistente", "40000001")
						.param("seccionId", escuelaA.primaria5A2026().toString()))
				.andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matricula WHERE seccion_id IN (?, ?)", Long.class,
				escuelaA.primaria5A2026(), escuelaA.primaria6A2027())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE numero_documento = '70000002'", Long.class))
				.isZero();
	}

	@Test
	void busquedaDelColegioBNoMuestraAlumnosDelA() throws Exception {
		mvc.perform(get("/alumnos").param("q", "quispe").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Mateo"))))
				.andExpect(content().string(containsString("No encontramos alumnos")));
		mvc.perform(get("/alumnos").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Andrés Torres Lima")))
				.andExpect(content().string(not(containsString("Quispe"))))
				// Los filtros tampoco muestran las secciones ni los años del A.
				.andExpect(content().string(not(containsString(">2027</option>"))))
				.andExpect(content().string(not(containsString("5.° Primaria A</option>"))));
		mvc.perform(get("/colegio").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-anio=\"2026\"")))
				.andExpect(content().string(not(containsString("data-anio=\"2027\""))));
	}
}
