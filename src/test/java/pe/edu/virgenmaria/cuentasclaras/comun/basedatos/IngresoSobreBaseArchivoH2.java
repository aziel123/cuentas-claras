package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La demo persistente (perfil {@code dev,persistente}, H2 en archivo): el doble ingreso cierra la sesión anterior con
 * OTRA_SESION ({@code ck_sesion_usuario_cierre}, un CHECK con IN) aunque el pool haya retirado todas sus conexiones, en el
 * primer arranque (la aplicación migra) y en el segundo (la base ya tiene datos). Ver {@link AnclaMigracionH2}: sin el
 * ancla, en el primer arranque el CHECK queda atado a la conexión del pool que migró y, al retirarla con la base abierta,
 * falla con 23514 (y con 90098, «La base de datos ha sido cerrada», en la aplicación).
 */
abstract class IngresoSobreBaseArchivoH2 {

	static final String OPCIONES = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_ON_EXIT=FALSE;"
			+ "LOCK_TIMEOUT=30000";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FuenteDatosEnrutada fuente;

	@Value("${spring.datasource.url}")
	private String url;

	static void borrarCarpeta(Path carpeta) throws IOException {
		if (!Files.exists(carpeta)) {
			return;
		}
		try (Stream<Path> rutas = Files.walk(carpeta)) {
			for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		}
	}

	@BeforeEach
	void preparar() {
		// El mismo esquema que la base en memoria de las pruebas: la limpieza y los usuarios de prueba no cambian.
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unSegundoIngresoTrasRenovarLasConexionesDelPoolCierraLaAnteriorYLaBaseSigueViva() throws Exception {
		assertThat(jdbc.queryForObject("SELECT DATABASE_PATH()", String.class)).isNotNull();
		ingresar(new MockHttpSession()).andExpect(redirectedUrl("/inicio"));

		// Una conexión fuera del pool mantiene abierta la base de archivo mientras el pool renueva TODAS las suyas, como
		// el maxLifetime de Hikari, que las retira de a una: la base no se cierra ni se vuelve a abrir.
		try (Connection retenida = DriverManager.getConnection(url, "sa", "")) {
			retirarTodasLasConexionesDelPool();
		}

		MockHttpSession segunda = new MockHttpSession();
		ingresar(segunda).andExpect(redirectedUrl("/inicio"));
		mvc.perform(get("/inicio").session(segunda)).andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT COALESCE(motivo_cierre, 'ABIERTA') FROM sesion_usuario ORDER BY id",
				String.class)).containsExactly("OTRA_SESION", "ABIERTA");
	}

	/** Retira las conexiones del pool y espera a que H2 cierre sus sesiones (como el maxLifetime de Hikari). */
	private void retirarTodasLasConexionesDelPool() throws InterruptedException {
		HikariDataSource pool = (HikariDataSource) fuente.app();
		List<Integer> antes = jdbc.queryForList("SELECT SESSION_ID FROM INFORMATION_SCHEMA.SESSIONS", Integer.class);
		long previo = Long.MAX_VALUE;
		int iguales = 0;
		for (int i = 0; i < 200 && iguales < 10; i++) {
			pool.getHikariPoolMXBean().softEvictConnections();
			Thread.sleep(50);
			long quedan = jdbc.queryForList("SELECT SESSION_ID FROM INFORMATION_SCHEMA.SESSIONS", Integer.class).stream()
					.filter(antes::contains).count();
			iguales = quedan == previo ? iguales + 1 : 0;
			previo = quedan;
		}
	}

	private ResultActions ingresar(MockHttpSession sesion) throws Exception {
		return mvc.perform(post("/login").session(sesion).param("usuario", "directora")
				.param("clave", UsuariosDePrueba.CLAVE).with(csrf()));
	}
}
