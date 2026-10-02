package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de la gestión de usuarios sobre la base real, con la seguridad por método activa.
 */
@PruebaIntegracion
class ServicioUsuariosTest {

	private static final String MOTIVO = "Dejó de trabajar en el colegio";

	@Autowired
	private ServicioUsuarios servicio;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private SessionRegistry registroSesiones;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	private Usuario director;

	private Usuario caja;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		promotora = guardar("promotora", Rol.PROMOTOR);
		director = guardar("director", Rol.DIRECTOR);
		caja = guardar("caja", Rol.CAJA);
		UsuariosDePrueba.iniciarSesion(promotora);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void crearGeneraClaveTemporalQueDebeCambiarse() {
		UsuarioCreado creado = servicio.crear(solicitud("Lucia.Ramos", Rol.CAJA));

		assertThat(creado.nombreUsuario()).isEqualTo("lucia.ramos");
		assertThat(creado.claveTemporal()).hasSize(12);
		Map<String, Object> fila = fila(creado.id());
		assertThat(fila.get("debe_cambiar_clave")).isEqualTo(true);
		assertThat(fila.get("colegio_id")).isEqualTo(1L);
		assertThat(codificador.matches(creado.claveTemporal(), (String) fila.get("clave_hash"))).isTrue();
		assertThat(accionesAuditadas()).contains("USUARIO_CREADO");
	}

	@Test
	void laClaveTemporalNoSeGuardaEnTextoPlano() {
		UsuarioCreado creado = servicio.crear(solicitud("lucia.ramos", Rol.CAJA));

		assertThat((String) fila(creado.id()).get("clave_hash")).startsWith("{bcrypt}")
				.doesNotContain(creado.claveTemporal());
		assertThat(creado.toString()).doesNotContain(creado.claveTemporal());
		assertThat(todaLaBitacora()).doesNotContain(creado.claveTemporal());
	}

