package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.OtraPersona;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;

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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;

/** Bandeja de aprobaciones desde la web: quien pidió no ve botones y, si los fuerza, recibe un mensaje claro. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AprobacionesControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	private Long solicitud;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		// Lo pide el subdirector (también es de Administración), que luego intentará aprobarlo.
		UsuariosDePrueba.iniciarSesion(subdirector());
		Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5A2026())).alumnoId();
		alumnos.retirar(mateo, new RetirarAlumnoRequest(LocalDate.of(2026, 10, 1), MOTIVO));
		solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void quienPidioVeLaSolicitudSinBotonesYSiFuerzaLaAprobacionRecibeUnMensaje() throws Exception {
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(subdirector()))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Retirar a Mateo Quispe Huamán desde el 01/10/2026")))
				.andExpect(content().string(containsString("debe resolverla otra persona")))
				.andExpect(content().string(not(containsString("/aprobaciones/" + solicitud + "/aprobar"))));

		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(UsuariosDePrueba.como(subdirector()))
						.with(csrf()))
				.andExpect(redirectedUrl("/aprobaciones"))
				.andExpect(flash().attribute("error", containsString("debe hacerlo otra persona")));
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno", String.class)).isEqualTo("ACTIVO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(1);
	}

	@Test
	void otraPersonaApruebaORechazaConMotivo() throws Exception {
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(OtraPersona.APROBADOR))).andExpect(status().isOk())
				.andExpect(content().string(containsString("/aprobaciones/" + solicitud + "/aprobar")))
				.andExpect(content().string(containsString("Rechazar solicitud")));
		mvc.perform(post("/aprobaciones/" + solicitud + "/rechazar").with(UsuariosDePrueba.como(OtraPersona.APROBADOR))
						.with(csrf()).param("motivo", ""))
				.andExpect(flash().attribute("error", containsString("motivo")));
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(UsuariosDePrueba.como(OtraPersona.APROBADOR))
						.with(csrf()))
				.andExpect(flash().attribute("exito", "Listo: aprobaste la solicitud y el cambio se aplicó."));
		assertThat(jdbc.queryForObject("SELECT estado FROM alumno", String.class)).isEqualTo("RETIRADO");
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(OtraPersona.APROBADOR)))
				.andExpect(content().string(containsString("Resueltas recientemente")))
				.andExpect(content().string(containsString("aprobador.prueba")));
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "ADMINISTRACION", "CAJA", "DOCENTE", "APODERADO" })
	void soloPromotoriaYDireccionResuelvenSolicitudes(Rol rol) throws Exception {
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(UsuariosDePrueba.como(rol)).with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(post("/aprobaciones/" + solicitud + "/rechazar").with(UsuariosDePrueba.como(rol)).with(csrf())
						.param("motivo", "Intento de alguien sin permiso"))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio", String.class)).isEqualTo("PENDIENTE");
	}

	private static pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado subdirector() {
		return UsuariosDePrueba.autenticado(1L, 15L, "subdirector", "Subdirector", false,
				java.util.EnumSet.of(Rol.DIRECTOR, Rol.ADMINISTRACION));
	}
}
