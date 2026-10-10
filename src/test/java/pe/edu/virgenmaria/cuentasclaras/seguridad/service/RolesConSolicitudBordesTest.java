package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 7 (cambio de roles con solicitud; decisiones 84 y 85, H2 y E6) sobre H2: el colegio nunca queda sin
 * Promotoría, un cambio de roles toca SOLO la diferencia (contado con un trigger de H2 sobre {@code usuario_rol}) y la
 * excepción de la primera Dirección (una sola Promotoría y ninguna Dirección activa) no sirve para más que eso.
 */
@PruebaIntegracion
class RolesConSolicitudBordesTest {

	private static final String MOTIVO = "Cambio de funciones aprobado en reunión";

	@Autowired
	private ServicioUsuarios servicio;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private SesionesFirmadas sesiones;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.execute("CREATE TRIGGER IF NOT EXISTS qa_usuario_rol_alta AFTER INSERT ON usuario_rol FOR EACH ROW CALL '"
				+ ContadorFilasRolH2.class.getName() + "'");
		jdbc.execute("CREATE TRIGGER IF NOT EXISTS qa_usuario_rol_baja AFTER DELETE ON usuario_rol FOR EACH ROW CALL '"
				+ ContadorFilasRolH2.class.getName() + "'");
		promotora = guardar("promotora", Rol.PROMOTOR, Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(promotora);
	}

	@AfterEach
	void limpiar() {
		jdbc.execute("DROP TRIGGER IF EXISTS qa_usuario_rol_alta");
		jdbc.execute("DROP TRIGGER IF EXISTS qa_usuario_rol_baja");
		ContadorFilasRolH2.reiniciar();
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ solo la diferencia (H2)

	@Test
	void debeQuitarSoloElRolQueCambiaSinBorrarNiReinsertarPromotoria() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR, Rol.DOCENTE);
		ContadorFilasRolH2.reiniciar();

		assertThat(servicio.cambiarRoles(otra.getId(), new CambiarRolesRequest(EnumSet.of(Rol.PROMOTOR), MOTIVO)))
				.as("sin Promotoría ni Dirección de por medio: directo").isFalse();

