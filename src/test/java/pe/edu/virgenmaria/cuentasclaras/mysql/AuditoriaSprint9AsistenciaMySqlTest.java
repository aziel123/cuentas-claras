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
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.portal.JustificarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsistenciaDireccion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConfiguracionesAcademicas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.JustificacionesFamilia;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ServicioAsistencia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprint 9, tanda 2 (sección 13.1, filas que dicen «MySQL»; brief de la tanda 2, sección 8) contra MySQL 8 real, con los
 * permisos mínimos de 02 y los triggers de 03. Con la clave de {@code cc_app}: la asistencia no se registra otro día ni
 * por quien no es docente de la sección, sin sesión ni firma, ni con la fuente MARCADOR; las marcas no se editan ni se
 * borran (1142) ni se bifurcan (1062); el personal no crea una falta justificada (3819) ni justifica «como la familia»;
 * la corrección del día cierra a la hora del segundo aviso; lo revertido no se rejustifica; la configuración la cambia
 * solo Dirección con su firma; y los avisos de asistencia no hablan de cobranza ni salen por WhatsApp sin el «sí».
 * Las pruebas que registran «hoy» se omiten en fin de semana o feriado (el día lo decide la base). Usa colegios nuevos.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint9AsistenciaMySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final String MOTIVO = "Corrección con el registro en papel de la tutora";

	private static final String REGISTRO = "INSERT INTO registro_asistencia (colegio_id, anio_escolar_id, seccion_id, fecha, "
			+ "origen, fuente, registrado_por, sesion_id, alumnos, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, "
			+ "'DEL_DIA', ?, ?, ?, 1, NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAsistencia asistencia;

	@Autowired
	private AsistenciaDireccion direccion;

	@Autowired
	private JustificacionesFamilia justificaciones;

	@Autowired
	private ConfiguracionesAcademicas configuraciones;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private TokenDeSesion tokens;

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

	/** El escenario: un colegio nuevo con 3.° A, su tutor(a) aprobado por la bandeja, dos alumnos y el año que cubre hoy. */
	private record Clase(EscuelaConDocentesMySql.Escuela escuela, long seccion, Long alumno, Long otroAlumno,
			Long apoderado, Long matricula) {

		long colegio() {
			return escuela.colegioId();
		}
	}

	/**
	 * UN colegio para toda la clase (la instalación del CI admite 25 colegios y las demás pruebas de MySQL ya crean
	 * varios): cada prueba usa una sección propia de ese colegio, con su tutor(a) y sus dos alumnos.
	 */
	private static EscuelaConDocentesMySql.Escuela compartida;

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private LocalDate hoyEnLaBase() {
		return jdbc.queryForObject("SELECT DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR)", LocalDate.class);
	}

	/** Las pruebas que registran «hoy» necesitan un día de clases (lunes a viernes, sin feriado nacional). */
	private void soloEnDiaDeClases() {
		LocalDate hoy = hoyEnLaBase();
		assumeTrue(hoy.getDayOfWeek() != DayOfWeek.SATURDAY && hoy.getDayOfWeek() != DayOfWeek.SUNDAY
				&& !FeriadosNacionales.es(hoy), "hoy no hay clases (fin de semana o feriado): se omite");
	}

	private Clase clase(String marca, String celular) {
		if (compartida == null) {
			compartida = EscuelaConDocentesMySql.crear(sufijo + "asis", "20600000056", celular, alta, servicioUsuarios,
					usuarios, codificador, colegios, estructura, jdbc);
			LocalDate hoy = hoyEnLaBase();
			// El año del alta (2027) pasa a cubrir hoy (cc_app tiene UPDATE de anio_escolar; sin trigger).
			jdbc.update("UPDATE anio_escolar SET inicio_clases = ?, fin_clases = ? WHERE id = ?", hoy.minusDays(40),
					hoy.plusDays(40), compartida.anioId());
		}
		EscuelaConDocentesMySql.Escuela e = compartida;
		long seccion = como(e.equipo().administracion(), () -> estructura.crearSeccion(e.anioId(),
				new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest(
						pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado.PRIMARIA_5, "T" + marca + numero(4))));
		UsuariosDePrueba.iniciarSesion(e.equipo().administracion());
		Long alumno = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71" + numero(6), "Quispe", "Rojas", "Mateo",
				LocalDate.of(2016, 3, 3), "45" + numero(6), "Rojas", "Paz", "Rosa", null, "rosa." + sufijo + marca
						+ "@correo.pe", seccion)).alumnoId();
		Long otro = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("72" + numero(6), "Flores", "Díaz", "Lucas",
				LocalDate.of(2016, 4, 4), "46" + numero(6), "Flores", "Luna", "Pedro", null, "pedro." + sufijo + marca
						+ "@correo.pe", seccion)).alumnoId();
		SecurityContextHolder.clearContext();
		Long id = como(e.equipo().administracion(), () -> asignaciones.proponer(new ProponerAsignacionRequest(seccion,
				e.docente().getId(), TipoAsignacion.TUTOR, null, "Tutoría del año aprobada por Dirección")));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, id);
		como(e.equipo().directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		Long apoderado = jdbc.queryForObject("SELECT a.id FROM apoderado a JOIN alumno al ON al.familia_id = a.familia_id "
				+ "WHERE al.id = ?", Long.class, alumno);
		Long matricula = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class, alumno);
		return new Clase(e, seccion, alumno, otro, apoderado, matricula);
	}

	private String numero(int digitos) {
		long n = Math.floorMod(System.nanoTime(), (long) Math.pow(10, digitos));
		return String.format("%0" + digitos + "d", n);
	}

	private <T> T como(Usuario quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** La sesión de la base de esa persona (la abre la prueba por la ruta de identidad). */
	private Long sesionDe(Usuario quien) {
		return como(quien, () -> tokens.actual().map(SesionAbierta::sesionId).orElseThrow());
	}

	/** La tutora registra hoy por la aplicación (con su firma): el camino legítimo. */
	private Long registrarHoy(Clase c, EstadoAsistencia delPrimero) {
		como(c.escuela().docente(), () -> asistencia.guardar(c.seccion(), new HojaRequest(List.of(
				new MarcaPedida(c.alumno(), delPrimero, null), new MarcaPedida(c.otroAlumno(), EstadoAsistencia.PRESENTE,
						null)), null)));
		return marcaVigente(c.alumno());
	}

	private Long marcaVigente(Long alumno) {
		return jdbc.queryForObject("SELECT m.id FROM marca_asistencia m WHERE m.alumno_id = ? AND NOT EXISTS (SELECT 1 FROM "
				+ "marca_asistencia s WHERE s.reemplaza_id = m.id) ORDER BY m.id DESC LIMIT 1", Long.class, alumno);
	}

	/** Una marca que reemplaza a otra, por SQL, con la sesión de quien la escribe (y su firma, si se pide). */
	private Integer insertarSucesora(Clase c, Long reemplaza, String estado, String origen, Usuario quien, String clave,
			String motivo) {
		Long sesion = sesionDe(quien);
		Long registro = jdbc.queryForObject("SELECT registro_id FROM marca_asistencia WHERE id = ?", Long.class, reemplaza);
		LocalDate fecha = jdbc.queryForObject("SELECT fecha FROM marca_asistencia WHERE id = ?", LocalDate.class, reemplaza);
		return this.<Integer>transaccion(quien, t -> {
			if (clave != null) {
				UsuariosDePrueba.iniciarSesion(quien);
				firma.firmar(clave);
				SecurityContextHolder.clearContext();
			}
			Integer codigo = codigoAl(() -> jdbc.update("INSERT INTO marca_asistencia (colegio_id, registro_id, seccion_id, "
					+ "fecha, alumno_id, matricula_id, estado, fuente, origen, reemplaza_id, motivo, sesion_id, creado_en, "
					+ "creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, 'DOCENTE', ?, ?, ?, ?, NOW(6), ?, NOW(6))",
					c.colegio(), registro, c.seccion(), fecha, c.alumno(), c.matricula(), estado, origen, reemplaza, motivo,
					sesion, quien.getNombreUsuario()));
			t.setRollbackOnly();
			return codigo;
		});
	}

	/** E1 y E9: un registro «del día» con la fecha de ayer o de mañana (para la base, hoy es otro día): 1644. */
	@Test
	void e1RegistroDeAyerOMananaSinSolicitudFalla() {
		Clase c = clase("a", "987654401");
		Usuario docente = c.escuela().docente();
		Long sesion = sesionDe(docente);
		for (LocalDate fecha : new LocalDate[] { hoyEnLaBase().minusDays(1), hoyEnLaBase().plusDays(1) }) {
			Integer codigo = this.<Integer>transaccion(docente, t -> {
				UsuariosDePrueba.iniciarSesion(docente);
				firma.firmar(ClaveFirma.asistenciaRegistrada(c.seccion(), fecha));
				SecurityContextHolder.clearContext();
				Integer r = codigoAl(() -> jdbc.update(REGISTRO, c.colegio(), c.escuela().anioId(), c.seccion(), fecha,
						"DOCENTE", docente.getNombreUsuario(), sesion, docente.getNombreUsuario()));
				t.setRollbackOnly();
				return r;
			});
			assertThat(codigo).as(fecha.toString()).isEqualTo(1644);
		}
	}

	/** E49, E50 y E54: sin asignación (Dirección), con la fuente MARCADOR o sin la firma de la sesión: 1644. */
	@Test
	void e49e50e54ElRegistroExigeDocenteFuenteYFirma() {
		soloEnDiaDeClases();
		Clase c = clase("b", "987654411");
		LocalDate hoy = hoyEnLaBase();
		Usuario docente = c.escuela().docente();
		Usuario directora = c.escuela().equipo().directora();
		// Dirección (sin asignación en la sección), aunque firme: 1644.
		Long sesionDirectora = sesionDe(directora);
		assertThat(this.<Integer>transaccion(directora, t -> {
			UsuariosDePrueba.iniciarSesion(directora);
			firma.firmar(ClaveFirma.asistenciaRegistrada(c.seccion(), hoy));
			SecurityContextHolder.clearContext();
			Integer r = codigoAl(() -> jdbc.update(REGISTRO, c.colegio(), c.escuela().anioId(), c.seccion(), hoy, "DOCENTE",
					directora.getNombreUsuario(), sesionDirectora, directora.getNombreUsuario()));
			t.setRollbackOnly();
			return r;
		})).isEqualTo(1644);
		Long sesion = sesionDe(docente);
		// El docente sin firma: 1644 (A5).
		assertThat(codigoAl(() -> jdbc.update(REGISTRO, c.colegio(), c.escuela().anioId(), c.seccion(), hoy, "DOCENTE",
				docente.getNombreUsuario(), sesion, docente.getNombreUsuario()))).isEqualTo(1644);
		// El docente con firma pero con la fuente MARCADOR: 1644 (E50).
		assertThat(this.<Integer>transaccion(docente, t -> {
			UsuariosDePrueba.iniciarSesion(docente);
			firma.firmar(ClaveFirma.asistenciaRegistrada(c.seccion(), hoy));
			SecurityContextHolder.clearContext();
			Integer r = codigoAl(() -> jdbc.update(REGISTRO, c.colegio(), c.escuela().anioId(), c.seccion(), hoy, "MARCADOR",
					docente.getNombreUsuario(), sesion, docente.getNombreUsuario()));
			t.setRollbackOnly();
			return r;
		})).isEqualTo(1644);
		// El otro docente (sin asignación en la sección), con su firma: 1644 (E2).
		Usuario otro = c.escuela().otroDocente();
		Long sesionOtro = sesionDe(otro);
		assertThat(this.<Integer>transaccion(otro, t -> {
			UsuariosDePrueba.iniciarSesion(otro);
			firma.firmar(ClaveFirma.asistenciaRegistrada(c.seccion(), hoy));
			SecurityContextHolder.clearContext();
			Integer r = codigoAl(() -> jdbc.update(REGISTRO, c.colegio(), c.escuela().anioId(), c.seccion(), hoy, "DOCENTE",
					otro.getNombreUsuario(), sesionOtro, otro.getNombreUsuario()));
			t.setRollbackOnly();
			return r;
		})).isEqualTo(1644);
		// Por la aplicación (con su firma), sí; y lo que guardó la base es de hoy, a su nombre y DOCENTE.
		Long marca = registrarHoy(c, EstadoAsistencia.FALTA);
		assertThat(jdbc.queryForObject("SELECT CONCAT(r.fecha, '|', r.registrado_por, '|', r.fuente, '|', "
				+ "(r.registrado_bd IS NOT NULL)) FROM registro_asistencia r JOIN marca_asistencia m ON m.registro_id = r.id "
				+ "WHERE m.id = ?", String.class, marca)).isEqualTo(hoy + "|" + docente.getNombreUsuario() + "|DOCENTE|1");
	}

	/** E54: una marca inicial a nombre de otra persona que la que registró: 1644. E50: una marca MARCADOR: 1644. */
	@Test
	void e54MarcaInicialDeOtroFalla() {
		soloEnDiaDeClases();
		Clase c = clase("c", "987654421");
		registrarHoy(c, EstadoAsistencia.PRESENTE);
		Long registro = jdbc.queryForObject("SELECT registro_id FROM marca_asistencia WHERE alumno_id = ?", Long.class,
				c.alumno());
		// El otro alumno del registro ya tiene su marca inicial; una tercera cuenta (Dirección) agrega una para un alumno
		// con matrícula: no es quien registró ni docente de la sección.
		Usuario directora = c.escuela().equipo().directora();
		Long sesion = sesionDe(directora);
		String inicial = "INSERT INTO marca_asistencia (colegio_id, registro_id, seccion_id, fecha, alumno_id, matricula_id, "
				+ "estado, fuente, origen, primera, sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, "
				+ "?, 'FALTA', ?, 'REGISTRO', TRUE, ?, NOW(6), ?, NOW(6))";
		LocalDate hoy = hoyEnLaBase();
		// (uk_marca_asistencia_primera rechazaría un duplicado del mismo alumno con 1062: el trigger va antes, 1644.)
		assertThat(codigoAl(() -> jdbc.update(inicial, c.colegio(), registro, c.seccion(), hoy, c.alumno(), c.matricula(),
				"DOCENTE", sesion, directora.getNombreUsuario()))).isEqualTo(1644);
		Long sesionDocente = sesionDe(c.escuela().docente());
		assertThat(codigoAl(() -> jdbc.update(inicial, c.colegio(), registro, c.seccion(), hoy, c.alumno(), c.matricula(),
				"MARCADOR", sesionDocente, c.escuela().docente().getNombreUsuario()))).isEqualTo(1644);
	}

	/** E7 (1142) y E8 (1062): la asistencia no se edita ni se borra, y una marca no tiene dos sucesoras. */
	@Test
	void e7e8LaAsistenciaNoSeEditaNiSeBorraNiSeBifurca() {
		for (String tabla : new String[] { "registro_asistencia", "marca_asistencia", "consentimiento_aviso" }) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		// La marca de agua: con cc_app nada (1142); con cc_sistema no cambia de colegio (1143) ni se borra (1142).
		assertThat(codigoAl(() -> jdbc.update("UPDATE aviso_asistencia_corte SET ultima_marca_id = 0 WHERE 1 = 0")))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("UPDATE aviso_asistencia_corte SET colegio_id = colegio_id WHERE 1 = 0")))
				.isEqualTo(1143);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM aviso_asistencia_corte WHERE 1 = 0"))).isEqualTo(1142);
		// La cobertura no se alarga (1143).
		assertThat(codigoAl(() -> jdbc.update("UPDATE asignacion_docente SET hasta = hasta WHERE 1 = 0"))).isEqualTo(1143);

		soloEnDiaDeClases();
		Clase c = clase("d", "987654431");
		Long marca = registrarHoy(c, EstadoAsistencia.FALTA);
		ajustarCierre(c, LocalTime.of(18, 0));
		assumeTrue(LocalTime.now(LIMA).isBefore(LocalTime.of(17, 55)), "después de las 18:00 la corrección ya cerró");
		Usuario docente = c.escuela().docente();
		// La primera sucesora (corrección del día con su firma) entra; la segunda de la MISMA marca: 1062.
		como(docente, () -> {
			asistencia.corregirHoy(marca, new CorreccionRequest(EstadoAsistencia.PRESENTE, null, MOTIVO));
			return null;
		});
		assertThat(insertarSucesora(c, marca, "TARDE", "CORRECCION_DEL_DIA", docente, null, MOTIVO)).isEqualTo(1062);
	}

	/** E51 (A1): una FALTA_JUSTIFICADA del personal (3819) y la corrección de una justificación de la familia (1644). */
	@Test
	void e51DocenteNoJustificaNiTocaLaJustificacion() {
		soloEnDiaDeClases();
		Clase c = clase("e", "987654441");
		Long marca = registrarHoy(c, EstadoAsistencia.FALTA);
		ajustarCierre(c, LocalTime.of(18, 0));
		Usuario docente = c.escuela().docente();
		assertThat(insertarSucesora(c, marca, "FALTA_JUSTIFICADA", "CORRECCION_DEL_DIA", docente,
				ClaveFirma.marcaCorregida(marca), MOTIVO)).isEqualTo(3819);
		// La familia justifica (con la cuenta del apoderado, su sesión y su firma).
		UsuarioAutenticado familia = familia(c);
		como(familia, () -> {
			justificaciones.justificar(marca, new JustificarRequest(MotivoJustificacion.SALUD, null));
			return null;
		});
		Long justificada = marcaVigente(c.alumno());
		assertThat(jdbc.queryForObject("SELECT origen FROM marca_asistencia WHERE id = ?", String.class, justificada))
				.isEqualTo("JUSTIFICACION");
		// El docente no corrige la justificación (aunque firme y sea antes del cierre): 1644.
		assertThat(insertarSucesora(c, justificada, "PRESENTE", "CORRECCION_DEL_DIA", docente,
				ClaveFirma.marcaCorregida(justificada), MOTIVO)).isEqualTo(1644);
		// El personal no «justifica como la familia» (E12): 1644 (o 3819 del CHECK sin apoderado).
		assertThat(codigoAl(() -> jdbc.update("INSERT INTO marca_asistencia (colegio_id, registro_id, seccion_id, fecha, "
				+ "alumno_id, matricula_id, estado, fuente, origen, reemplaza_id, motivo_justificacion, apoderado_id, "
				+ "familia_id, sesion_id, creado_en, creado_por, actualizado_en) SELECT colegio_id, registro_id, seccion_id, "
				+ "fecha, alumno_id, matricula_id, 'FALTA_JUSTIFICADA', 'DOCENTE', 'JUSTIFICACION', id, 'SALUD', ?, "
				+ "(SELECT familia_id FROM apoderado WHERE id = ?), ?, NOW(6), ?, NOW(6) FROM marca_asistencia WHERE id = ?",
				c.apoderado(), c.apoderado(), sesionDe(docente), docente.getNombreUsuario(), marcaVigente(c.otroAlumno()))))
				.isIn(1644, 3819, 1452);
	}

	/** E13 y E53: la reversión exige Dirección con su firma; lo revertido no se vuelve a justificar. */
	@Test
	void e13e53ReversionConFirmaYNoSeRejustifica() {
		soloEnDiaDeClases();
		Clase c = clase("f", "987654451");
		Long marca = registrarHoy(c, EstadoAsistencia.FALTA);
		UsuarioAutenticado familia = familia(c);
		como(familia, () -> {
			justificaciones.justificar(marca, new JustificarRequest(MotivoJustificacion.FAMILIAR, null));
			return null;
		});
		Long justificada = marcaVigente(c.alumno());
		Usuario directora = c.escuela().equipo().directora();
		// Sin firma: 1644. El docente, con su firma: 1644.
		assertThat(insertarSucesora(c, justificada, "FALTA", "REVERSION_JUSTIFICACION", directora, null, MOTIVO))
				.isEqualTo(1644);
		assertThat(insertarSucesora(c, justificada, "FALTA", "REVERSION_JUSTIFICACION", c.escuela().docente(),
				ClaveFirma.marcaRevertida(justificada), MOTIVO)).isEqualTo(1644);
		// Dirección por la aplicación (con su firma), sí.
		como(directora, () -> {
			direccion.revertir(justificada, MOTIVO);
			return null;
		});
		Long revertida = marcaVigente(c.alumno());
		assertThat(jdbc.queryForObject("SELECT origen FROM marca_asistencia WHERE id = ?", String.class, revertida))
				.isEqualTo("REVERSION_JUSTIFICACION");
		// La familia no la vuelve a justificar, ni saltándose la aplicación (con su sesión y su firma): 1644 (A3).
		Long sesionFamilia = como(familia, () -> tokens.actual().map(SesionAbierta::sesionId).orElseThrow());
		Integer rejustificar = this.<Integer>transaccion(familia, t -> {
			UsuariosDePrueba.iniciarSesion(familia);
			firma.firmar(ClaveFirma.marcaJustificada(revertida));
			SecurityContextHolder.clearContext();
			Integer r = codigoAl(() -> jdbc.update("INSERT INTO marca_asistencia (colegio_id, registro_id, seccion_id, fecha, "
					+ "alumno_id, matricula_id, estado, fuente, origen, reemplaza_id, motivo_justificacion, apoderado_id, "
					+ "familia_id, sesion_id, creado_en, creado_por, actualizado_en) SELECT colegio_id, registro_id, "
					+ "seccion_id, fecha, alumno_id, matricula_id, 'FALTA_JUSTIFICADA', 'DOCENTE', 'JUSTIFICACION', id, "
					+ "'SALUD', ?, (SELECT familia_id FROM apoderado WHERE id = ?), ?, NOW(6), ?, NOW(6) FROM "
					+ "marca_asistencia WHERE id = ?", c.apoderado(), c.apoderado(), sesionFamilia, familia.getUsername(),
					revertida));
			t.setRollbackOnly();
			return r;
		});
		assertThat(rejustificar).isEqualTo(1644);
	}

	/** E52 (A2): la corrección del día se cierra a la hora del segundo aviso (con la hora de Lima de la base). */
	@Test
	void e52CorreccionDelDiaDespuesDelCierreFalla() {
		soloEnDiaDeClases();
		assumeTrue(!LocalTime.now(LIMA).isBefore(LocalTime.of(13, 5)), "antes de las 13:00 no se puede cerrar todavía");
		Clase c = clase("g", "987654461");
		Long marca = registrarHoy(c, EstadoAsistencia.FALTA);
		ajustarCierre(c, LocalTime.of(13, 0));
		assertThat(insertarSucesora(c, marca, "PRESENTE", "CORRECCION_DEL_DIA", c.escuela().docente(),
				ClaveFirma.marcaCorregida(marca), MOTIVO)).isEqualTo(1644);
	}

	/** E10: un registro TARDIO sin su solicitud aprobada y firmada: 1644. */
	@Test
	void e10RegistroTardioExigeSuSolicitud() {
		Clase c = clase("h", "987654471");
		Usuario docente = c.escuela().docente();
		Long sesion = sesionDe(docente);
		LocalDate ayer = hoyEnLaBase().minusDays(1);
		Long otraSolicitud = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE colegio_id = ?", Long.class,
				c.colegio());
		assertThat(this.<Integer>transaccion(docente, t -> {
			UsuariosDePrueba.iniciarSesion(docente);
			firma.firmar(ClaveFirma.asistenciaRegistrada(c.seccion(), ayer));
			SecurityContextHolder.clearContext();
			Integer r = codigoAl(() -> jdbc.update("INSERT INTO registro_asistencia (colegio_id, anio_escolar_id, seccion_id, "
					+ "fecha, origen, fuente, solicitud_id, registrado_por, sesion_id, alumnos, creado_en, creado_por, "
					+ "actualizado_en) VALUES (?, ?, ?, ?, 'TARDIO', 'DOCENTE', ?, ?, ?, 1, NOW(6), ?, NOW(6))", c.colegio(),
					c.escuela().anioId(), c.seccion(), ayer, otraSolicitud, docente.getNombreUsuario(), sesion,
					docente.getNombreUsuario()));
			t.setRollbackOnly();
			return r;
		})).isEqualTo(1644);
	}

	/** E55 (A6) y E15: la configuración la cambia solo Dirección con su firma; el aviso no se corre a la noche (3819). */
	@Test
	void e55e15ConfiguracionSinDireccionNiFirmaFalla() {
		Clase c = clase("i", "987654481");
		String admin = c.escuela().equipo().administracion().getNombreUsuario();
		String directora = c.escuela().equipo().directora().getNombreUsuario();
		String insertar = "INSERT INTO configuracion_academica (colegio_id, hora_aviso, actualizado_por, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, ?, ?, NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(insertar, c.colegio(), "11:00:00", admin, admin))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, c.colegio(), "11:00:00", directora, directora))).isEqualTo(1644);
		// Por la aplicación (Dirección con su firma), sí.
		ajustarCierre(c, LocalTime.of(15, 30));
		assertThat(jdbc.queryForObject("SELECT actualizado_por FROM configuracion_academica WHERE colegio_id = ?",
				String.class, c.colegio())).isEqualTo(directora);
		// UPDATE sin firma nueva (o a nombre de Administración): 1644. El aviso a las 20:00: 3819.
		assertThat(codigoAl(() -> jdbc.update("UPDATE configuracion_academica SET hora_aviso = '12:00:00', actualizado_por "
				+ "= ? WHERE colegio_id = ?", admin, c.colegio()))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE configuracion_academica SET hora_aviso = '20:00:00' WHERE colegio_id "
				+ "= ?", c.colegio()))).isIn(3819, 1644);
		assertThat(codigoAl(() -> jdbc.update(insertar.replace("hora_aviso", "hora_segundo_aviso"), 0L, "20:00:00",
				directora, directora))).isIn(3819, 1644);
	}

	/** E56 (A7): una cobertura de más de 10 días calendario (o de tutor) no nace: 1644 o 3819. */
	@Test
	void e56CoberturaLargaFalla() {
		Clase c = clase("j", "987654491");
		String admin = c.escuela().equipo().administracion().getNombreUsuario();
		String insertar = "INSERT INTO asignacion_docente (colegio_id, anio_escolar_id, seccion_id, usuario_id, tipo, estado, "
				+ "pendiente, propuesto_por, hasta, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'PROPUESTA', "
				+ "TRUE, ?, ?, NOW(6), ?, NOW(6))";
		assertThat(codigoAl(() -> jdbc.update(insertar, c.colegio(), c.escuela().anioId(), c.escuela().seccionB(),
				c.escuela().otroDocente().getId(), "AUXILIAR", admin, hoyEnLaBase().plusDays(11), admin))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, c.colegio(), c.escuela().anioId(), c.escuela().seccionB(),
				c.escuela().otroDocente().getId(), "TUTOR", admin, hoyEnLaBase().plusDays(2), admin))).isIn(3819, 1644);
		// Una cobertura que vence hoy todavía da acceso (cc_docente_de_seccion con hasta >= hoy en Lima).
		assertThat(jdbc.update(insertar, c.colegio(), c.escuela().anioId(), c.escuela().seccionB(),
				c.escuela().otroDocente().getId(), "AUXILIAR", admin, hoyEnLaBase(), admin)).isEqualTo(1);
	}

	/** E37 y E40: los avisos de asistencia (con cc_sistema) no hablan de cobranza y no salen por WhatsApp sin el «sí». */
	@Test
	void e37e40AvisosDeAsistenciaSinCobranzaYConConsentimiento() {
		Clase c = clase("k", "987654501");
		String mensaje = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, familia_id, apoderado_id, "
				+ "destino, plantilla, parametros, estado, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, "
				+ "'APODERADO', (SELECT familia_id FROM apoderado WHERE id = ?), ?, ?, ?, ?, 'PENDIENTE', NOW(6), ?, NOW(6))";
		String correo = jdbc.queryForObject("SELECT correo FROM apoderado WHERE id = ?", String.class, c.apoderado());
		// Con un monto: 1644 (la regla de cobranza o la del colegio en preparación).
		assertThat(mensajeDe(() -> sistema().update(mensaje, c.colegio(), "ci-monto-" + sufijo, "ASISTENCIA_DIA", "CORREO",
				c.apoderado(), c.apoderado(), correo, "ASISTENCIA", "Colegio\nlunes\nMateo: falta. Debe S/ 350.00",
				"sistema.asistencia"))).contains("1644", "cuentasclaras");
		// Un recordatorio de deuda con la plantilla de asistencia: 1644 (E40).
		assertThat(codigoAl(() -> sistema().update(mensaje, c.colegio(), "ci-plantilla-" + sufijo,
				"RECORDATORIO_VENCIMIENTO", "CORREO", c.apoderado(), c.apoderado(), correo, "cc_asistencia_v2", "x",
				"sistema.mensajeria"))).isEqualTo(1644);
		// Con cc_app (no es el proceso): 1644.
		assertThat(codigoAl(() -> jdbc.update(mensaje, c.colegio(), "ci-app-" + sufijo, "ASISTENCIA_DIA", "CORREO",
				c.apoderado(), c.apoderado(), correo, "ASISTENCIA", "Colegio\nlunes\nMateo: falta", "sistema.asistencia")))
				.isEqualTo(1644);
		// Por WhatsApp sin consentimiento: 1644 (E37).
		assertThat(codigoAl(() -> sistema().update(mensaje, c.colegio(), "ci-wa-" + sufijo, "ASISTENCIA_DIA", "WHATSAPP",
				c.apoderado(), c.apoderado(), "+51900000000", "ASISTENCIA", "Colegio\nlunes\nMateo: falta",
				"sistema.asistencia"))).isEqualTo(1644);
	}

	/** REGEXP_LIKE de MySQL 8.4 (ICU): «Pagán» y «Vencesla» no coinciden; «pago» y «S/» sí (la misma regla que Java). */
	@Test
	void laReglaDeCobranzaRespetaLosLimitesDePalabra() {
		// cc_app no ve information_schema.TRIGGERS: se usa la expresión de Java, que ContenidoSinCobranzaTest compara con 03.
		String expresion = pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza.EXPRESION;
		for (String nombre : new String[] { "Pagán: falta", "Vencesla: llegó tarde (08:12)", "Cobo: falta" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, nombre, expresion)).as(nombre)
					.isZero();
		}
		for (String cobro : new String[] { "su pago", "S/ 350.00", "la pensión", "Yape" }) {
			assertThat(jdbc.queryForObject("SELECT REGEXP_LIKE(?, ?, 'i')", Integer.class, cobro, expresion)).as(cobro)
					.isEqualTo(1);
		}
	}

	/** A12: sesion_usuario.vence_en está en hora de Lima: una que vence en 1 minuto sirve; una vencida hace 1 minuto no. */
	@Test
	void a12ZonaDeVenceEnEsLima() {
		Clase c = clase("l", "987654511");
		// cc_app no llama a cc_sesion_abierta directamente (no tiene EXECUTE): se prueba por el trigger que la usa. La
		// Administración registra un consentimiento desde la ficha con cada sesión (trg_consentimiento_aviso_nace).
		Usuario admin = c.escuela().equipo().administracion();
		String abrir = "INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, ip, abierta_en, vence_en, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, ?, ?, '127.0.0.1', UTC_TIMESTAMP(6) - INTERVAL 5 HOUR - INTERVAL ? "
				+ "MINUTE, UTC_TIMESTAMP(6) - INTERVAL 5 HOUR + INTERVAL ? MINUTE, NOW(6), 'prueba', NOW(6))";
		RutaConexion.identidad(() -> {
			sistema().update(abrir, c.colegio(), admin.getId(), "a".repeat(48) + numero(16), 0, 1);
			sistema().update(abrir, c.colegio(), admin.getId(), "b".repeat(48) + numero(16), 4, -1);
			return null;
		});
		List<Long> sesiones = jdbc.queryForList("SELECT id FROM sesion_usuario WHERE usuario_id = ? ORDER BY id DESC LIMIT 2",
				Long.class, admin.getId());
		Long vencida = sesiones.get(0);
		Long vigente = sesiones.get(1);
		String consentimiento = "INSERT INTO consentimiento_aviso (colegio_id, familia_id, apoderado_id, finalidad, canal, "
				+ "otorgado, contacto_principal, origen, evidencia, aviso_privacidad_version, sesion_id, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, (SELECT familia_id FROM apoderado WHERE id = ?), ?, 'ASISTENCIA', 'CORREO', TRUE, "
				+ "FALSE, 'FICHA', 'FM-A12', 'v1', ?, NOW(6), ?, NOW(6))";
		// La que vence en 1 minuto (hora de Lima) sirve; la vencida hace 1 minuto, no (con UTC, las dos al revés).
		assertThat(jdbc.update(consentimiento, c.colegio(), c.apoderado(), c.apoderado(), vigente, admin.getNombreUsuario()))
				.isEqualTo(1);
		assertThat(codigoAl(() -> jdbc.update(consentimiento, c.colegio(), c.apoderado(), c.apoderado(), vencida,
				admin.getNombreUsuario()))).isEqualTo(1644);
		// Y la Caja (no es Administración ni Dirección) no registra desde la ficha aunque tenga sesión: 1644 (E38).
		Long caja = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND r.rol = 'CAJA'", Long.class, c.colegio());
		String cajera = jdbc.queryForObject("SELECT nombre_usuario FROM usuario WHERE id = ?", String.class, caja);
		RutaConexion.identidad(() -> sistema().update(abrir, c.colegio(), caja, "c".repeat(48) + numero(16), 0, 5));
		Long sesionCaja = jdbc.queryForObject("SELECT MAX(id) FROM sesion_usuario WHERE usuario_id = ?", Long.class, caja);
		assertThat(codigoAl(() -> jdbc.update(consentimiento, c.colegio(), c.apoderado(), c.apoderado(), sesionCaja,
				cajera))).isEqualTo(1644);
	}

	/** Dirección pone la hora del segundo aviso (con su firma, por la aplicación). */
	private void ajustarCierre(Clase c, LocalTime cierre) {
		LocalTime aviso = LocalTime.of(10, 30);
		como(c.escuela().equipo().directora(), () -> {
			configuraciones.cambiar(new ConfiguracionAcademica.Valores(LocalTime.of(9, 30), aviso, cierre, 5, 5,
					LocalTime.of(18, 0), 4));
			return null;
		});
	}

	/** La cuenta del portal del apoderado del primer alumno (nace enlazada a él, por la ruta de identidad). */
	private UsuarioAutenticado familia(Clase c) {
		String documento = jdbc.queryForObject("SELECT numero_documento FROM apoderado WHERE id = ?", String.class,
				c.apoderado());
		String nombre = "familia.s9." + sufijo + "." + c.apoderado();
		Usuario cuenta = RutaConexion.identidad(() -> ContextoColegio.en(c.colegio(), () -> usuarios.saveAndFlush(
				Usuario.deApoderado(documento, nombre, null, codificador.encode(UsuariosDePrueba.CLAVE), c.apoderado()))));
		return new UsuarioAutenticado(cuenta.getId(), c.colegio(), cuenta.getNombreUsuario(), nombre, null, true, false,
				false, EnumSet.of(Rol.APODERADO), c.apoderado());
	}

	/**
	 * La transacción con la persona YA en sesión: Hibernate fija el colegio de la sesión (el {@code @TenantId} de la firma)
	 * al empezar la transacción, así que la persona va antes.
	 */
	private <T> T transaccion(Usuario quien, org.springframework.transaction.support.TransactionCallback<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return new TransactionTemplate(transacciones).execute(operacion);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private <T> T transaccion(UsuarioAutenticado quien,
			org.springframework.transaction.support.TransactionCallback<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return new TransactionTemplate(transacciones).execute(operacion);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private <T> T transaccion(org.springframework.transaction.support.TransactionCallback<T> operacion) {
		return new TransactionTemplate(transacciones).execute(operacion);
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
			Integer codigo = null;
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					codigo = sql.getErrorCode();
				}
			}
			return codigo + " " + e.getMostSpecificCause().getMessage();
		}
	}
}
