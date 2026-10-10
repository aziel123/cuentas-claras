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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El inicializador es {@code @Profile("prod")}: aquí se construye a mano sobre la base de pruebas.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InicializadorPromotorTest {

	private static final String CLAVE_INICIAL = "clave inicial del colegio";

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
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void creaPromotorSiNoHayUsuarios() {
		assertThat(inicializador(1L, "Promotora", "María Elena Torres", CLAVE_INICIAL).crearSiNoHayUsuarios()).isTrue();

		List<Usuario> todos = ContextoColegio.comoSistema(() -> usuarios.findAll());
		assertThat(todos).singleElement().satisfies(u -> {
			assertThat(u.getNombreUsuario()).isEqualTo("promotora");
			assertThat(u.getColegioId()).isEqualTo(1L);
			assertThat(u.getRoles()).containsExactly(Rol.PROMOTOR);
			assertThat(codificador.matches(CLAVE_INICIAL, u.getClaveHash())).isTrue();
		});
		assertThat(jdbc.queryForObject("SELECT accion FROM evento_auditoria", String.class)).isEqualTo("USUARIO_CREADO");
	}

	@Test
	void elPromotorInicialDebeCambiarSuClave() {
		inicializador(1L, "promotora", "María Elena Torres", CLAVE_INICIAL).crearSiNoHayUsuarios();

		assertThat(ContextoColegio.comoSistema(() -> usuarios.findAll())).singleElement()
				.satisfies(u -> assertThat(u.isDebeCambiarClave()).isTrue());
	}

	@Test
	void noTocaNadaSiYaHayUsuarios() {
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "director", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);

		assertThat(inicializador(1L, "", "", "").crearSiNoHayUsuarios()).isFalse();

		assertThat(ContextoColegio.comoSistema(() -> usuarios.count())).isEqualTo(1);
	}

	@Test
	void fallaConMensajeClaroSiFaltanVariables() {
		assertThatThrownBy(() -> inicializador(1L, "", "María", "").crearSiNoHayUsuarios())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CC_PROMOTOR_USUARIO")
				.hasMessageContaining("CC_PROMOTOR_CLAVE")
				.hasMessageNotContaining("CC_PROMOTOR_NOMBRE");
		assertThat(ContextoColegio.comoSistema(() -> usuarios.count())).isZero();
	}

	@Test
	void rechazaClaveInicialDebil() {
		assertThatThrownBy(() -> inicializador(1L, "promotora", "María", "1234567890").crearSiNoHayUsuarios())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CC_PROMOTOR_CLAVE")
				.hasMessageNotContaining("1234567890");
		assertThat(ContextoColegio.comoSistema(() -> usuarios.count())).isZero();
	}

	@Test
	void fallaSiElColegioNoExiste() {
		assertThatThrownBy(() -> inicializador(99L, "promotora", "María", CLAVE_INICIAL).crearSiNoHayUsuarios())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CC_COLEGIO_ID");
	}

	private InicializadorPromotor inicializador(Long colegioId, String usuario, String nombre, String clave) {
		return new InicializadorPromotor(usuarios, colegios, codificador, auditoria,
				new pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad(transacciones), colegioId, usuario,
				nombre, clave);
	}
}
