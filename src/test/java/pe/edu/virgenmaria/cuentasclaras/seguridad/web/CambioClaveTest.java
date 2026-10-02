package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio de clave del propio usuario, empezando por el primer ingreso con clave temporal.
 */
@PruebaIntegracion
class CambioClaveTest {

	private static final String TEMPORAL = UsuariosDePrueba.CLAVE;

	private static final String NUEVA = "mi gato duerme al sol";

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

	private MockHttpSession sesion;

	@BeforeEach
	void ingresarConClaveTemporal() throws Exception {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "nuevo", TEMPORAL, true, Rol.DOCENTE);
		sesion = (MockHttpSession) ingresar(TEMPORAL).andExpect(redirectedUrl("/cuenta/cambiar-clave"))
				.andReturn().getRequest().getSession(false);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void exigeLaClaveActual() throws Exception {
		cambiar("esta no es mi clave actual", NUEVA, NUEVA)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Tu clave actual no es correcta.")));
		assertThat(codificador.matches(TEMPORAL, hashGuardado())).isTrue();
	}

	@Test
	void rechazaNuevaIgualALaActual() throws Exception {
		cambiar(TEMPORAL, TEMPORAL, TEMPORAL)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("debe ser distinta de la actual")));
		assertThat(debeCambiarClave()).isTrue();
	}

	@Test
	void laConfirmacionDebeCoincidir() throws Exception {
		cambiar(TEMPORAL, NUEVA, NUEVA + " x")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("no coinciden")));
	}

	@Test
	void aplicaLaPoliticaDeClaves() throws Exception {
		cambiar(TEMPORAL, "corta", "corta")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("entre 10 y 64 caracteres")));
		cambiar(TEMPORAL, "nuevo es mi usuario", "nuevo es mi usuario")
				.andExpect(content().string(containsString("no puede contener tu nombre de usuario")));
		assertThat(debeCambiarClave()).isTrue();
	}

	@Test
	void cambioCierraSesionYPideIngresar() throws Exception {
		cambiar(TEMPORAL, NUEVA, NUEVA).andExpect(redirectedUrl("/login?clave-cambiada"));

		assertThat(sesion.isInvalid()).isTrue();
		assertThat(debeCambiarClave()).isFalse();
		ingresar(TEMPORAL).andExpect(redirectedUrl("/login?error"));
		ingresar(NUEVA).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void cincoClavesActualesIncorrectasBloqueanLaCuentaYCierranLaSesion() throws Exception {
		for (int i = 0; i < 4; i++) {
			cambiar("no es mi clave actual", NUEVA, NUEVA)
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("Tu clave actual no es correcta.")));
		}
		assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE nombre_usuario = 'nuevo'",
				Integer.class)).isEqualTo(4);

		cambiar("no es mi clave actual", NUEVA, NUEVA).andExpect(redirectedUrl("/login?error"));

		assertThat(sesion.isInvalid()).isTrue();
		ingresar(TEMPORAL).andExpect(redirectedUrl("/login?error"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'INGRESO_RECHAZADO_BLOQUEADA'",
				Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CUENTA_BLOQUEADA'",
				Long.class)).isEqualTo(1);
	}

	@Test
	void cuentaBloqueadaNoPuedeCambiarLaClaveNiConLaActualCorrecta() throws Exception {
		String hashAntes = hashGuardado();
		jdbc.update("UPDATE usuario SET bloqueado_hasta = ? WHERE nombre_usuario = 'nuevo'",
				java.time.LocalDateTime.now(reloj).plusMinutes(10));

		cambiar(TEMPORAL, NUEVA, NUEVA).andExpect(redirectedUrl("/login?error"));

		assertThat(sesion.isInvalid()).isTrue();
		assertThat(hashGuardado()).isEqualTo(hashAntes);
	}

	@Test
	void cambioQuedaAuditadoSinLaClave() throws Exception {
		cambiar(TEMPORAL, NUEVA, NUEVA);

		Map<String, Object> evento = jdbc.queryForMap(
				"SELECT * FROM evento_auditoria WHERE accion = 'CLAVE_CAMBIADA'");
		assertThat(evento.get("nombre_usuario")).isEqualTo("nuevo");
		assertThat(evento.get("roles")).as("con clave pendiente igual se registra su rol").isEqualTo("DOCENTE");
		assertThat(evento.get("valor_anterior")).isNull();
		assertThat(evento.get("valor_nuevo")).isNull();
		assertThat(String.valueOf(evento.get("detalle"))).contains("temporal").doesNotContain(NUEVA)
				.doesNotContain(TEMPORAL);
	}

	private ResultActions cambiar(String actual, String nueva, String confirmacion) throws Exception {
		return mvc.perform(post("/cuenta/cambiar-clave").session(sesion).with(csrf())
				.param("claveActual", actual).param("claveNueva", nueva).param("confirmacion", confirmacion));
	}

	private ResultActions ingresar(String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", "nuevo").param("clave", clave));
	}

	private String hashGuardado() {
		return jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE nombre_usuario = 'nuevo'", String.class);
	}

	private boolean debeCambiarClave() {
		return jdbc.queryForObject("SELECT debe_cambiar_clave FROM usuario WHERE nombre_usuario = 'nuevo'",
				Boolean.class);
	}
}
