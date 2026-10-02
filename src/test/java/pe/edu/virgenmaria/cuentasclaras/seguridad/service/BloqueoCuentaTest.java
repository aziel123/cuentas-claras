package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * 5 intentos fallidos seguidos bloquean la cuenta 15 minutos. El reloj de la aplicación se
 * reemplaza por uno ajustable para no esperar de verdad.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BloqueoCuentaTest {

	static final Instant INICIO = ConfiguracionRelojAjustable.INICIO;

	private static final String CLAVE = UsuariosDePrueba.CLAVE;

	private static final String INCORRECTA = "no es la clave correcta";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ProveedorAutenticacion proveedor;

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
		reloj.fijar(INICIO);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cincoIntentosFallidosBloqueanLaCuenta() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar(INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}

		Map<String, Object> fila = jdbc.queryForMap(
				"SELECT intentos_fallidos, bloqueado_hasta FROM usuario WHERE nombre_usuario = 'caja'");
		assertThat(fila.get("bloqueado_hasta")).isNotNull();
		assertThat(((java.sql.Timestamp) fila.get("bloqueado_hasta")).toLocalDateTime())
				.isEqualTo(LocalDateTime.now(reloj).plusMinutes(15));
		assertThat(contar("INGRESO_FALLIDO")).isEqualTo(5);
		assertThat(contar("CUENTA_BLOQUEADA")).isEqualTo(1);
	}

	@Test
	void cuentaBloqueadaRechazaInclusoLaClaveCorrecta() throws Exception {
		bloquear();

		// Mismo mensaje que una clave incorrecta: no revela que la cuenta existe ni que está bloqueada.
		ingresar(CLAVE).andExpect(redirectedUrl("/login?error"));

		mvc.perform(get("/login").param("error", ""))
				.andExpect(content().string(containsString(
						"Usuario o clave incorrectos. Después de 5 intentos fallidos la cuenta se bloquea 15 minutos.")));
		assertThat(contar("INGRESO_RECHAZADO_BLOQUEADA")).isEqualTo(1);
	}

	@Test
	void laCuentaSeDesbloqueaSolaPasados15Minutos() throws Exception {
		bloquear();

		reloj.avanzar(Duration.ofMinutes(14).plusSeconds(59));
		ingresar(CLAVE).andExpect(redirectedUrl("/login?error"));
		assertThat(contar("INGRESO_RECHAZADO_BLOQUEADA")).isEqualTo(1);

		reloj.avanzar(Duration.ofSeconds(1));
		ingresar(CLAVE).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	void ingresoExitosoReiniciaElContador() throws Exception {
		for (int i = 0; i < 4; i++) {
			ingresar(INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		assertThat(intentos()).isEqualTo(4);

		ingresar(CLAVE).andExpect(redirectedUrl("/inicio"));
		assertThat(intentos()).isZero();

		for (int i = 0; i < 4; i++) {
			ingresar(INCORRECTA).andExpect(redirectedUrl("/login?error"));
		}
		ingresar(CLAVE).andExpect(redirectedUrl("/inicio"));
		assertThat(contar("CUENTA_BLOQUEADA")).isZero();
	}

	@Test
	void directorDesbloqueaYQuedaAuditado() throws Exception {
		Usuario director = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", CLAVE, false, Rol.DIRECTOR);
		long idCaja = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre_usuario = 'caja'", Long.class);
		bloquear();
		ingresar(CLAVE).andExpect(redirectedUrl("/login?error"));

		mvc.perform(post("/usuarios/" + idCaja + "/desbloquear").with(UsuariosDePrueba.como(director)).with(csrf())
						.param("motivo", "Confirmé su identidad en persona"))
				.andExpect(redirectedUrl("/usuarios/" + idCaja));

		ingresar(CLAVE).andExpect(redirectedUrl("/inicio"));
		Map<String, Object> evento = jdbc.queryForMap(
				"SELECT * FROM evento_auditoria WHERE accion = 'CUENTA_DESBLOQUEADA'");
		assertThat(evento.get("nombre_usuario")).isEqualTo("director");
		assertThat(evento.get("entidad_id")).isEqualTo(String.valueOf(idCaja));
		assertThat((String) evento.get("detalle")).contains("Confirmé su identidad en persona");
	}

	@Test
	void trasExpirarElBloqueoUnSoloFalloNoVuelveABloquear() throws Exception {
		bloquear();
		reloj.avanzar(Duration.ofMinutes(15));

		ingresar(INCORRECTA).andExpect(redirectedUrl("/login?error"));

		assertThat(intentos()).isEqualTo(1);
		ingresar(CLAVE).andExpect(redirectedUrl("/inicio"));
		assertThat(contar("CUENTA_BLOQUEADA")).isEqualTo(1);
	}

	@Test
	void intentosEnParaleloNoSuperanElBloqueoNiLoDeshaceUnIngresoCorrecto() throws Exception {
		int total = 20;
		ExecutorService ejecutor = Executors.newFixedThreadPool(total);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<Boolean>> resultados = new ArrayList<>();
		try {
			for (int i = 0; i < total; i++) {
				String clave = i == total / 2 ? CLAVE : INCORRECTA;
				resultados.add(ejecutor.submit(() -> {
					largada.await();
					try {
						proveedor.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("caja", clave));
						return true;
					}
					catch (AuthenticationException e) {
						return false;
					}
				}));
			}
			largada.countDown();
			for (Future<Boolean> resultado : resultados) {
				resultado.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			ejecutor.shutdownNow();
		}

		// Cada intento se atendió de uno en uno y quedó registrado exactamente una vez.
		assertThat(contar("INGRESO_FALLIDO") + contar("INGRESO_RECHAZADO_BLOQUEADA") + contar("INGRESO_EXITOSO"))
				.isEqualTo(total);
		assertThat(contar("CUENTA_BLOQUEADA")).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE nombre_usuario = 'caja'",
				LocalDateTime.class)).as("la cuenta termina bloqueada").isNotNull();
		long primerBloqueo = jdbc.queryForObject(
				"SELECT MIN(secuencia) FROM evento_auditoria WHERE accion = 'CUENTA_BLOQUEADA'", Long.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'INGRESO_EXITOSO' "
				+ "AND secuencia > ?", Long.class, primerBloqueo)).as("nadie entra después del bloqueo").isZero();
		// Mientras está bloqueada no se cuentan más fallos: como máximo 5 antes de cada bloqueo.
		assertThat(contar("INGRESO_FALLIDO")).isLessThanOrEqualTo(5L * contar("CUENTA_BLOQUEADA") + 4);
	}

	@Test
	void intentosConUsuarioInexistenteQuedanAuditadosSinColegio() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("usuario", "No.Existe").param("clave", INCORRECTA))
				.andExpect(redirectedUrl("/login?error"));

		Map<String, Object> evento = jdbc.queryForMap(
				"SELECT colegio_id, usuario_id, nombre_usuario, accion FROM evento_auditoria");
		assertThat(evento.get("colegio_id")).isNull();
		assertThat(evento.get("usuario_id")).isNull();
		assertThat((String) evento.get("nombre_usuario")).startsWith("desconocido-").doesNotContainIgnoringCase("existe");
		assertThat(evento.get("accion")).isEqualTo("INGRESO_FALLIDO");
	}

	private void bloquear() throws Exception {
		for (int i = 0; i < 5; i++) {
			ingresar(INCORRECTA);
		}
	}

	private ResultActions ingresar(String clave) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("usuario", "caja").param("clave", clave));
	}

	private int intentos() {
		return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE nombre_usuario = 'caja'", Integer.class);
	}

	private long contar(String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}
}
