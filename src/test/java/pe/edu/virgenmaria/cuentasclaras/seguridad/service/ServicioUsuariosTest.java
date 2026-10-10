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
	private pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private SessionRegistry registroSesiones;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private java.time.Clock reloj;

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

	/**
	 * Sprint 7, tanda 2 (decisión 84, H1): dar Dirección se pide; no lo aprueba quien lo pidió ni el titular (otra
	 * persona sí). Mientras tanto, los roles no cambian.
	 */
	@Test
	void darDireccionLoApruebaOtraPersonaNiQuienPidioNiElTitular() {
		Usuario docente = guardar("docente.nuevo", Rol.DOCENTE);

		assertThat(servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				"Asume la dirección del nivel secundaria"))).isTrue();
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, docente.getId());
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, docente.getId()))
				.containsExactly("DOCENTE");

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Me apruebo lo que pedí")).as("quien pidió")
				.isInstanceOf(RuntimeException.class);
		UsuariosDePrueba.iniciarSesion(docente);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Me doy Dirección")).as("el titular")
				.isInstanceOf(RuntimeException.class);
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, docente.getId()))
				.containsExactly("DOCENTE");

		UsuariosDePrueba.iniciarSesion(director);
		bandeja.aprobar(solicitud, "Confirmado con la promotora");

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ? ORDER BY rol", String.class,
				docente.getId())).containsExactly("DIRECTOR", "DOCENTE");
		assertThat(accionesAuditadas()).contains("ROLES_CAMBIADOS");
		// La firma de la aprobación quedó a nombre de quien aprobó, con su sesión.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ? AND usuario_id = ?",
				Long.class, "solicitud_cambio:" + solicitud + ":APROBADA", director.getId())).isEqualTo(1);
	}

	/** Sprint 7, tanda 2: un rol del personal (sin Promotoría ni Dirección) se aplica directo, sin solicitud. */
	@Test
	void losRolesDelPersonalSeAplicanSinSolicitud() {
		assertThat(servicio.cambiarRoles(caja.getId(), new CambiarRolesRequest(EnumSet.of(Rol.CAJA, Rol.DOCENTE),
				"También dicta talleres por las tardes"))).isFalse();

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ? ORDER BY rol", String.class,
				caja.getId())).containsExactly("CAJA", "DOCENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES'", Long.class))
				.isZero();
	}

	@Test
	void crearNoDevuelveClaveYEnviaElEnlaceAlTitular() {
		UsuarioCreado creado = servicio.crear(solicitud("Lucia.Ramos", Rol.CAJA));

		assertThat(creado.nombreUsuario()).isEqualTo("lucia.ramos");
		assertThat(creado.enviadoA()).isEqualTo("Enlace enviado a WhatsApp +51 *** *** "
				+ UsuariosDePrueba.celular("lucia.ramos").substring(9));
		Map<String, Object> fila = fila(creado.id());
		assertThat(fila.get("debe_cambiar_clave")).isEqualTo(true);
		assertThat(fila.get("colegio_id")).isEqualTo(1L);
		assertThat(fila.get("telefono_whatsapp")).isEqualTo(UsuariosDePrueba.celular("lucia.ramos"));
		assertThat(accionesAuditadas()).contains("USUARIO_CREADO");
		// El mensaje de activación nace PENDIENTE y sin token: el enlace se genera al enviarlo.
		assertThat(jdbc.queryForMap("SELECT tipo, canal, destinatario_tipo, destino, estado, parametros FROM mensaje "
				+ "WHERE usuario_id = ?", creado.id())).containsEntry("tipo", "ACTIVACION_CUENTA")
				.containsEntry("canal", "WHATSAPP").containsEntry("destinatario_tipo", "USUARIO")
				.containsEntry("destino", UsuariosDePrueba.celular("lucia.ramos")).containsEntry("estado", "PENDIENTE")
				.containsEntry("parametros", "");
	}

	@Test
	void laClaveAlAzarNoSeConoceYYaVencio() {
		UsuarioCreado creado = servicio.crear(solicitud("lucia.ramos", Rol.CAJA));

		Map<String, Object> fila = fila(creado.id());
		assertThat((String) fila.get("clave_hash")).startsWith("{bcrypt}");
		assertThat(((java.sql.Timestamp) fila.get("clave_temporal_hasta")).toLocalDateTime()).isBeforeOrEqualTo(
				java.time.LocalDateTime.now(reloj));
		assertThat(creado.toString()).doesNotContain("/activar/");
		assertThat(todaLaBitacora()).doesNotContain("/activar/");
	}

	@Test
	void personalSinCelularNiCorreoNoSeCrea() {
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Sin Contacto", "sin.contacto", null, null,
				Set.of(Rol.CAJA)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("celular");
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Mal Celular", "mal.celular", null, "12345",
				Set.of(Rol.CAJA)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("celular");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario IN ('sin.contacto', "
				+ "'mal.celular')", Long.class)).isZero();
	}

	@Test
	void noSePuedeCrearUnUsuarioConMiPropioCelular() {
		String mio = UsuariosDePrueba.celular("promotora").substring(3);
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Cuenta Fantasma", "cuenta.fantasma", null, mio,
				Set.of(Rol.CAJA)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("es tuyo");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje", Long.class)).isZero();
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

		assertThat(servicio.rolesAsignables()).doesNotContain(Rol.PROMOTOR, Rol.DIRECTOR).contains(Rol.DOCENTE);
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

	/**
	 * Auditoría A5 (sprint 2): Dirección se fabricaba una segunda cuenta de Administración (o le restablecía la clave
	 * a una existente) y con ella cumplía el doble control sola. Ahora solo Promotoría da acceso a esas cuentas.
	 */
	@Test
	void direccionNoCreaNiRestableceNiCambiaRolesDeAdministracionNiCaja() {
		Usuario administracion = guardar("ana.administracion", Rol.ADMINISTRACION);
		Usuario docente = guardar("luis.docente", Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(director);

		// Sprint 4: APODERADO no se asigna desde Usuarios; su acceso se da desde la ficha del apoderado (enlazado).
		assertThat(servicio.rolesAsignables()).doesNotContain(Rol.ADMINISTRACION, Rol.CAJA, Rol.APODERADO)
				.contains(Rol.DOCENTE);
		assertThatThrownBy(() -> servicio.crear(solicitud("segunda.cuenta", Rol.ADMINISTRACION)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.crear(solicitud("cajera.fantasma", Rol.CAJA)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.restablecerClave(administracion.getId(), MOTIVO))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.restablecerClave(caja.getId(), MOTIVO))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.cambiarRoles(docente.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.ADMINISTRACION), MOTIVO))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.cambiarRoles(caja.getId(),
				new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO))).isInstanceOf(AccessDeniedException.class);
		assertThat(servicio.obtener(caja.getId()).puedeDarAcceso()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion IN ('USUARIO_CREADO', "
				+ "'CLAVE_RESTABLECIDA', 'ROLES_CAMBIADOS')", Long.class)).isZero();

		// Sí puede dar acceso a Docentes, y desactivar una cuenta de Caja (quita acceso, no lo da).
		assertThat(servicio.restablecerClave(docente.getId(), MOTIVO).enviadoA()).startsWith("Enlace enviado a");
		servicio.desactivar(caja.getId(), MOTIVO);

		// Promotoría sí.
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(servicio.obtener(administracion.getId()).puedeDarAcceso()).isTrue();
		assertThat(servicio.restablecerClave(administracion.getId(), MOTIVO).enviadoA()).startsWith("Enlace enviado a");
	}

	@Test
	void cajaNoPuedeUsarLaGestionDeUsuarios() {
		UsuariosDePrueba.iniciarSesion(caja);
		assertThatThrownBy(() -> servicio.listar()).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> servicio.crear(solicitud("intruso", Rol.CAJA))).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void restablecerEnviaUnEnlaceNuevoYDesbloquea() {
		bloquear(caja);
		assertThat(fila(caja.getId()).get("bloqueado_hasta")).as("estaba bloqueada").isNotNull();
		String hashAntes = (String) fila(caja.getId()).get("clave_hash");

		UsuarioCreado nueva = servicio.restablecerClave(caja.getId(), "Olvidó su clave y la bloqueó");

		Map<String, Object> fila = fila(caja.getId());
		assertThat(fila.get("clave_hash")).as("una clave al azar que nadie conoce").isNotEqualTo(hashAntes);
		assertThat(nueva.enviadoA()).doesNotContain("/activar/");
		assertThat(fila).containsEntry("debe_cambiar_clave", true).containsEntry("intentos_fallidos", 0);
		assertThat(fila.get("bloqueado_hasta")).isNull();
		assertThat(ultimoEvento("CLAVE_RESTABLECIDA").get("valor_nuevo")).isEqualTo("enlace nuevo de un solo uso al titular");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND tipo = 'ACTIVACION_CUENTA'",
				Long.class, caja.getId())).isEqualTo(1);
		assertThat(fila).containsEntry("clave_restablecida_por", "promotora");
		assertThat(fila.get("clave_temporal_hasta")).as("la clave temporal vence").isNotNull();
	}

	@Test
	void restablecerClaveCierraLasSesionesDelUsuario() {
		conSesionRegistrada(caja, "sesion-restablecer", () -> servicio.restablecerClave(caja.getId(), MOTIVO));
	}

	@Test
	void cambiarRolesCierraLasSesionesDelUsuario() {
		conSesionRegistrada(caja, "sesion-roles",
				() -> servicio.cambiarRoles(caja.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE), MOTIVO)));
	}

	@Test
	void direccionNoPuedeRestablecerLaClaveDeUnaPromotora() {
		UsuariosDePrueba.iniciarSesion(director);
		String hashAntes = (String) fila(promotora.getId()).get("clave_hash");

		assertThatThrownBy(() -> servicio.restablecerClave(promotora.getId(), MOTIVO))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(fila(promotora.getId()).get("clave_hash")).isEqualTo(hashAntes);
	}

	@Test
	void direccionNoPuedeReactivarAUnaPromotoraDesactivada() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR);
		// Correcciones del sprint 7: una Promotoría se desactiva con una solicitud aprobada; aquí ya está desactivada.
		jdbc.update("UPDATE usuario SET activo = FALSE, desactivado_en = CURRENT_TIMESTAMP, desactivado_por = 'promotora' "
				+ "WHERE id = ?", otra.getId());
		UsuariosDePrueba.iniciarSesion(director);

		assertThatThrownBy(() -> servicio.reactivar(otra.getId(), MOTIVO)).isInstanceOf(AccessDeniedException.class);
		assertThat(fila(otra.getId())).containsEntry("activo", false);
	}

	@Test
	void direccionNoPuedeDesbloquearAUnaPromotora() {
		bloquear(promotora);
		UsuariosDePrueba.iniciarSesion(director);

		assertThatThrownBy(() -> servicio.desbloquear(promotora.getId(), MOTIVO)).isInstanceOf(AccessDeniedException.class);
		assertThat(fila(promotora.getId()).get("bloqueado_hasta")).isNotNull();
	}

	@Test
	void elUltimoPromotorSeCuentaPorColegio() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT id FROM colegio WHERE nombre = 'Colegio de Prueba B'", Long.class);
		Usuario promotoraB1 = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b1",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		Usuario promotoraB2 = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b2",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);

		// Colegio A tiene una sola promotora: las dos de B no cuentan.
		UsuariosDePrueba.iniciarSesion(director);
		assertThatThrownBy(() -> servicio.desactivar(promotora.getId(), MOTIVO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("último usuario de Promotoría");

		// Colegio B tiene dos: una pide desactivar a la otra (correcciones del sprint 7: lo aprueba otra persona, aquí la
		// directora de B); la que queda ya es la última.
		UsuariosDePrueba.iniciarSesion(promotoraB1);
		assertThat(servicio.desactivar(promotoraB2.getId(), MOTIVO)).isTrue();
		Usuario directoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "directora.b", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(directoraB);
		bandeja.aprobar(jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'ESTADO_CUENTA' AND entidad_id = ?",
				Long.class, promotoraB2.getId()), "Confirmado en persona con las dos promotoras");
		assertThat(fila(promotoraB2.getId())).containsEntry("activo", false);
		UsuariosDePrueba.iniciarSesion(promotoraB2);
		assertThatThrownBy(() -> servicio.desactivar(promotoraB1.getId(), MOTIVO))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void conDosPromotorasActivasSePuedeQuitarPromotoriaAUna() {
		Usuario otra = guardar("otra.promotora", Rol.PROMOTOR);

		// Sprint 7, tanda 2 (decisión 84): quitar Promotoría y dar Dirección se PIDE; lo aprueba otra persona.
		assertThat(servicio.cambiarRoles(otra.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DIRECTOR), MOTIVO))).isTrue();
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, otra.getId()))
				.containsExactly("PROMOTOR");
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' "
				+ "AND entidad_id = ? AND estado = 'PENDIENTE'", Long.class, otra.getId());

		UsuariosDePrueba.iniciarSesion(director);
		bandeja.aprobar(solicitud, "Confirmado en persona");

		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, otra.getId()))
				.containsExactly("DIRECTOR");
		assertThat(jdbc.queryForObject("SELECT roles_solicitud_id FROM usuario WHERE id = ?", Long.class, otra.getId()))
				.isEqualTo(solicitud);
	}

	@Test
	void rolesVaciosONulosDanUnMensajeClaro() {
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Sin Roles", "sin.roles", null, "987000001", Set.of())))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un rol");
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Sin Roles", "sin.roles", null, "987000001", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un rol");
		assertThatThrownBy(() -> servicio.cambiarRoles(caja.getId(), new CambiarRolesRequest(Set.of(), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un rol");
		assertThatThrownBy(() -> servicio.cambiarRoles(caja.getId(), new CambiarRolesRequest(null, MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un rol");
	}

	@Test
	void laCreacionSimultaneaDelMismoNombreDaUnMensajeClaro() throws Exception {
		int hilos = 6;
		java.util.concurrent.ExecutorService ejecutor = java.util.concurrent.Executors.newFixedThreadPool(hilos);
		java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
		java.util.List<java.util.concurrent.Future<String>> resultados = new java.util.ArrayList<>();
		try {
			for (int i = 0; i < hilos; i++) {
				resultados.add(ejecutor.submit(() -> {
					UsuariosDePrueba.iniciarSesion(promotora);
					largada.await();
					try {
						servicio.crear(solicitud("mismo.nombre", Rol.DOCENTE));
						return "creado";
					}
					catch (ReglaNegocioException e) {
						return e.getMessage();
					}
					finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			largada.countDown();
			java.util.List<String> textos = new java.util.ArrayList<>();
			for (var resultado : resultados) {
				textos.add(resultado.get(60, java.util.concurrent.TimeUnit.SECONDS));
			}
			assertThat(textos).filteredOn("creado"::equals).hasSize(1);
			assertThat(textos).filteredOn(t -> !"creado".equals(t))
					.hasSize(hilos - 1).allSatisfy(t -> assertThat(t).contains("Ya existe un usuario «mismo.nombre»"));
		}
		finally {
			ejecutor.shutdownNow();
		}
	}

	private void conSesionRegistrada(Usuario usuario, String idSesion, Runnable accion) {
		registroSesiones.registerNewSession(idSesion, UsuariosDePrueba.autenticado(usuario));
		try {
			accion.run();
			assertThat(registroSesiones.getSessionInformation(idSesion).isExpired()).isTrue();
		}
		finally {
			registroSesiones.removeSessionInformation(idSesion);
		}
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
		return new CrearUsuarioRequest("Nombre de " + nombreUsuario, nombreUsuario, null,
				UsuariosDePrueba.celular(nombreUsuario.toLowerCase(java.util.Locale.ROOT)).substring(3), Set.of(roles));
	}

	private void bloquear(Usuario usuario) {
		jdbc.update("UPDATE usuario SET bloqueado_hasta = ?, intentos_fallidos = 0 WHERE id = ?",
				java.time.LocalDateTime.now(reloj).plusHours(1), usuario.getId());
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
