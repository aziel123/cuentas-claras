package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.sql.SQLException;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 1 (sección 13, filas que dicen «MySQL») contra MySQL 8 real, con los permisos mínimos de 02 y los
 * triggers de 03. Con la clave de cc_app (o de cc_sistema): una asignación docente no nace VIGENTE ni a nombre de quien no
 * es de Promotoría, Dirección o Administración; no pasa a VIGENTE sin SU solicitud aprobada y firmada por otra persona de
 * Dirección o Promotoría, y nunca la aprueba el docente asignado (E4); no se termina sin la firma de Dirección o
 * Promotoría ni como «sistema.academico» desde cc_app (E3); no reescribe quién la aprobó; no cambia de docente, sección ni
 * tipo (1143) ni apunta a la sección de otro colegio (E43); y DOCENTE no se combina con CAJA ni ADMINISTRACION (E46).
 * Corre después de la fase 2 del job mysql ({@code CC_PRUEBA_MYSQL=true}). Usa nombres únicos y no limpia.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint9MySqlTest {

	private static final String INSERTAR = "INSERT INTO asignacion_docente (colegio_id, anio_escolar_id, seccion_id, "
			+ "usuario_id, tipo, estado, pendiente, vigente, tutor_vigente, propuesto_por, resuelto_por, resuelto_en, creado_en, "
			+ "creado_por, actualizado_en) VALUES (?, ?, ?, ?, 'TUTOR', ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static final String A_VIGENTE = "UPDATE asignacion_docente SET estado = 'VIGENTE', pendiente = NULL, "
			+ "vigente = TRUE, tutor_vigente = TRUE, resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private AltaColegio alta;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private EscuelaConDocentesMySql.Escuela escuela(String marca, String celular) {
		return EscuelaConDocentesMySql.crear(sufijo + marca, "20600000056", celular, alta, servicioUsuarios, usuarios,
				codificador, colegios, estructura, jdbc);
	}

	private Long proponer(EscuelaConDocentesMySql.Escuela e, long seccion, Usuario docente) {
		UsuariosDePrueba.iniciarSesion(e.equipo().administracion());
		try {
			return asignaciones.proponer(new ProponerAsignacionRequest(seccion, docente.getId(), TipoAsignacion.TUTOR, null,
					"Tutoría del año aprobada por Dirección"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private Long solicitudDe(Long asignacion) {
		return jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, asignacion);
	}

	private String estado(Long asignacion) {
		return jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, asignacion);
	}

	/** E4: no nace VIGENTE ni la propone un docente; no pasa a VIGENTE sin su solicitud aprobada y firmada. */
	@Test
	void e4AsignacionSinSolicitudFirmadaFalla() {
		EscuelaConDocentesMySql.Escuela e = escuela("a", "987653201");
		long colegio = e.colegioId();
		String admin = e.equipo().administracion().getNombreUsuario();
		String directora = e.equipo().directora().getNombreUsuario();
		// Nace VIGENTE (con cc_app y con cc_sistema): 1644.
		Object[] vigente = { colegio, e.anioId(), e.seccionA(), e.docente().getId(), "VIGENTE", null, true, true, admin,
				directora, java.time.LocalDateTime.now(), admin };
		assertThat(codigoAl(() -> jdbc.update(INSERTAR, vigente))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(INSERTAR, vigente))).isEqualTo(1644);
		// Propuesta a nombre de un docente (no es de Promotoría, Dirección ni Administración): 1644.
		String docente = e.otroDocente().getNombreUsuario();
		assertThat(codigoAl(() -> jdbc.update(INSERTAR, colegio, e.anioId(), e.seccionA(), e.docente().getId(), "PROPUESTA",
				true, null, null, docente, null, null, docente))).isEqualTo(1644);
		// Propuesta de una cuenta que no es DOCENTE (la Caja): 1644.
		Long caja = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND r.rol = 'CAJA'", Long.class, colegio);
		assertThat(codigoAl(() -> jdbc.update(INSERTAR, colegio, e.anioId(), e.seccionA(), caja, "PROPUESTA", true, null,
				null, admin, null, null, admin))).isEqualTo(1644);

		Long id = proponer(e, e.seccionA(), e.docente());
		// A VIGENTE sin la solicitud aprobada (con cc_app y con cc_sistema): 1644.
		assertThat(codigoAl(() -> jdbc.update(A_VIGENTE, directora, id))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(A_VIGENTE, directora, id))).isEqualTo(1644);
		// Con la solicitud APROBADA por la directora pero sin su firma: 1644.
		Long solicitud = solicitudDe(id);
		Integer sinFirma = new TransactionTemplate(transacciones).execute(t -> {
			// La solicitud APROBADA sin firma ya la rechaza trg_solicitud_cambio_resuelta; si pasara, la asignación no.
			Integer codigo = codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
					+ "resuelto_por = ?, resuelto_en = NOW(6) WHERE id = ?", directora, solicitud));
			if (codigo == null) {
				codigo = codigoAl(() -> jdbc.update(A_VIGENTE, directora, id));
			}
			t.setRollbackOnly();
			return codigo;
		});
		assertThat(sinFirma).isEqualTo(1644);
		assertThat(estado(id)).isEqualTo("PROPUESTA");
		// Por el camino legítimo (la bandeja, con la firma de la directora) sí pasa.
		UsuariosDePrueba.iniciarSesion(e.equipo().directora());
		bandeja.aprobar(solicitud, null);
		SecurityContextHolder.clearContext();
		assertThat(estado(id)).isEqualTo("VIGENTE");
		// Una vez VIGENTE: no se reescribe quién la aprobó (1644) ni vuelve a PROPUESTA (1644).
		assertThat(codigoAl(() -> jdbc.update("UPDATE asignacion_docente SET resuelto_por = ? WHERE id = ?", admin, id)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE asignacion_docente SET estado = 'PROPUESTA', pendiente = TRUE, "
				+ "vigente = NULL, tutor_vigente = NULL, resuelto_por = NULL, resuelto_en = NULL WHERE id = ?", id)))
				.isEqualTo(1644);
	}

	/** E4: un director que también enseña no aprueba su propia asignación, aunque la firme con su sesión. */
	@Test
	void e4ElDocenteNoApruebaLaSuya() {
		EscuelaConDocentesMySql.Escuela e = escuela("b", "987653211");
		// El docente pasa a tener también Dirección (por el camino legítimo: la pide la Promotoría y la aprueba otra persona).
		UsuariosDePrueba.iniciarSesion(e.equipo().promotora());
		servicioUsuarios.cambiarRoles(e.docente().getId(), new CambiarRolesRequest(EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR),
				"Asume la dirección y sigue enseñando"));
		Long roles = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' AND entidad_id = ?",
				Long.class, e.docente().getId());
		UsuariosDePrueba.iniciarSesion(e.equipo().directora());
		bandeja.aprobar(roles, "Confirmado con la promotora");
		SecurityContextHolder.clearContext();
		Usuario directorDocente = pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(e.colegioId(),
				() -> usuarios.findById(e.docente().getId()).orElseThrow());
		assertThat(directorDocente.getRoles()).contains(Rol.DIRECTOR, Rol.DOCENTE);

		Long id = proponer(e, e.seccionA(), directorDocente);
		Long solicitud = solicitudDe(id);
		// La aplicación no lo deja (participante); la base tampoco: firma y aprueba él mismo, y la asignación no pasa.
		UsuariosDePrueba.iniciarSesion(directorDocente);
		tokens.actual();
		Integer propia = new TransactionTemplate(transacciones).execute(t -> {
			UsuariosDePrueba.iniciarSesion(directorDocente);
			firma.firmar(ClaveFirma.solicitud(solicitud, EstadoSolicitud.APROBADA));
			jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, "
					+ "resuelto_en = NOW(6) WHERE id = ?", directorDocente.getNombreUsuario(), solicitud);
			Integer codigo = codigoAl(() -> jdbc.update(A_VIGENTE, directorDocente.getNombreUsuario(), id));
			t.setRollbackOnly();
			return codigo;
		});
		SecurityContextHolder.clearContext();
		assertThat(propia).isEqualTo(1644);
		assertThat(estado(id)).isEqualTo("PROPUESTA");
	}

	/** E3: terminar exige la firma de Dirección o Promotoría; «sistema.academico» solo con cc_sistema. */
	@Test
	void e3TerminarSinFirmaFalla() {
		EscuelaConDocentesMySql.Escuela e = escuela("c", "987653221");
		Long id = proponer(e, e.seccionA(), e.docente());
		UsuariosDePrueba.iniciarSesion(e.equipo().directora());
		bandeja.aprobar(solicitudDe(id), null);
		SecurityContextHolder.clearContext();
		String terminar = "UPDATE asignacion_docente SET estado = 'TERMINADA', vigente = NULL, tutor_vigente = NULL, "
				+ "terminada_por = ?, terminada_en = NOW(6), motivo_fin = 'Prueba de terminar sin firma' WHERE id = ?";
		assertThat(codigoAl(() -> jdbc.update(terminar, e.equipo().promotora().getNombreUsuario(), id))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(terminar, "sistema.academico", id))).isEqualTo(1644);
		// La Administración (aunque firmara) no termina.
		assertThat(codigoAl(() -> jdbc.update(terminar, e.equipo().administracion().getNombreUsuario(), id)))
				.isEqualTo(1644);
		assertThat(estado(id)).isEqualTo("VIGENTE");
		// sistema.academico con cc_sistema sí (es el camino del proceso).
		sistema().update(terminar, "sistema.academico", id);
		assertThat(estado(id)).isEqualTo("TERMINADA");
	}

	/** E43: la asignación no apunta a la sección de otro colegio (FK compuesta: 1452); 1143 y 1142 en lo inmutable. */
	@Test
	void e43ReferenciasCruzadasYColumnasInmutables() {
		EscuelaConDocentesMySql.Escuela e = escuela("d", "987653231");
		String admin = e.equipo().administracion().getNombreUsuario();
		Long seccionDelColegio1 = jdbc.queryForObject("SELECT MIN(id) FROM seccion WHERE colegio_id = 1", Long.class);
		assertThat(seccionDelColegio1).isNotNull();
		assertThat(codigoAl(() -> jdbc.update(INSERTAR, e.colegioId(), e.anioId(), seccionDelColegio1, e.docente().getId(),
				"PROPUESTA", true, null, null, admin, null, null, admin))).isEqualTo(1452);
		for (String columna : new String[] { "usuario_id", "seccion_id", "anio_escolar_id", "tipo", "areas",
				"propuesto_por" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE asignacion_docente SET " + columna + " = " + columna
					+ " WHERE 1 = 0"))).as(columna).isEqualTo(1143);
		}
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM asignacion_docente WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM asignacion_docente WHERE 1 = 0"))).isEqualTo(1142);
	}

	/** E46: DOCENTE no se combina con CAJA ni con ADMINISTRACION (trg_usuario_rol_alta, también con cc_sistema). */
	@Test
	void e46DocenteConCajaFalla() {
		EscuelaConDocentesMySql.Escuela e = escuela("e", "987653241");
		Long caja = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND r.rol = 'CAJA'", Long.class, e.colegioId());
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'DOCENTE')", caja)))
				.isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'CAJA')",
				e.docente().getId()))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'ADMINISTRACION')",
				e.docente().getId()))).isEqualTo(1644);
		assertThat(mensajeDe(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'CAJA')",
				e.docente().getId()))).contains("el docente no ve deudas");
		assertThat(jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ?", String.class, e.docente().getId()))
				.containsExactly("DOCENTE");
		// Con la aplicación tampoco (ReglasSegregacion): la Promotoría no le da Caja.
		UsuariosDePrueba.iniciarSesion(e.equipo().promotora());
		assertThat(mensajeDeApp(() -> servicioUsuarios.cambiarRoles(e.docente().getId(), new CambiarRolesRequest(EnumSet.of(
				Rol.DOCENTE, Rol.CAJA), "Ayuda en caja por las tardes")))).contains("el docente no ve deudas");
		SecurityContextHolder.clearContext();
	}

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			throw e;
		}
	}

	private static String mensajeDe(Runnable sentencia) {
		try {
			sentencia.run();
			return "(sin error)";
		}
		catch (DataAccessException e) {
			return String.valueOf(e.getMostSpecificCause().getMessage());
		}
	}

	private static String mensajeDeApp(Runnable accion) {
		try {
			accion.run();
			return "(sin error)";
		}
		catch (RuntimeException e) {
			return String.valueOf(e.getMessage());
		}
	}
}
