package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug P0 del sprint 9: un segundo ingreso con una sesión abierta respondía 500 («ck_sesion_usuario_cierre»,
 * «La base de datos ha sido cerrada») cuando el pool ya había retirado la conexión que migró. En H2 un CHECK con IN
 * queda atado a la sesión que lo creó (ver {@code AnclaMigracionH2}). Aquí se retiran TODAS las conexiones del pool,
 * como lo hace Hikari a los 30 minutos o el reinicio de devtools, y se ingresa dos veces con la misma persona.
 */
@PruebaIntegracion
class DobleIngresoTrasRetirarConexionesTest {

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

	@MockitoSpyBean
	private SesionesFirmadas sesionesFirmadas;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unSegundoIngresoTrasRetirarLasConexionesDelPoolCierraLaAnteriorYLaBaseSigueViva() throws Exception {
		MockHttpSession primera = new MockHttpSession();
		ingresar(primera).andExpect(redirectedUrl("/inicio"));

		retirarTodasLasConexionesDelPool();

		ingresar(new MockHttpSession()).andExpect(redirectedUrl("/inicio"));
		assertThat(jdbc.queryForList("SELECT COALESCE(motivo_cierre, 'ABIERTA') FROM sesion_usuario ORDER BY id",
				String.class)).containsExactly("OTRA_SESION", "ABIERTA");

		// La base sigue sirviendo a todos: un tercer ingreso y una página con datos responden bien.
		MockHttpSession tercera = new MockHttpSession();
		ingresar(tercera).andExpect(redirectedUrl("/inicio"));
		mvc.perform(get("/inicio").session(tercera)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE cerrada_en IS NULL", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void siNoSePuedeAbrirLaSesionDeLaBaseNadieQuedaDentroYLaBaseSigueSirviendo() throws Exception {
		doThrow(new DataIntegrityViolationException("simulada")).doCallRealMethod().when(sesionesFirmadas)
				.abrir(anyLong(), any(), any());
		MockHttpSession fallida = new MockHttpSession();

		assertThatThrownBy(() -> ingresar(fallida)).hasStackTraceContaining("simulada");

		// La sesión HTTP del ingreso fallido se descartó: no quedó autenticada sin firma.
		assertThat(fallida.isInvalid()).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario", Integer.class)).isZero();
		MockHttpSession buena = new MockHttpSession();
		ingresar(buena).andExpect(redirectedUrl("/inicio"));
		mvc.perform(get("/inicio").session(buena)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE cerrada_en IS NULL", Integer.class))
				.isEqualTo(1);
	}

	/** Retira las conexiones del pool y espera a que H2 cierre sus sesiones (como el maxLifetime de Hikari). */
	private void retirarTodasLasConexionesDelPool() throws InterruptedException {
		HikariDataSource pool = (HikariDataSource) fuente.app();
		List<Integer> antes = jdbc.queryForList("SELECT SESSION_ID FROM INFORMATION_SCHEMA.SESSIONS", Integer.class);
		long previo = Long.MAX_VALUE;
		int iguales = 0;
		// Hasta que solo queden las sesiones que el pool no maneja (la de migración, si la hay) y la de esta consulta.
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
