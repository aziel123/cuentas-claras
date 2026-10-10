package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 7 (A07 de OWASP; H9, decisiones 83 y 104; E18 y E24): el ingreso con los límites de producción (5 con un
 * usuario y 20 con cualquiera desde una conexión), que no se puedan enumerar usuarios y la sesión de la base que nace al
 * ingresar (10 horas como máximo) y se cierra al salir, al expirar o con un segundo ingreso.
 */
@PruebaIntegracion
@TestPropertySource(properties = { "cuentasclaras.seguridad.intentos-maximos=15", "cuentasclaras.sesion.intentos-por-ip=20",
		"cuentasclaras.sesion.intentos-por-cuenta-e-ip=5", "cuentasclaras.sesion.ventana-intentos-ip=15m" })
class IngresoYSesionBordesTest {

	private static final String INCORRECTA = "no es la clave de nadie";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@MockitoSpyBean
	private PasswordEncoder codificador;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "ex.cajera", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		jdbc.update("UPDATE usuario SET activo = FALSE, desactivado_en = CURRENT_TIMESTAMP, desactivado_por = 'directora' "
				+ "WHERE nombre_usuario = 'ex.cajera'");
		clearInvocations(codificador);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ enumeración de usuarios

	@Test
	void debeResponderLoMismoAUnUsuarioInexistenteDesactivadoOBloqueado() throws Exception {
		bloquear("caja");

		for (String usuario : List.of("nadie.inventado", "ex.cajera", "caja", "directora")) {
			ingresar("198.51.100." + (usuario.length() + 10), usuario, INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
	}

	@Test
	void debeCompararContraUnHashAunqueElUsuarioNoExista() throws Exception {
		ingresar("198.51.100.30", "nadie.inventado", INCORRECTA).andExpect(redirectedUrl("/login?error"));

		verify(codificador, atLeastOnce()).matches(anyString(), anyString());
	}

	/**
	 * QA-S7-5. Dado un usuario desactivado (una ex cajera), cuando alguien intenta ingresar con su nombre, entonces la
	 * respuesta debe tardar lo mismo que con un usuario que no existe (BCrypt contra el señuelo). Hoy
	 * ProveedorAutenticacion rechaza la cuenta desactivada SIN comparar la clave: responde en milisegundos y el atacante
	 * sabe que ese usuario existe (enumeración por tiempo).
	 */
	@Disabled("QA-S7-5: una cuenta desactivada responde sin BCrypt: se distingue de una inexistente por el tiempo")
	@Test
	void debeCompararContraUnHashAunqueLaCuentaEsteDesactivada() throws Exception {
		ingresar("198.51.100.31", "ex.cajera", INCORRECTA).andExpect(redirectedUrl("/login?error"));

		verify(codificador, atLeastOnce()).matches(anyString(), anyString());
	}

	/**
	 * QA-S7-5 (mismo hallazgo). Dada una cuenta bloqueada (por ejemplo, la que el atacante bloqueó desde 3 conexiones),
	 * cuando se intenta ingresar con ella, entonces debe pasar por BCrypt igual que una inexistente: si no, el atacante
	 * confirma que la cuenta existe en cuanto la bloquea.
	 */
	@Disabled("QA-S7-5: una cuenta bloqueada responde sin BCrypt: se distingue de una inexistente por el tiempo")
	@Test
	void debeCompararContraUnHashAunqueLaCuentaEsteBloqueada() throws Exception {
		bloquear("caja");

		ingresar("198.51.100.32", "caja", INCORRECTA).andExpect(redirectedUrl("/login?error"));

		verify(codificador, atLeastOnce()).matches(anyString(), anyString());
	}

	@Test
	void elAvisoDe429EsElMismoParaUnUsuarioQueExisteYUnoQueNo() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("203.0.113.81", "caja", INCORRECTA).andExpect(redirectedUrl("/login?error"));
			ingresar("203.0.113.82", "nadie.inventado", INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		String aviso = "Demasiados intentos con este usuario desde esta conexión. Espera 15 minutos.";
		ingresar("203.0.113.81", "caja", INCORRECTA).andExpect(status().isTooManyRequests())
				.andExpect(request().attribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE, aviso));
		ingresar("203.0.113.82", "nadie.inventado", INCORRECTA).andExpect(status().isTooManyRequests())
				.andExpect(request().attribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE, aviso));
	}

	// ------------------------------------------------------------------ límite por cuenta y conexión

	@Test
	void elLimitePorCuentaNoSeSaltaConMayusculasNiEspacios() throws Exception {
		for (String variante : List.of("caja", "Caja", "CAJA", " caja", "caja ")) {
			ingresar("203.0.113.90", variante, INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		ingresar("203.0.113.90", "  CaJa  ", UsuariosDePrueba.CLAVE).andExpect(status().isTooManyRequests());

		assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE nombre_usuario = 'caja'",
				Integer.class)).isEqualTo(5);
	}

