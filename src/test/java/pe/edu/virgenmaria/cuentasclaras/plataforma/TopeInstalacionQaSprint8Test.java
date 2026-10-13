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
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 8 (sección 3.1, decisión 138): una instalación compartida tiene un tope de 25 colegios u 8,000 alumnos
 * ({@code cuentasclaras.plataforma.max-alumnos}; aquí 1 para probarlo). Pasado eso, el colegio siguiente va en otra
 * instalación.
 *
 * <p>Dada una instalación compartida que ya pasó su tope de alumnos, cuando el operador da de alta otro colegio, entonces
 * se rechaza sin crear nada (QA-S8-8).
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-alumnos=1")
@ActiveProfiles("test")
class TopeInstalacionQaSprint8Test {

	@Autowired
	private AltaColegio alta;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

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
	void debeRechazarElAltaCuandoLaInstalacionYaLlegoAlTopeDeAlumnos() {
		EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Long.class)).isGreaterThan(1L);
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.tope"));
		long antes = colegiosCreados();

		assertThatThrownBy(() -> alta.crear(EscenarioPlataforma.alta("Colegio Sobre el Tope", EscenarioPlataforma.RUC_C,
				"titular.tope", "45670042", "987650142"), null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("alumnos");
		assertThat(colegiosCreados()).isEqualTo(antes);
	}
}
