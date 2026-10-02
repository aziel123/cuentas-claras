package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingreso, cierre de sesión y protecciones de la sesión con usuarios reales en la base.
 */
@PruebaIntegracion
class LoginTest {

	private static final String CLAVE = UsuariosDePrueba.CLAVE;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private java.time.Clock reloj;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios servicioUsuarios;

	@BeforeEach
	void crearUsuarios() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", CLAVE, false, Rol.CAJA);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "nuevo", CLAVE, true, Rol.DOCENTE);
		Usuario inactivo = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "inactivo", CLAVE, false, Rol.CAJA);
		ContextoColegio.en(1L, () -> {
			inactivo.desactivar("director", LocalDateTime.now(reloj));
			usuarios.save(inactivo);
		});
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void ingresoCorrectoRedirigeAInicio() throws Exception {
		ingresar("caja", CLAVE)
				.andExpect(redirectedUrl("/inicio"))
				.andExpect(authenticated().withUsername("caja").withRoles("CAJA"));
	}

	@Test
	void elNombreDeUsuarioNoDistingueMayusculasNiEspacios() throws Exception {
		ingresar("  CAJA ", CLAVE).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void claveIncorrectaYUsuarioInexistenteMuestranElMismoMensaje() throws Exception {
		ingresar("caja", "otra clave cualquiera").andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
		ingresar("no.existe", CLAVE).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());

		mvc.perform(get("/login").param("error", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Usuario o clave incorrectos")));
	}

	@Test
	void usuarioDesactivadoNoPuedeIngresar() throws Exception {
		ingresar("inactivo", CLAVE).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
	}

	@Test
	void unaClaveDeMasDe72BytesSoloEsUnIngresoFallido() throws Exception {
		ingresar("caja", "ñ".repeat(50)).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
	}

	@Test
	void primerIngresoObligaACambiarLaClave() throws Exception {
		MvcResult resultado = ingresar("nuevo", CLAVE)
				.andExpect(redirectedUrl("/cuenta/cambiar-clave"))
				.andReturn();
		MockHttpSession sesion = sesionDe(resultado);

		mvc.perform(get("/inicio").session(sesion)).andExpect(status().isForbidden());
		mvc.perform(get("/cuenta/cambiar-clave").session(sesion))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("clave temporal")));
	}

	@Test
	void loginSinCsrfEsRechazado() throws Exception {
		mvc.perform(post("/login").param("usuario", "caja").param("clave", CLAVE))
				.andExpect(status().isForbidden())
				.andExpect(unauthenticated());
	}

	@Test
	void cerrarSesionPorGetNoCierraLaSesion() throws Exception {
		MockHttpSession sesion = sesionDe(ingresar("caja", CLAVE).andReturn());

		mvc.perform(get("/salir").session(sesion)).andExpect(status().isForbidden());

		assertThat(sesion.isInvalid()).isFalse();
		mvc.perform(get("/inicio").session(sesion)).andExpect(status().isOk());
	}

	@Test
	void cierreDeSesionInvalidaLaSesion() throws Exception {
		MockHttpSession sesion = sesionDe(ingresar("caja", CLAVE).andReturn());

		mvc.perform(post("/salir").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salio"));

		assertThat(sesion.isInvalid()).isTrue();
		mvc.perform(get("/login").param("salio", ""))
				.andExpect(content().string(containsString("Cerraste sesión")));
	}

	@Test
	void elIdDeSesionCambiaAlIngresar() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		String idAntes = sesion.getId();

		MvcResult resultado = mvc.perform(post("/login").session(sesion).with(csrf())
				.param("usuario", "caja").param("clave", CLAVE)).andReturn();

		assertThat(resultado.getRequest().getSession().getId()).isNotEqualTo(idAntes);
	}

	@Test
	void unSegundoIngresoExpiraLaPrimeraSesion() throws Exception {
		MockHttpSession primera = sesionDe(ingresar("caja", CLAVE).andReturn());
		MockHttpSession segunda = sesionDe(ingresar("caja", CLAVE).andReturn());

		mvc.perform(get("/inicio").session(primera)).andExpect(redirectedUrl("/login?expirada"));
		mvc.perform(get("/inicio").session(segunda)).andExpect(status().isOk());
	}

	@Test
	void directorDegradadoEsExpulsadoEnSuSiguientePeticion() throws Exception {
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", CLAVE, false, Rol.PROMOTOR);
		Usuario director = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", CLAVE, false, Rol.DIRECTOR);
		MockHttpSession sesionDirector = sesionDe(ingresar("director", CLAVE).andReturn());
		mvc.perform(get("/usuarios").session(sesionDirector)).andExpect(status().isOk());

		UsuariosDePrueba.iniciarSesion(promotora);
		try {
			servicioUsuarios.cambiarRoles(director.getId(), new pe.edu.virgenmaria.cuentasclaras.seguridad.dto
					.CambiarRolesRequest(java.util.EnumSet.of(Rol.DOCENTE), "Ya no es director del colegio"));
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}

		mvc.perform(get("/usuarios").session(sesionDirector)).andExpect(redirectedUrl("/login?expirada"));
	}

	@Test
	void respuestasIncluyenCabecerasDeSeguridad() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
				.andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
				.andExpect(header().string("Content-Security-Policy", containsString("style-src 'self'")))
				.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
				.andExpect(header().string("Permissions-Policy", containsString("camera=()")));
	}

	@Test
	void formulariosIncluyenTokenCsrf() throws Exception {
		mvc.perform(get("/login")).andExpect(content().string(containsString("name=\"_csrf\"")));

		MockHttpSession sesion = sesionDe(ingresar("caja", CLAVE).andReturn());
		mvc.perform(get("/cuenta/cambiar-clave").session(sesion))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("action=\"/salir\"")));
	}

	@Test
	void quienYaInicioSesionNoVuelveAlFormulario() throws Exception {
		MockHttpSession sesion = sesionDe(ingresar("caja", CLAVE).andReturn());
		mvc.perform(get("/login").session(sesion)).andExpect(redirectedUrl("/inicio"));
	}

	private org.springframework.test.web.servlet.ResultActions ingresar(String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", usuario).param("clave", clave));
	}

	private static MockHttpSession sesionDe(MvcResult resultado) {
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
