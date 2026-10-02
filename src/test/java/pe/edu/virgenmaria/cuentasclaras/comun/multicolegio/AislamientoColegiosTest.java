package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aislamiento entre colegios con {@code @TenantId}: un colegio nunca ve ni toca datos de otro,
 * y sin colegio en el contexto no se ve ni se inserta nada (falla cerrado).
 * <p>
 * Sin transacción de prueba ({@code NOT_SUPPORTED}): Hibernate fija el colegio al abrir la
 * sesión, así que cada operación abre la suya dentro de {@link ContextoColegio#en}.
 */
@PruebaJpa
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AislamientoColegiosTest {

	private static final long COLEGIO_A = 1L;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaccion;

	private long colegioB;

	private Usuario directorA;

	private Usuario directorB;

	@BeforeEach
	void prepararDosColegios() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		directorA = ContextoColegio.en(COLEGIO_A, () -> usuarios.save(usuario("director.a", "Ana Directora", Rol.DIRECTOR)));
		ContextoColegio.en(COLEGIO_A, () -> usuarios.save(usuario("caja.a", "Carlos Cajero", Rol.CAJA)));
		directorB = ContextoColegio.en(colegioB, () -> usuarios.save(usuario("director.b", "Beatriz Directora", Rol.DIRECTOR)));
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unUsuarioDelColegioANoPuedeLeerPorIdUnUsuarioDelColegioB() {
		ContextoColegio.en(COLEGIO_A, () -> {
			assertThat(usuarios.findById(directorB.getId())).isEmpty();
			assertThat(usuarios.existsById(directorB.getId())).isFalse();
			assertThat(usuarios.findById(directorA.getId())).isPresent();
		});
	}

	@Test
	void listarYContarSoloDevuelvenLosDelColegioActual() {
		ContextoColegio.en(COLEGIO_A, () -> {
			assertThat(usuarios.findAll()).extracting(Usuario::getNombreUsuario)
					.containsExactlyInAnyOrder("director.a", "caja.a");
			assertThat(usuarios.count()).isEqualTo(2);
			assertThat(usuarios.findAllByOrderByNombreCompletoAsc()).extracting(Usuario::getNombreUsuario)
					.containsExactly("director.a", "caja.a");
		});
		ContextoColegio.en(colegioB, () -> {
			assertThat(usuarios.findAll()).extracting(Usuario::getNombreUsuario).containsExactly("director.b");
			assertThat(usuarios.count()).isEqualTo(1);
		});
	}

	@Test
	void consultasDerivadasYJpqlFiltranPorColegio() {
		ContextoColegio.en(COLEGIO_A, () -> {
			assertThat(usuarios.findByNombreUsuario("director.b")).isEmpty();
			assertThat(usuarios.contarActivosConRol(Rol.DIRECTOR)).isEqualTo(1);
		});
		ContextoColegio.en(COLEGIO_A, () -> transaccion.executeWithoutResult(
				estado -> assertThat(usuarios.bloquearPorNombreUsuario("director.b")).isEmpty()));
		ContextoColegio.en(colegioB, () -> {
			assertThat(usuarios.findByNombreUsuario("director.b")).isPresent();
			assertThat(usuarios.contarActivosConRol(Rol.CAJA)).isZero();
		});
	}

	@Test
	void sinColegioEnContextoNoSeVeNingunDato() {
		assertThat(ContextoColegio.actual()).isEqualTo(ContextoColegio.NINGUNO);
		assertThat(usuarios.findAll()).isEmpty();
		assertThat(usuarios.count()).isZero();
		assertThat(usuarios.findById(directorA.getId())).isEmpty();
		assertThat(usuarios.findByNombreUsuario("director.a")).isEmpty();
	}

	@Test
	void sinColegioEnContextoNoSePuedeInsertar() {
		assertThatThrownBy(() -> usuarios.save(usuario("intruso", "Sin Colegio", Rol.DOCENTE)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'intruso'", Long.class))
				.isZero();
	}

	@Test
	void elColegioSeAsignaAutomaticamenteAlGuardar() {
		assertThat(directorA.getColegioId()).isEqualTo(COLEGIO_A);
		assertThat(directorB.getColegioId()).isEqualTo(colegioB);
		assertThat(jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE id = ?", Long.class, directorB.getId()))
				.isEqualTo(colegioB);
		assertThat(directorB.getCreadoEn()).isNotNull();
		assertThat(directorB.getActualizadoEn()).isNotNull();
		assertThat(directorB.getCreadoPor()).isEqualTo("sistema");
		assertThat(directorB.getVersion()).isZero();
	}

	@Test
	void noSePuedeMoverUnaEntidadAOtroColegioConMerge() {
		Usuario cargadoEnA = ContextoColegio.en(COLEGIO_A, () -> usuarios.findById(directorA.getId()).orElseThrow());

		// Desde el colegio B la fila no existe: Hibernate la trata como obsoleta y rechaza el merge.
		assertThatThrownBy(() -> ContextoColegio.en(colegioB, () -> usuarios.save(cargadoEnA)))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE id = ?", Long.class, directorA.getId()))
				.isEqualTo(COLEGIO_A);
		ContextoColegio.en(colegioB, () -> assertThat(usuarios.count()).isEqualTo(1));
	}

	@Test
	void cambiarDeColegioDentroDeUnaTransaccionLanzaExcepcion() {
		ContextoColegio.en(COLEGIO_A, () -> transaccion.executeWithoutResult(estado -> {
			assertThatThrownBy(() -> ContextoColegio.en(colegioB, () -> usuarios.count()))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("transacción abierta");
			assertThatThrownBy(() -> ContextoColegio.comoSistema(() -> usuarios.count()))
					.isInstanceOf(IllegalStateException.class);
		}));
	}

	@Test
	void comoSistemaVeLosUsuariosDeTodosLosColegios() {
		List<String> todos = ContextoColegio.comoSistema(
				() -> usuarios.findAll().stream().map(Usuario::getNombreUsuario).toList());
		assertThat(todos).containsExactlyInAnyOrder("director.a", "caja.a", "director.b");
	}

	private static Usuario usuario(String nombreUsuario, String nombreCompleto, Rol rol) {
		return Usuario.nuevo(nombreUsuario, nombreCompleto, null, "{bcrypt}hash-de-prueba", Set.of(rol));
	}
}