		assertThat(ContadorFilasRolH2.FILAS).containsExactly("BAJA DOCENTE");
		assertThat(roles(otra)).containsExactly("PROMOTOR");
	}

	@Test
	void debeCambiarAdministracionPorDocenteConUnBorradoYUnaInsercion() {
		Usuario ana = guardar("ana.administracion", Rol.ADMINISTRACION);
		ContadorFilasRolH2.reiniciar();

		servicio.cambiarRoles(ana.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO));

		assertThat(ContadorFilasRolH2.FILAS).containsExactlyInAnyOrder("BAJA ADMINISTRACION", "ALTA DOCENTE");
	}

	@Test
	void alAprobarDireccionSoloSeInsertaEseRol() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);
		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).isTrue();
		ContadorFilasRolH2.reiniciar();

		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitudPendienteDe(docente), "Confirmado en persona con la promotora");

		assertThat(ContadorFilasRolH2.FILAS).containsExactly("ALTA DIRECTOR");
		assertThat(jdbc.queryForObject("SELECT roles_solicitud_id FROM usuario WHERE id = ?", Long.class, docente.getId()))
				.as("la solicitud quedó enlazada en la cuenta").isNotNull();
	}

	@Test
	void alAprobarQueSeQuiteDireccionNoSeTocaElOtroRol() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		Usuario subdirector = guardar("subdirector", Rol.DIRECTOR, Rol.DOCENTE);
		assertThat(servicio.cambiarRoles(subdirector.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO)))
				.isTrue();
		ContadorFilasRolH2.reiniciar();

		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitudPendienteDe(subdirector), "Confirmado en persona con la promotora");

		assertThat(ContadorFilasRolH2.FILAS).containsExactly("BAJA DIRECTOR");
	}

	// ------------------------------------------------------------------ nunca sin Promotoría

	@Test
	void noDebeQuedarElColegioSinPromotoriaConDosSolicitudesCruzadas() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR, Rol.DOCENTE);
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		// Cada promotora pide quitarle Promotoría a la otra.
		assertThat(servicio.cambiarRoles(otra.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO))).isTrue();
		UsuariosDePrueba.iniciarSesion(otra);
		assertThat(servicio.cambiarRoles(promotora.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO)))
				.isTrue();
		Long primera = solicitudPendienteDe(otra);
		Long segunda = solicitudPendienteDe(promotora);

		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(primera, "Confirmado en persona con las dos");
		assertThatThrownBy(() -> bandeja.aprobar(segunda, "Confirmado en persona con las dos"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("último");

		assertThat(promotorasActivas()).isEqualTo(1);
		assertThat(roles(promotora)).contains("PROMOTOR");
	}

	@Test
	void noDebeQuedarSinPromotoriaSiLaOtraSeDesactivaMientrasEsperaLaSolicitud() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR, Rol.DOCENTE);
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(otra);
		assertThat(servicio.cambiarRoles(promotora.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO)))
				.isTrue();
		// Mientras la solicitud espera, la promotora pide desactivar a la otra (con dos Promotorías activas se puede) y la
		// directora lo aprueba (correcciones del sprint 7: una cuenta de Promotoría no se desactiva de un solo paso).
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(servicio.desactivar(otra.getId(), "Dejó de trabajar en el colegio")).isTrue();
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitudPendiente("ESTADO_CUENTA", otra), "Confirmado en persona con las dos");
		assertThat(activa(otra)).isFalse();

		Long solicitud = solicitudPendienteDe(promotora);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Confirmado en persona con la promotora"))
				.isInstanceOf(ReglaNegocioException.class);

		assertThat(promotorasActivas()).isEqualTo(1);
		assertThat(roles(promotora)).contains("PROMOTOR");
	}

	@Test
	void nadiePuedeDesactivarNiQuitarlePromotoriaALaUnica() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(directora);
		assertThatThrownBy(() -> servicio.desactivar(promotora.getId(), "Dejó de trabajar en el colegio"))
				.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> servicio.cambiarRoles(promotora.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE),
				MOTIVO))).isInstanceOf(RuntimeException.class);

		assertThat(promotorasActivas()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES'", Long.class))
				.isZero();
	}

	// ------------------------------------------------------------------ excepción de la primera Dirección

	@Test
	void laPrimeraDireccionSeDaSinSolicitudConUnaSolaPromotoriaYNingunaDireccion() {
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);
		ContadorFilasRolH2.reiniciar();

		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).as("sin solicitud: nadie más podría aprobarla").isFalse();

		assertThat(ContadorFilasRolH2.FILAS).containsExactly("ALTA DIRECTOR");
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'ROLES_CAMBIADOS'",
				String.class)).contains("Primer usuario de Dirección");
	}

	@Test
	void conDosPromotoriasLaPrimeraDireccionSePideAunqueNoHayaDireccion() {
		guardar("otra.promotora", Rol.PROMOTOR);
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).as("la otra promotora puede aprobarla").isTrue();

		assertThat(roles(docente)).containsExactly("DOCENTE");
	}

	@Test
	void conUnaDireccionActivaLaSegundaDireccionSePide() {
		guardar("directora", Rol.DIRECTOR);
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).isTrue();

		assertThat(roles(docente)).containsExactly("DOCENTE");
	}

	@Test
	void laExcepcionNoSirveParaDarDireccionYPromotoriaALaVez() {
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR,
				Rol.PROMOTOR), MOTIVO))).isTrue();

		assertThat(roles(docente)).containsExactly("DOCENTE");
	}

	@Test
	void laExcepcionNoSirveParaQuitarPromotoria() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR, Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(otra.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).as("quita Promotoría y da Dirección: se pide").isTrue();

		assertThat(roles(otra)).containsExactlyInAnyOrder("DOCENTE", "PROMOTOR");
	}

	@Test
	void laExcepcionNoDaDireccionACaja() {
		Usuario cajera = guardar("caja", Rol.CAJA);

		assertThatThrownBy(() -> servicio.cambiarRoles(cajera.getId(), new CambiarRolesRequest(EnumSet.of(Rol.CAJA,
				Rol.DIRECTOR), MOTIVO))).isInstanceOf(ReglaNegocioException.class);

		assertThat(roles(cajera)).containsExactly("CAJA");
	}

	/**
	 * Observación de QA (correcciones del sprint 7). La excepción de la primera Dirección es UNA vez por colegio: si el
	 * colegio ya tuvo Dirección (aunque hoy no haya ninguna activa), una Dirección nueva se pide y la aprueba otra persona.
	 */
	@Test
	void laExcepcionDeLaPrimeraDireccionSeUsaUnaSolaVezPorColegio() {
		Usuario primera = guardar("primera.directora", Rol.DOCENTE);
		assertThat(servicio.cambiarRoles(primera.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).as("la primera de la historia: sin solicitud").isFalse();
		assertThat(jdbc.queryForObject("SELECT usuario_id FROM primera_direccion WHERE colegio_id = 1", Long.class))
				.isEqualTo(primera.getId());
		// La primera Dirección deja el colegio: hoy no hay ninguna activa y sigue habiendo una sola Promotoría.
		jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ? AND rol = 'DIRECTOR'", primera.getId());
		Usuario segunda = guardar("segunda.directora", Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(segunda.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				MOTIVO))).as("la excepción ya se usó: se pide").isTrue();

		assertThat(roles(segunda)).containsExactly("DOCENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM primera_direccion", Long.class)).isEqualTo(1L);
	}

	// ------------------------------------------------------------------ desactivar y reactivar Promotoría o Dirección

	/** Observación de QA: desactivar una cuenta de Dirección se pide; la aprueba otra persona y queda enlazada. */
	@Test
	void desactivarUnaDireccionSePideYLaApruebaOtraPersona() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		Usuario otraDirectora = guardar("otra.directora", Rol.DIRECTOR);

		assertThat(servicio.desactivar(directora.getId(), "Dejó de trabajar en el colegio")).isTrue();
		assertThat(activa(directora)).as("hasta que otra persona lo apruebe, sigue activa").isTrue();

		UsuariosDePrueba.iniciarSesion(otraDirectora);
		Long solicitud = solicitudPendiente("ESTADO_CUENTA", directora);
		bandeja.aprobar(solicitud, "Confirmado en persona con la promotora");

		assertThat(activa(directora)).isFalse();
		assertThat(jdbc.queryForObject("SELECT estado_solicitud_id FROM usuario WHERE id = ?", Long.class,
				directora.getId())).isEqualTo(solicitud);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'USUARIO_DESACTIVADO'",
				Long.class)).isEqualTo(1L);
	}

	/** Observación de QA: reactivar una cuenta de Promotoría también se pide (no vuelve a aprobar de un solo paso). */
	@Test
	void reactivarUnaPromotoriaSePide() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR);
		jdbc.update("UPDATE usuario SET activo = FALSE, desactivado_en = CURRENT_TIMESTAMP, desactivado_por = 'promotora' "
				+ "WHERE id = ?", otra.getId());

		assertThat(servicio.reactivar(otra.getId(), "Volvió a trabajar en el colegio")).isTrue();

		assertThat(activa(otra)).isFalse();
		assertThat(jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE tipo = 'ESTADO_CUENTA'", String.class))
				.contains("\"activo\":\"true\"");
	}

	/** Ni quien la pidió ni la titular aprueban la desactivación de su cuenta. */
	@Test
	void niQuienPidioNiLaTitularApruebanElEstado() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		assertThat(servicio.desactivar(directora.getId(), "Dejó de trabajar en el colegio")).isTrue();
		Long solicitud = solicitudPendiente("ESTADO_CUENTA", directora);

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Lo apruebo yo misma")).as("quien la pidió")
				.isInstanceOf(RuntimeException.class);
		UsuariosDePrueba.iniciarSesion(directora);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Lo apruebo yo misma")).as("la titular")
				.isInstanceOf(RuntimeException.class);

		assertThat(activa(directora)).isTrue();
	}

	/** Una cuenta sin Promotoría ni Dirección se desactiva como siempre, de un solo paso. */
	@Test
	void unaCuentaDeCajaSeDesactivaSinSolicitud() {
		Usuario cajera = guardar("caja", Rol.CAJA);

		assertThat(servicio.desactivar(cajera.getId(), "Dejó de trabajar en el colegio")).isFalse();

		assertThat(activa(cajera)).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'ESTADO_CUENTA'", Long.class))
				.isZero();
	}

	// ------------------------------------------------------------------ sesiones del titular

	@Test
	void alAplicarLosRolesSeCierranLasSesionesDelTitular() {
		Usuario directora = guardar("directora", Rol.DIRECTOR);
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);
		SesionAbierta abierta = sesiones.abrir(1L, docente.getId(), "192.0.2.10");
		servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR), MOTIVO));

		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitudPendienteDe(docente), "Confirmado en persona con la promotora");

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario WHERE id = ?", String.class,
				abierta.sesionId())).isEqualTo("CUENTA_CAMBIADA");
	}

	// ------------------------------------------------------------------ apoyo

	private Long solicitudPendienteDe(Usuario usuario) {
		return jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' AND entidad_id = ? "
				+ "AND estado = 'PENDIENTE'", Long.class, usuario.getId());
	}

	private Long solicitudPendiente(String tipo, Usuario usuario) {
		return jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = ? AND entidad_id = ? AND estado = 'PENDIENTE'",
				Long.class, tipo, usuario.getId());
	}

	private boolean activa(Usuario usuario) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class,
				usuario.getId()));
	}

	private List<String> roles(Usuario usuario) {
		return jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ? ORDER BY rol", String.class,
				usuario.getId());
	}

	private long promotorasActivas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = 1 AND u.activo AND r.rol = 'PROMOTOR'", Long.class);
	}

	private Usuario guardar(String nombre, Rol... roles) {
		Set<Rol> pedidos = EnumSet.copyOf(List.of(roles));
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false,
				pedidos.toArray(Rol[]::new));
	}
}
