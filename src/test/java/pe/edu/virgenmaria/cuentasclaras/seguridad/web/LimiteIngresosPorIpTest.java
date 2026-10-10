package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 3 (H9, E24, decisión 104): límite de intentos de ingreso por conexión, con los valores de producción
 * (5 con un usuario y 20 con cualquiera desde una conexión, 15 minutos; la cuenta se bloquea a los 15 desde cualquier
 * conexión). Un tercero no puede bloquear la cuenta de la promotora desde su conexión; un ataque desde varias sí la bloquea.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = { "cuentasclaras.seguridad.intentos-maximos=15", "cuentasclaras.sesion.intentos-por-ip=20",
		"cuentasclaras.sesion.intentos-por-cuenta-e-ip=5", "cuentasclaras.sesion.ventana-intentos-ip=15m" })
class LimiteIngresosPorIpTest {

	private static final String CLAVE = UsuariosDePrueba.CLAVE;

	private static final String INCORRECTA = "no es la clave de la promotora";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", CLAVE, false, Rol.PROMOTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unTerceroNoBloqueaLaCuentaDeLaPromotora() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("203.0.113.10", "promotora", INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		// Desde su conexión, el tercero ya no llega a la cuenta: 429 sin mirar la clave (tampoco la correcta).
		for (int i = 0; i < 10; i++) {
			ingresar("203.0.113.10", "promotora", INCORRECTA).andExpect(status().isTooManyRequests())
					.andExpect(forwardedUrl(FiltroLimiteIngresos.RUTA_ESPERA))
					.andExpect(request().attribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE,
							"Demasiados intentos con este usuario desde esta conexión. Espera 15 minutos."));
		}
		ingresar("203.0.113.10", "promotora", CLAVE).andExpect(status().isTooManyRequests());

		assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE nombre_usuario = 'promotora'",
				Integer.class)).as("solo 5 intentos llegaron a la cuenta").isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE nombre_usuario = 'promotora'",
				java.sql.Timestamp.class)).as("la cuenta no se bloqueó").isNull();
		// La promotora, desde su conexión, ingresa sin problema.
		ingresar("198.51.100.20", "promotora", CLAVE).andExpect(redirectedUrl("/inicio"));
		assertThat(contar("INGRESOS_LIMITADOS")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'INGRESOS_LIMITADOS'",
				String.class)).contains("203.0.113.10").contains("La cuenta no se bloqueó");
	}

	@Test
	void veinteFallosDesdeUnaConexionLaHacenEsperarConCualquierUsuario() throws Exception {
		String[] probados = { "a.uno", "b.dos", "c.tres", "d.cuatro", "e.cinco" };
		for (String usuario : probados) {
			for (int i = 0; i < 4; i++) {
				ingresar("203.0.113.30", usuario, INCORRECTA).andExpect(redirectedUrl("/login?error"));
			}
		}
		ingresar("203.0.113.30", "caja", CLAVE).andExpect(status().isTooManyRequests())
				.andExpect(request().attribute(FiltroLimiteIngresos.ATRIBUTO_MENSAJE,
						"Demasiados intentos desde esta conexión. Espera 15 minutos."))
				.andExpect(header().exists("Content-Security-Policy"));
		// Otra conexión no espera.
		ingresar("198.51.100.40", "caja", CLAVE).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void pasados15MinutosLaConexionVuelveAIntentar() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar("203.0.113.50", "caja", INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		ingresar("203.0.113.50", "caja", CLAVE).andExpect(status().isTooManyRequests());

		reloj.avanzar(Duration.ofMinutes(15).plusSeconds(1));
		ingresar("203.0.113.50", "caja", CLAVE).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void unAtaqueDesdeVariasConexionesSiBloqueaLaCuenta() throws Exception {
		for (String ip : new String[] { "203.0.113.61", "203.0.113.62", "203.0.113.63" }) {
			for (int i = 0; i < 5; i++) {
				ingresar(ip, "caja", INCORRECTA).andExpect(redirectedUrl("/login?error"));
			}
		}
		assertThat(jdbc.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE nombre_usuario = 'caja'",
				java.sql.Timestamp.class)).as("15 intentos desde 3 conexiones bloquean la cuenta").isNotNull();
		assertThat(contar("CUENTA_BLOQUEADA")).isEqualTo(1);
	}

	@Test
	void sinTokenCsrfNoSeCuentaNiSeIntenta() throws Exception {
		for (int i = 0; i < 30; i++) {
			mvc.perform(post("/login").param("usuario", "caja").param("clave", INCORRECTA)
					.with(r -> { r.setRemoteAddr("203.0.113.70"); return r; }))
					.andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE nombre_usuario = 'caja'",
				Integer.class)).isZero();
		ingresar("203.0.113.70", "caja", CLAVE).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void laPaginaDeEsperaMuestraElAvisoConUn429() throws Exception {
		mvc.perform(post(FiltroLimiteIngresos.RUTA_ESPERA).with(csrf()).requestAttr(FiltroLimiteIngresos.ATRIBUTO_MENSAJE,
				"Demasiados intentos desde esta conexión. Espera 15 minutos."))
				.andExpect(status().isTooManyRequests())
				.andExpect(content().string(containsString("Demasiados intentos desde esta conexión. Espera 15 minutos.")))
				.andExpect(content().string(containsString("name=\"usuario\"")));
	}

	private ResultActions ingresar(String ip, String usuario, String clave) throws Exception {
		return mvc.perform(post("/login").param("usuario", usuario).param("clave", clave).with(csrf())
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				}));
	}

	private long contar(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}
}
