package pe.edu.virgenmaria.cuentasclaras.seguridad.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SelladorAuditoria;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los datos de demostración son {@code @Profile("dev")}: aquí se construyen a mano sobre la base de pruebas.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatosDemoDevTest {

	private static final String CLAVE_DEMO = "demo-cuentas-claras-2026";

	private final PasswordEncoder codificador = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void creaUnUsuarioPorRol() {
		assertThat(datosDemo("jdbc:h2:mem:demo;MODE=MySQL", CLAVE_DEMO).crearSiCorresponde()).isTrue();

		List<Usuario> enColegioPrincipal = ContextoColegio.en(1L, () -> usuarios.findAll());
		assertThat(enColegioPrincipal).flatExtracting(Usuario::getRoles).containsExactlyInAnyOrder(Rol.values());
		assertThat(enColegioPrincipal).extracting(Usuario::getNombreUsuario)
				.containsExactlyInAnyOrder("promotor", "director", "administracion", "caja", "docente", "apoderado");
		assertThat(enColegioPrincipal).allSatisfy(u -> {
			assertThat(u.isDebeCambiarClave()).isFalse();
			assertThat(codificador.matches(CLAVE_DEMO, u.getClaveHash())).isTrue();
		});

		long colegioB = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = 'Colegio de Prueba B'", Long.class);
		assertThat(ContextoColegio.en(colegioB, () -> usuarios.findAll())).extracting(Usuario::getNombreUsuario)
				.containsExactly("promotor.b");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'USUARIO_CREADO'",
				Long.class)).isEqualTo(7);
	}

	@Test
	void seNiegaSiLaBaseNoEsH2EnMemoria() {
		assertThat(datosDemo("jdbc:mysql://localhost:3306/cuentasclaras", CLAVE_DEMO).crearSiCorresponde()).isFalse();
		assertThat(datosDemo("jdbc:h2:file:./datos/cc", CLAVE_DEMO).crearSiCorresponde()).isFalse();
		assertThat(datosDemo("", CLAVE_DEMO).crearSiCorresponde()).isFalse();

		assertThat(ContextoColegio.comoSistema(() -> usuarios.count())).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio", Long.class)).isEqualTo(1);
	}

	@Test
	void noRepiteSiYaHayUsuarios() {
		datosDemo("jdbc:h2:mem:demo", CLAVE_DEMO).crearSiCorresponde();

		assertThat(datosDemo("jdbc:h2:mem:demo", CLAVE_DEMO).crearSiCorresponde()).isFalse();
		assertThat(ContextoColegio.comoSistema(() -> usuarios.count())).isEqualTo(7);
	}

	@Test
	void rechazaUnaClaveDeDemoDebil() {
		assertThatThrownBy(() -> datosDemo("jdbc:h2:mem:demo", "corta").crearSiCorresponde())
				.hasMessageContaining("al menos 10");
	}

	private DatosDemoDev datosDemo(String url, String clave) {
		return new DatosDemoDev(usuarios, colegios, codificador, auditoria, transacciones, reloj, url, clave);
	}
}