	@Test
	void nadieCambiaSusPropiosRoles() {
		assertThatThrownBy(() -> servicio.cambiarRoles(promotora.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("tus propios roles");
	}

	@Test
	void nadieSeDesactivaASiMismo() {
		assertThatThrownBy(() -> servicio.desactivar(promotora.getId(), MOTIVO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("tu propio usuario");
	}

	@Test
	void noSeDesactivaAlUltimoPromotor() {
		UsuariosDePrueba.iniciarSesion(director);

		assertThatThrownBy(() -> servicio.desactivar(promotora.getId(), MOTIVO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("último usuario de Promotoría");
		assertThatThrownBy(() -> servicio.cambiarRoles(promotora.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.DIRECTOR), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("último usuario de Promotoría");
		assertThat(fila(promotora.getId()).get("activo")).isEqualTo(true);
	}

	@Test
	void desactivarExigeMotivoYAuditaAnteriorYNuevo() {
		assertThatThrownBy(() -> servicio.desactivar(caja.getId(), "corto"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");
		assertThat(fila(caja.getId()).get("activo")).isEqualTo(true);

		servicio.desactivar(caja.getId(), MOTIVO);

		assertThat(fila(caja.getId())).containsEntry("activo", false).containsEntry("desactivado_por", "promotora");
		Map<String, Object> evento = ultimoEvento("USUARIO_DESACTIVADO");
		assertThat(evento).containsEntry("valor_anterior", "activo").containsEntry("valor_nuevo", "inactivo")
				.containsEntry("nombre_usuario", "promotora").containsEntry("entidad_id", caja.getId().toString());
		assertThat((String) evento.get("detalle")).contains("caja").contains(MOTIVO);
	}

	@Test
	void desactivarCierraLasSesionesDelUsuario() {
		registroSesiones.registerNewSession("sesion-de-caja", UsuariosDePrueba.autenticado(caja));
		try {
			servicio.desactivar(caja.getId(), MOTIVO);

			assertThat(registroSesiones.getSessionInformation("sesion-de-caja").isExpired()).isTrue();
		}
		finally {
			registroSesiones.removeSessionInformation("sesion-de-caja");
		}
	}

	@Test
	void cambiarRolesAuditaRolesAnterioresYNuevos() {
		servicio.cambiarRoles(caja.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), "Pasó a dictar clases"));

		assertThat(ultimoEvento("ROLES_CAMBIADOS")).containsEntry("valor_anterior", "CAJA")
				.containsEntry("valor_nuevo", "DOCENTE");
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, caja.getId()))
				.containsExactly("DOCENTE");
	}

	@Test
	void nombreDuplicadoEntreColegiosMuestraMensajeClaro() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = 'Colegio de Prueba B'", Long.class);
		UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "lucia", UsuariosDePrueba.CLAVE, false, Rol.CAJA);

		assertThatThrownBy(() -> servicio.crear(solicitud("Lucia", Rol.CAJA)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Ya existe un usuario «lucia»");
	}

	@Test
	void elNombreDeUsuarioDebeTenerUnFormatoValido() {
		assertThatThrownBy(() -> servicio.crear(solicitud("lucía ramos", Rol.CAJA)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("minúsculas sin tildes");
	}

	@Test
	void seAplicaLaSegregacionDeRoles() {
		assertThatThrownBy(() -> servicio.crear(solicitud("cajera.admin", Rol.CAJA, Rol.ADMINISTRACION)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("quien cobra no aprueba");
		assertThatThrownBy(() -> servicio.cambiarRoles(caja.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.CAJA, Rol.DIRECTOR), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void direccionNoTocaNiAsignaPromotoriaNiDireccion() {
		Usuario otraPromotora = guardar("otra.promotora", Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(director);

		assertThat(servicio.rolesAsignables()).doesNotContain(Rol.PROMOTOR, Rol.DIRECTOR).contains(Rol.CAJA);
		assertThatThrownBy(() -> servicio.crear(solicitud("nuevo.director", Rol.DIRECTOR)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.desactivar(otraPromotora.getId(), MOTIVO))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.cambiarRoles(caja.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.PROMOTOR), MOTIVO)))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(servicio.obtener(otraPromotora.getId()).puedeGestionar()).isFalse();
		assertThat(servicio.obtener(caja.getId()).puedeGestionar()).isTrue();
	}

	@Test
	void cajaNoPuedeUsarLaGestionDeUsuarios() {
		UsuariosDePrueba.iniciarSesion(caja);
		assertThatThrownBy(() -> servicio.listar()).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.crear(solicitud("intruso", Rol.CAJA))).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void restablecerClaveDaUnaTemporalNuevaYDesbloquea() {
		bloquear(caja);

		UsuarioCreado nueva = servicio.restablecerClave(caja.getId(), "Olvidó su clave y la bloqueó");

		Map<String, Object> fila = fila(caja.getId());
		assertThat(codificador.matches(nueva.claveTemporal(), (String) fila.get("clave_hash"))).isTrue();
		assertThat(fila).containsEntry("debe_cambiar_clave", true).containsEntry("intentos_fallidos", 0);
		assertThat(fila.get("bloqueado_hasta")).isNull();
		assertThat(ultimoEvento("CLAVE_RESTABLECIDA").get("valor_nuevo")).isEqualTo("clave temporal");
		assertThat(todaLaBitacora()).doesNotContain(nueva.claveTemporal());
	}

	@Test
	void desbloquearSoloAplicaACuentasBloqueadas() {
		assertThatThrownBy(() -> servicio.desbloquear(caja.getId(), MOTIVO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está bloqueada");

		bloquear(caja);
		servicio.desbloquear(caja.getId(), "Confirmé su identidad por teléfono");

		assertThat(fila(caja.getId()).get("bloqueado_hasta")).isNull();
		assertThat(ultimoEvento("CUENTA_DESBLOQUEADA").get("valor_nuevo")).isEqualTo("desbloqueada");
	}

	@Test
	void reactivarDevuelveElAcceso() {
		servicio.desactivar(caja.getId(), MOTIVO);
		servicio.reactivar(caja.getId(), "Volvió a trabajar en el colegio");

		assertThat(fila(caja.getId())).containsEntry("activo", true);
		assertThat(fila(caja.getId()).get("desactivado_por")).isNull();
		assertThat(ultimoEvento("USUARIO_REACTIVADO")).containsEntry("valor_anterior", "inactivo")
				.containsEntry("valor_nuevo", "activo");
	}

	@Test
	void unUsuarioDeOtroColegioNoExiste() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = 'Colegio de Prueba B'", Long.class);
		Usuario deB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "caja.b", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);

		assertThatThrownBy(() -> servicio.obtener(deB.getId())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.desactivar(deB.getId(), MOTIVO))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(servicio.listar()).extracting(u -> u.nombreUsuario()).doesNotContain("caja.b");
	}

	private Usuario guardar(String nombre, Rol... roles) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, roles);
	}

	private static CrearUsuarioRequest solicitud(String nombreUsuario, Rol... roles) {
		return new CrearUsuarioRequest("Nombre de " + nombreUsuario, nombreUsuario, null, Set.of(roles));
	}

	private void bloquear(Usuario usuario) {
		jdbc.update("UPDATE usuario SET bloqueado_hasta = ?, intentos_fallidos = 0 WHERE id = ?",
				java.time.LocalDateTime.now().plusHours(1), usuario.getId());
	}

	private Map<String, Object> fila(Long id) {
		return jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", id);
	}

	private Map<String, Object> ultimoEvento(String accion) {
		return jdbc.queryForMap("SELECT * FROM evento_auditoria WHERE accion = ? ORDER BY secuencia DESC LIMIT 1",
				accion);
	}

	private java.util.List<String> accionesAuditadas() {
		return jdbc.queryForList("SELECT accion FROM evento_auditoria", String.class);
	}

	private String todaLaBitacora() {
		return jdbc.queryForList("SELECT * FROM evento_auditoria").stream()
				.flatMap(f -> f.values().stream()).map(String::valueOf).collect(Collectors.joining("\n"));
	}
}
