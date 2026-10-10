package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 3 (A07 de OWASP, decisión 83; E18): una sesión dura como máximo 10 horas desde el ingreso aunque haya
 * actividad. Un celular olvidado con la sesión abierta deja de servir.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class SesionMaximaTest {

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
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void conActividadLaSesionSigueHastaLas10HorasYAhiSeCierra() throws Exception {
		MockHttpSession sesion = ingresar();
		// Actividad cada 25 minutos (menos que los 30 de inactividad): la sesión sigue.
		for (int i = 0; i < 23; i++) {
			reloj.avanzar(Duration.ofMinutes(25));
			mvc.perform(get("/inicio").session(sesion)).andExpect(status().isOk());
		}
		reloj.avanzar(Duration.ofHours(10).minus(Duration.ofMinutes(25L * 23)));
		mvc.perform(get("/inicio").session(sesion)).andExpect(redirectedUrl("/login?expirada"));
		assertThat(sesion.isInvalid()).as("la sesión HTTP se invalidó").isTrue();
	}

	@Test
	void antesDeLas10HorasLaSesionSirve() throws Exception {
		MockHttpSession sesion = ingresar();
		reloj.avanzar(Duration.ofHours(9).plusMinutes(59));
		mvc.perform(get("/inicio").session(sesion)).andExpect(status().isOk());
	}

	private MockHttpSession ingresar() throws Exception {
		MockHttpSession sesion = new MockHttpSession();
		mvc.perform(post("/login").session(sesion).param("usuario", "directora").param("clave", UsuariosDePrueba.CLAVE)
				.with(csrf())).andExpect(redirectedUrl("/inicio"));
		return sesion;
	}
}
