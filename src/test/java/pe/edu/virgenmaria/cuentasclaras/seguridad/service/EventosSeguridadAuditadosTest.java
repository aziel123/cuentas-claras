package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada evento de seguridad queda en la bitácora con quién, colegio, rol e IP, y nunca con claves.
 */
@PruebaIntegracion
class EventosSeguridadAuditadosTest {

	private static final String CLAVE = UsuariosDePrueba.CLAVE;

	private static final String CLAVE_EQUIVOCADA = "clave equivocada secreta 123";

	private static final String CLAVE_NUEVA = "mi gato duerme al sol";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private VerificadorIntegridadAuditoria verificador;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario caja;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		SecurityContextHolder.clearContext();
	}

	@Test
	void ingresoExitosoQuedaAuditado() throws Exception {
		ingresar("caja", CLAVE).andExpect(redirectedUrl("/inicio"));

		Map<String, Object> evento = ultimo("INGRESO_EXITOSO");
		assertThat(evento.get("colegio_id")).isEqualTo(1L);
		assertThat(evento.get("usuario_id")).isEqualTo(caja.getId());
		assertThat(evento.get("nombre_usuario")).isEqualTo("caja");
		assertThat(evento.get("roles")).isEqualTo("CAJA");
		assertThat(evento.get("ip")).isEqualTo("127.0.0.1");
	}

	@Test
	void ingresoFallidoQuedaAuditado() throws Exception {
		ingresar("caja", CLAVE_EQUIVOCADA);

		Map<String, Object> evento = ultimo("INGRESO_FALLIDO");
		assertThat(evento.get("colegio_id")).isEqualTo(1L);
		assertThat(evento.get("usuario_id")).isEqualTo(caja.getId());
		assertThat((String) evento.get("detalle")).contains("Intento 1 de 5");
	}

	@Test
	void cuentaBloqueadaQuedaAuditada() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("caja", CLAVE_EQUIVOCADA);
		}
		assertThat(ultimo("CUENTA_BLOQUEADA").get("usuario_id")).isEqualTo(caja.getId());
	}

	@Test
	void ingresoRechazadoPorCuentaBloqueadaQuedaAuditado() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("caja", CLAVE_EQUIVOCADA);
		}
		ingresar("caja", CLAVE).andExpect(redirectedUrl("/login?bloqueada"));
		assertThat(ultimo("INGRESO_RECHAZADO_BLOQUEADA").get("usuario_id")).isEqualTo(caja.getId());
	}

	@Test
	void ingresoRechazadoPorCuentaInactivaQuedaAuditado() throws Exception {
		ContextoColegio.en(1L, () -> {
			caja.desactivar("director", LocalDateTime.now());
			usuarios.save(caja);
		});
		ingresar("caja", CLAVE).andExpect(redirectedUrl("/login?error"));
		assertThat(ultimo("INGRESO_RECHAZADO_INACTIVA").get("usuario_id")).isEqualTo(caja.getId());
	}

	@Test
	void cierreDeSesionQuedaAuditado() throws Exception {
		MockHttpSession sesion = sesion(ingresar("caja", CLAVE));
		mvc.perform(post("/salir").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salio"));

		Map<String, Object> evento = ultimo("SESION_CERRADA");
		assertThat(evento.get("usuario_id")).isEqualTo(caja.getId());
		assertThat(evento.get("colegio_id")).isEqualTo(1L);
	}

	@Test
	void accesoDenegadoQuedaAuditado() throws Exception {
		MockHttpSession sesion = sesion(ingresar("caja", CLAVE));
		mvc.perform(get("/usuarios").param("buscar", "dato-sensible").session(sesion)).andExpect(status().isForbidden());

		Map<String, Object> evento = ultimo("ACCESO_DENEGADO");
		assertThat(evento.get("nombre_usuario")).isEqualTo("caja");
		assertThat(evento.get("colegio_id")).isEqualTo(1L);
		assertThat(evento.get("detalle")).as("sin la query string").isEqualTo("GET /usuarios");
	}

	@Test
	void unVisitanteAnonimoSinCsrfNoLlenaLaBitacora() throws Exception {
		mvc.perform(post("/login").param("usuario", "caja").param("clave", CLAVE)).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria", Long.class)).isZero();
	}

	@Test
	void cambioDeClaveQuedaAuditado() throws Exception {
		MockHttpSession sesion = sesion(ingresar("caja", CLAVE));
		cambiarClave(sesion).andExpect(redirectedUrl("/login?clave-cambiada"));
		assertThat(ultimo("CLAVE_CAMBIADA").get("usuario_id")).isEqualTo(caja.getId());
	}

	@Test
	void laAuditoriaNuncaContieneClavesNiHashes() throws Exception {
		ingresar("caja", CLAVE_EQUIVOCADA);
		ingresar(CLAVE_EQUIVOCADA, CLAVE);
		cambiarClave(sesion(ingresar("caja", CLAVE)));
		ingresar("caja", CLAVE_NUEVA).andExpect(redirectedUrl("/inicio"));

		String bitacora = jdbc.queryForList("SELECT * FROM evento_auditoria").stream()
				.flatMap(fila -> fila.values().stream())
				.map(String::valueOf)
				.collect(Collectors.joining("\n"));
		assertThat(bitacora).isNotBlank()
				.doesNotContain(CLAVE)
				.doesNotContain(CLAVE_NUEVA)
				.doesNotContain("{bcrypt}")
				.doesNotContain("$2a$");
		// Lo único que no se puede evitar: si alguien escribe su clave en el campo de usuario, queda como nombre intentado.
		List<String> nombres = jdbc.queryForList("SELECT DISTINCT nombre_usuario FROM evento_auditoria", String.class);
		assertThat(nombres).contains("caja");
	}

	@Test
	void losEventosDeSeguridadMantienenLaCadenaIntegra() throws Exception {
		ingresar("caja", CLAVE_EQUIVOCADA);
		ingresar("caja", CLAVE);
		SecurityContextHolder.getContext()
				.setAuthentication(UsuariosDePrueba.autenticacion(UsuariosDePrueba.autenticado(Rol.PROMOTOR)));

		assertThat(verificador.verificar().integra()).isTrue();
	}

	private ResultActions ingresar(String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", usuario).param("clave", clave));
	}

	private ResultActions cambiarClave(MockHttpSession sesion) throws Exception {
		return mvc.perform(post("/cuenta/cambiar-clave").session(sesion).with(csrf())
				.param("claveActual", CLAVE).param("claveNueva", CLAVE_NUEVA).param("confirmacion", CLAVE_NUEVA));
	}

	private static MockHttpSession sesion(ResultActions resultado) {
		return (MockHttpSession) resultado.andReturn().getRequest().getSession(false);
	}

	private Map<String, Object> ultimo(String accion) {
		return jdbc.queryForMap("SELECT * FROM evento_auditoria WHERE accion = ? ORDER BY secuencia DESC LIMIT 1",
				accion);
	}
}
