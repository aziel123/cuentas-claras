package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repetir una petición prohibida no inunda la bitácora: como máximo un evento por usuario y ruta por minuto.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccesoDenegadoLimitadoTest {

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

	private Usuario caja;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.limite", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		// El reloj se comparte con las demás clases de prueba: se devuelve a su inicio.
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void comoMaximoUnoPorUsuarioYRutaPorMinuto() throws Exception {
		for (int i = 0; i < 30; i++) {
			mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(caja))).andExpect(status().isForbidden());
		}
		assertThat(denegados()).isEqualTo(1);

		mvc.perform(get("/auditoria").with(UsuariosDePrueba.como(caja))).andExpect(status().isForbidden());
		assertThat(denegados()).as("otra ruta sí se registra").isEqualTo(2);

		reloj.avanzar(Duration.ofSeconds(59));
		mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(caja))).andExpect(status().isForbidden());
		assertThat(denegados()).isEqualTo(2);

		reloj.avanzar(Duration.ofSeconds(1));
		mvc.perform(get("/usuarios").with(UsuariosDePrueba.como(caja))).andExpect(status().isForbidden());
		assertThat(denegados()).as("pasado un minuto se vuelve a registrar").isEqualTo(3);
	}

	private long denegados() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'ACCESO_DENEGADO'", Long.class);
	}
}