	@Test
	void elLimiteNoSeSaltaConOtraFormaDeEscribirLaRutaDeIngreso() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("203.0.113.93", "caja", INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		for (String ruta : List.of("/login/", "/Login", "/login;jsessionid=x", "//login")) {
			String destino;
			try {
				destino = mvc.perform(post(ruta).param("usuario", "caja").param("clave", UsuariosDePrueba.CLAVE)
						.with(csrf()).with(r -> {
							r.setRemoteAddr("203.0.113.93");
							return r;
						})).andReturn().getResponse().getRedirectedUrl();
			}
			catch (Exception rechazada) {
				destino = "rechazada: " + rechazada.getClass().getSimpleName();
			}
			assertThat(destino).as(ruta).isNotEqualTo("/inicio");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario", Long.class))
				.as("ninguna forma de la ruta abrió una sesión").isZero();
	}

	@Test
	void elLimitePorConexionCuentaTambienLosUsuariosQueNoExisten() throws Exception {
		for (int i = 0; i < 20; i++) {
			ingresar("203.0.113.91", "inventado." + i, INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		ingresar("203.0.113.91", "caja", UsuariosDePrueba.CLAVE).andExpect(status().isTooManyRequests())
				.andExpect(request().attribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE,
						"Demasiados intentos desde esta conexión. Espera 15 minutos."));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'INGRESOS_LIMITADOS' "
				+ "AND detalle LIKE '%203.0.113.91%'", Long.class)).isEqualTo(1);
	}

	@Test
	void elLimiteNoGuardaElTextoEscritoComoUsuarioEnLaBitacora() throws Exception {
		String parecidoAClave = "MiClaveSecreta2026";
		for (int i = 0; i < 5; i++) {
			ingresar("203.0.113.92", parecidoAClave, INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}

		assertThat(jdbc.queryForList("SELECT CONCAT(COALESCE(nombre_usuario, ''), ' ', COALESCE(detalle, '')) "
				+ "FROM evento_auditoria", String.class)).noneMatch(fila -> fila.contains(parecidoAClave));
	}

	// ------------------------------------------------------------------ sesión de la base

	@Test
	void laSesionDeLaBaseNaceAbiertaConVencimientoDeDiezHorasYSoloElHash() throws Exception {
		ingresar(new MockHttpSession(), "198.51.100.40", "directora", UsuariosDePrueba.CLAVE)
				.andExpect(redirectedUrl("/inicio"));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM sesion_usuario");
		Duration vigencia = Duration.between(((Timestamp) fila.get("abierta_en")).toLocalDateTime(),
				((Timestamp) fila.get("vence_en")).toLocalDateTime());
		assertThat(vigencia).isEqualTo(Duration.ofHours(10));
		assertThat(fila.get("cerrada_en")).isNull();
		assertThat(fila.get("ip")).isEqualTo("198.51.100.40");
		assertThat((String) fila.get("hash_token")).matches("[0-9a-f]{64}");
	}

	@Test
	void unSegundoIngresoCierraLaSesionAnteriorDeLaBase() throws Exception {
		ingresar(new MockHttpSession(), "198.51.100.41", "directora", UsuariosDePrueba.CLAVE)
				.andExpect(redirectedUrl("/inicio"));
		ingresar(new MockHttpSession(), "198.51.100.42", "directora", UsuariosDePrueba.CLAVE)
				.andExpect(redirectedUrl("/inicio"));

		assertThat(jdbc.queryForList("SELECT COALESCE(motivo_cierre, 'ABIERTA') FROM sesion_usuario ORDER BY id",
				String.class)).containsExactly("OTRA_SESION", "ABIERTA");
	}

	@Test
	void alSalirSeCierraLaSesionDeLaBase() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		ingresar(sesion, "198.51.100.43", "directora", UsuariosDePrueba.CLAVE).andExpect(redirectedUrl("/inicio"));

		mvc.perform(post("/salir").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salio"));

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario", String.class)).isEqualTo("SALIO");
	}

	@Test
	void alExpirarLaSesionHttpSeCierraLaDeLaBase() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		ingresar(sesion, "198.51.100.44", "directora", UsuariosDePrueba.CLAVE).andExpect(redirectedUrl("/inicio"));

		eventos.publishEvent(new HttpSessionDestroyedEvent(sesion));

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario", String.class)).isEqualTo("VENCIO");
	}

	@Test
	void unaSesionDeLaBaseYaCerradaNoSeVuelveACerrar() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		ingresar(sesion, "198.51.100.45", "directora", UsuariosDePrueba.CLAVE).andExpect(redirectedUrl("/inicio"));
		mvc.perform(post("/salir").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salio"));

		// La sesión HTTP ya se invalidó: el evento de expiración llega después y no cambia el motivo.
		eventos.publishEvent(new HttpSessionDestroyedEvent(sesion));

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario", String.class)).isEqualTo("SALIO");
	}

	/** Bloquea la cuenta una hora desde ahora, en hora de Lima (como la bloquea la aplicación). */
	private void bloquear(String usuario) {
		jdbc.update("UPDATE usuario SET bloqueado_hasta = ? WHERE nombre_usuario = ?",
				java.time.LocalDateTime.now(pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)
						.plusHours(1), usuario);
	}

	private ResultActions ingresar(String ip, String usuario, String clave) throws Exception {
		return ingresar(new MockHttpSession(), ip, usuario, clave);
	}

	private ResultActions ingresar(MockHttpSession sesion, String ip, String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").session(sesion).param("usuario", usuario).param("clave", clave).with(csrf())
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				}));
	}
}
