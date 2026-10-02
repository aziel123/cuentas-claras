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

	@Autowired
	private java.time.Clock reloj;

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
		ingresar("caja", CLAVE).andExpect(redirectedUrl("/login?error"));
		assertThat(ultimo("INGRESO_RECHAZADO_BLOQUEADA").get("usuario_id")).isEqualTo(caja.getId());
	}

	@Test
	void ingresoRechazadoPorCuentaInactivaQuedaAuditado() throws Exception {
		ContextoColegio.en(1L, () -> {
			caja.desactivar("director", LocalDateTime.now(reloj));
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
		// La clave escrita por error en el campo de usuario no tiene forma de usuario: no se guarda.
		List<String> nombres = jdbc.queryForList("SELECT DISTINCT nombre_usuario FROM evento_auditoria", String.class);
		assertThat(nombres).contains("caja").doesNotContain(CLAVE_EQUIVOCADA)
				.anySatisfy(n -> assertThat(n).startsWith(ProveedorAutenticacion.PREFIJO_DESCONOCIDO));
	}

	@Test
	void elTextoEscritoPorQuienNoEsUsuarioNuncaSeGuarda() throws Exception {
		ingresar("Mi Clave Secreta!", CLAVE).andExpect(redirectedUrl("/login?error"));
		ingresar("Mi Clave Secreta!", CLAVE);
		ingresar("no.existe", CLAVE);

		List<String> nombres = jdbc.queryForList("SELECT nombre_usuario FROM evento_auditoria ORDER BY secuencia",
				String.class);
		assertThat(nombres).hasSize(3).allSatisfy(n -> assertThat(n).matches("desconocido-[0-9a-f]{12}"));
		assertThat(nombres.get(0)).as("el mismo texto da el mismo código").isEqualTo(nombres.get(1));
		assertThat(nombres.get(2)).isNotEqualTo(nombres.get(0));
		String bitacora = String.join("\n", jdbc.queryForList("SELECT CONCAT_WS('|', nombre_usuario, detalle, valor_nuevo) "
				+ "FROM evento_auditoria", String.class));
		assertThat(bitacora).doesNotContainIgnoringCase("secreta").doesNotContain("no.existe");
	}

	@Test
	void usuarioCreadoQuedaAuditado() throws Exception {
		mvc.perform(post("/usuarios").with(UsuariosDePrueba.como(promotora())).with(csrf())
				.param("nombreCompleto", "Ana Torres").param("nombreUsuario", "ana.torres").param("roles", "DOCENTE"))
				.andExpect(status().isOk());

		Map<String, Object> evento = ultimo("USUARIO_CREADO");
		assertThat(evento.get("nombre_usuario")).isEqualTo("promotora");
		assertThat((String) evento.get("valor_nuevo")).contains("roles=DOCENTE");
		assertThat((String) evento.get("detalle")).contains("ana.torres");
	}

	@Test
	void rolesCambiadosQuedanAuditados() throws Exception {
		accionSobreCaja("roles", "roles", "DOCENTE");
		Map<String, Object> evento = ultimo("ROLES_CAMBIADOS");
		assertThat(evento.get("valor_anterior")).isEqualTo("CAJA");
		assertThat(evento.get("valor_nuevo")).isEqualTo("DOCENTE");
	}

	@Test
	void usuarioDesactivadoYReactivadoQuedanAuditados() throws Exception {
		accionSobreCaja("desactivar");
		accionSobreCaja("reactivar");
		assertThat(ultimo("USUARIO_DESACTIVADO").get("valor_nuevo")).isEqualTo("inactivo");
		assertThat(ultimo("USUARIO_REACTIVADO").get("valor_nuevo")).isEqualTo("activo");
	}

	@Test
	void claveRestablecidaQuedaAuditadaSinLaClave() throws Exception {
		String html = accionSobreCaja("restablecer-clave").andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		java.util.regex.Matcher clave = java.util.regex.Pattern.compile("clave-temporal-valor[^>]*>([^<]+)<").matcher(html);
		assertThat(clave.find()).isTrue();

		Map<String, Object> evento = ultimo("CLAVE_RESTABLECIDA");
		assertThat(evento.get("entidad_id")).isEqualTo(caja.getId().toString());
		assertThat(evento.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(clave.group(1)));
	}

	@Test
	void cuentaDesbloqueadaQuedaAuditada() throws Exception {
		jdbc.update("UPDATE usuario SET bloqueado_hasta = ? WHERE id = ?", LocalDateTime.now(reloj).plusHours(1),
				caja.getId());
		accionSobreCaja("desbloquear");
		assertThat(ultimo("CUENTA_DESBLOQUEADA").get("nombre_usuario")).isEqualTo("promotora");
	}

	private Usuario promotoraGuardada;

	private Usuario promotora() {
		if (promotoraGuardada == null) {
			promotoraGuardada = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", CLAVE, false,
					Rol.PROMOTOR);
		}
		return promotoraGuardada;
	}

	private ResultActions accionSobreCaja(String accion, String... parametros) throws Exception {
		var peticion = post("/usuarios/" + caja.getId() + "/" + accion).with(UsuariosDePrueba.como(promotora()))
				.with(csrf()).param("motivo", "Motivo de prueba suficiente");
		for (int i = 0; i + 1 < parametros.length; i += 2) {
			peticion.param(parametros[i], parametros[i + 1]);
		}
		return mvc.perform(peticion);
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
