package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 8 (sección 3.1, decisión 108): una instalación DEDICADA tiene un solo colegio.
 *
 * <p>Dada una instalación DEDICADA cuyo colegio ya opera (tiene cuentas), cuando el operador da de alta otro colegio,
 * entonces se rechaza sin crear nada.
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.instalacion=DEDICADA")
@ActiveProfiles("test")
class InstalacionQaSprint8Test {

	@Autowired
	private AltaColegio alta;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private long colegiosCreados() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM colegio WHERE tipo = 'COLEGIO'", Long.class);
	}

	@Test
	void debeRechazarUnSegundoColegioEnUnaInstalacionDedicadaQueYaOpera() {
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.dedicada", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.dedicada"));
		long antes = colegiosCreados();

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Segundo", EscenarioPlataforma.RUC_B,
				"titular.dedicada", "45670041", "987650141"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("DEDICADA");
		assertThat(colegiosCreados()).isEqualTo(antes);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'titular.dedicada'", Long.class))
				.isZero();
	}
}
