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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.proceso.EmisionLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasColegio;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.LibretasFamilia;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.VerificacionLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.DelegacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosLibretas;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprint 10, tanda 3 (brief, sección 11, «MySQL real»; diseño, secciones 3.7 a 3.10, 3.13 y 6.3) contra MySQL 8 real, con
 * los permisos mínimos de 02 y los triggers de 03. La libreta la emite SOLO sistema.libretas con cc_sistema (cc_app no tiene
 * GRANT: E14), de un periodo CERRADO, para una matrícula ACTIVA o RESERVADA (C1), también la de la familia que debe
 * (INDECOPI); la versión 1 es lo registrado hasta el cierre y la siguiente solo con un cambio aprobado o la reemisión
 * aprobada por Promotoría (A3); la entrega va con la sesión de la familia, de Dirección o del tutor de la sección (M9; en
 * Inicial, en la entrevista); la exportación para el SIAGIE la saca Dirección o la persona de Administración delegada (M12,
 * nunca Caja); los avisos, sin cobranza y con consentimiento (E30). Y la carga: 300 libretas en menos de 60 s.
 * <p>
 * UN colegio para toda la clase (sube el tope a 30, como las del sprint 10). El año del alta (2027, en curso) cubre hoy; su
 * I bimestre queda CERRADO con notas (A: Mateo, con la matrícula vencida; B: Lucas, al día con su beca; C: Sofía, que
 * además tiene la matrícula RESERVADA del año siguiente, con su periodo cerrado y su nota). Las pruebas que cambian algo
 * corren en una transacción que se revierte; el escenario y el flujo con permisos mínimos quedan (el job de respaldo los
 * usa).
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=30")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint10LibretasMySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final String MOTIVO = "Carga horaria aprobada por Dirección para el año";

	private static final String MOTIVO_CORRECCION = "El examen recalificado muestra que logró lo esperado";

	private static final String INSERTAR_LIBRETA = "INSERT INTO libreta_emitida (colegio_id, anio_escolar_id, periodo_id, "
			+ "seccion_id, matricula_id, alumno_id, numero_version, reemplaza_id, motivo, solicitud_id, hasta_calificacion_id, "
			+ "hasta_marca_id, hasta_exoneracion_id, alumno_nombre, alumno_codigo, tipo_codigo, director_nombre, "
			+ "huella_contenido, cambios, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, "
			+ "'Alumno de prueba', '00000000', 'DNI', 'Dirección de prueba', REPEAT('a', 64), ?, NOW(6), 'sistema.libretas', "
			+ "NOW(6))";

	private static final String INSERTAR_ENTREGA = "INSERT INTO entrega_libreta (colegio_id, libreta_id, canal, apoderado_id, "
			+ "sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static final String INSERTAR_EXPORTACION = "INSERT INTO exportacion_calificaciones (colegio_id, periodo_id, "
			+ "seccion_id, perfil, filas, huella_libretas, codigo, sha256, sesion_id, creado_en, creado_por, actualizado_en) "
			+ "VALUES (?, ?, ?, 'SIAGIE_CALIFICACIONES_PROVISIONAL', 3, REPEAT('b', 64), ?, REPEAT('c', 64), ?, NOW(6), ?, "
			+ "NOW(6))";

	private static final String INSERTAR_DELEGACION = "INSERT INTO delegacion_siagie (colegio_id, usuario_id, desde, hasta, "
			+ "autorizado_por, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private static final String INSERTAR_MENSAJE = "INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, "
			+ "familia_id, apoderado_id, destino, plantilla, parametros, entidad, entidad_id, estado, creado_en, creado_por, "
			+ "actualizado_en) VALUES (?, ?, ?, ?, 'APODERADO', ?, ?, ?, ?, ?, 'familia', ?, 'PENDIENTE', NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private RegistroNotas registro;

	@Autowired
	private CorreccionesNota correcciones;

	@Autowired
	private AvanceRegistro avance;

	@Autowired
	private ConfiguracionesEvaluacion configuraciones;

	@Autowired
	private Periodos periodos;

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private EmisionLibretas emision;

	@Autowired
	private AvisosLibretas avisos;

	@Autowired
	private LibretasFamilia portal;

	@Autowired
	private LibretasColegio libretasColegio;

	@Autowired
	private VerificacionLibreta verificacion;

	@Autowired
	private CalificacionesSiagie calificaciones;

	@Autowired
	private DelegacionesSiagie delegaciones;

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

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.comun.prueba.CuentasDePrueba cuentasDePrueba;

	/** El colegio de la clase. */
	private record Escuela(EscuelaConDocentesMySql.Escuela e, long anio, long seccion, long otraSeccion, long mateo,
			long lucas, long sofia, long alumnoMateo, long alumnoLucas, long alumnoSofia, long apoderadoMateo,
			long apoderadoLucas, long apoderadoSofia, Usuario tito, Usuario otroTutor, Usuario cajera, long p1, long p2,
			long siguiente, long seccionSiguiente, long reservada, long periodoSiguiente) {

		long colegio() {
			return e.colegioId();
		}

		Usuario directora() {
			return e.equipo().directora();
		}

		Usuario promotora() {
			return e.equipo().promotora();
		}

		Usuario administracion() {
			return e.equipo().administracion();
		}

		Usuario ana() {
			return e.docente();
		}
	}

	private static Escuela compartida;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private Escuela escuela() {
		if (compartida != null) {
			return compartida;
		}
		assumeTrue(enVentana(), "fuera de la ventana del año para las fechas relativas a hoy");
		EscuelaConDocentesMySql.Escuela e = EscuelaConDocentesMySql.crear(sufijo + "lib", "20600000056", "987654713", alta,
				servicioUsuarios, usuarios, codificador, colegios, estructura, jdbc);
		LocalDate hoy = hoy();
		long anio = e.anioId();
		jdbc.update("UPDATE anio_escolar SET inicio_clases = ?, fin_clases = ? WHERE id = ?", hoy.minusDays(100),
				hoy.plusDays(100), anio);
		// Cobranza de Primaria con la matrícula VENCIDA hace 20 días (las pensiones vencen en el año del plan). La propone
		// Administración, la envía otra persona de Administración y la aprueba Dirección.
		Usuario adm2 = UsuariosDePrueba.guardar(usuarios, codificador, e.colegioId(), "adm2.lib." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		List<LocalDate> vencimientos = new ArrayList<>();
		for (int mes = 3; mes <= 12; mes++) {
			vencimientos.add(LocalDate.of(2027, mes, 1).withDayOfMonth(LocalDate.of(2027, mes, 1).lengthOfMonth()));
		}
		Long plan = como(e.equipo().administracion(), () -> planes.crearBorrador(anio, Nivel.PRIMARIA, new PlanRequest(
				new BigDecimal("300.00"), hoy.minusDays(20), new BigDecimal("350.00"), vencimientos, null)));
		como(adm2, () -> {
			planes.enviar(plan);
			return null;
		});
		como(e.equipo().directora(), () -> planes.aprobar(plan, planes.obtener(plan).version()));
		long seccion = como(e.equipo().administracion(), () -> estructura.crearSeccion(anio,
				new CrearSeccionRequest(Grado.PRIMARIA_5, "L" + sufijo.substring(sufijo.length() - 4))));
		long[] ids = new long[3];
		String[] nombres = { "Mateo", "Lucas", "Sofía" };
		for (int i = 0; i < 3; i++) {
			int n = i;
			ids[i] = como(e.equipo().administracion(), () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(
					"7" + n + numero(6), "Quispe", "Rojas", nombres[n], LocalDate.of(2016, 3, 3), "4" + n + numero(6), "Rojas",
					"Paz", "Rosa", null, "libreta" + n + "." + sufijo + "@correo.pe", seccion)).alumnoId());
		}
		// B (Lucas) está al día: su matrícula vencida tiene una beca del 100 % (la pide Administración y la aprueba
		// Dirección: queda EXONERADA).
		long cuotaLucas = jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND tipo = 'MATRICULA'", Long.class,
				ids[1]);
		Long beca = como(e.equipo().administracion(), () -> descuentos.solicitar(new DescuentoRequest(ids[1],
				TipoDescuento.BECA, ModalidadDescuento.PORCENTAJE, new BigDecimal("100"), List.of(cuotaLucas),
				"Beca completa del reglamento de pensiones", "Reglamento de pensiones, art. 12")));
		Long solicitudBeca = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE entidad = 'descuento' AND "
				+ "entidad_id = ? AND estado = 'PENDIENTE'", Long.class, beca);
		como(e.equipo().directora(), () -> {
			bandeja.aprobar(solicitudBeca, null);
			return null;
		});
		Usuario tito = docente(e, "tito.lib." + sufijo);
		Usuario otroTutor = docente(e, "otro.lib." + sufijo);
		Usuario cajera = RutaConexion.identidad(() -> ContextoColegio.en(e.colegioId(), () -> usuarios
				.findByNombreUsuario("caja.c." + sufijo + "lib").orElseThrow()));
		como(e.equipo().directora(), () -> configuraciones.guardar(anio, bimestres()));
		asignar(e, seccion, e.docente(), TipoAsignacion.DOCENTE, List.of(area("MAT"), area("CYT")));
		asignar(e, seccion, tito, TipoAsignacion.TUTOR, List.of(area("PS")));
		asignar(e, e.seccionA(), otroTutor, TipoAsignacion.TUTOR, List.of(area("PS")));
		long mateo = matricula(ids[0]);
		long lucas = matricula(ids[1]);
		long sofia = matricula(ids[2]);
		long p1 = como(e.equipo().directora(), () -> periodos.programar(anio, hoy.minusDays(90), hoy.minusDays(50)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(p1);
			return null;
		});
		guardar(e.docente(), seccion, "MAT", p1, List.of(mateo, lucas, sofia), "A", null);
		como(e.equipo().directora(), () -> {
			avance.cerrar(p1);
			return null;
		});
		long p2 = como(e.equipo().directora(), () -> periodos.programar(anio, hoy.minusDays(40), hoy.plusDays(60)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(p2);
			return null;
		});
		// C1: el año siguiente PLANIFICADO, con la matrícula RESERVADA (impaga) de Sofía, su evaluación, su I bimestre
		// abierto con la nota de Ana y después CERRADO.
		long siguiente = como(e.equipo().administracion(), () -> estructura.crearAnio(new CrearAnioEscolarRequest(2028,
				LocalDate.of(2028, 1, 2), LocalDate.of(2028, 12, 30), false)));
		jdbc.update("UPDATE anio_escolar SET inicio_clases = ?, fin_clases = ? WHERE id = ?", hoy.minusDays(100),
				hoy.plusDays(100), siguiente);
		long seccionSiguiente = como(e.equipo().administracion(), () -> estructura.crearSeccion(siguiente,
				new CrearSeccionRequest(Grado.PRIMARIA_5, "R" + sufijo.substring(sufijo.length() - 4))));
		jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'RESERVADA', NOW(6), ?, NOW(6))", e.colegioId(),
				ids[2], siguiente, seccionSiguiente, hoy, e.equipo().administracion().getNombreUsuario());
		long reservada = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ? AND anio_escolar_id = ?", Long.class,
				ids[2], siguiente);
		como(e.equipo().directora(), () -> configuraciones.guardar(siguiente, bimestres()));
		asignar(e, seccionSiguiente, e.docente(), TipoAsignacion.DOCENTE, List.of(area("MAT")));
		long periodoSiguiente = como(e.equipo().directora(), () -> periodos.programar(siguiente, hoy.minusDays(60),
				hoy.minusDays(20)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(periodoSiguiente);
			return null;
		});
		guardar(e.docente(), seccionSiguiente, "MAT", periodoSiguiente, List.of(reservada), "A", null);
		como(e.equipo().directora(), () -> {
			avance.cerrar(periodoSiguiente);
			return null;
		});
		long[] apoderados = new long[3];
		for (int i = 0; i < 3; i++) {
			apoderados[i] = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, ids[i]);
		}
		compartida = new Escuela(e, anio, seccion, e.seccionA(), mateo, lucas, sofia, ids[0], ids[1], ids[2], apoderados[0],
				apoderados[1], apoderados[2], tito, otroTutor, cajera, p1, p2, siguiente, seccionSiguiente, reservada,
				periodoSiguiente);
		// En producción (los avisos a las familias solo salen de un colegio en producción: S8-M2).
		cuentasDePrueba.pasarAProduccion(e.colegioId(), e.equipo().promotora());
		// La emisión de sistema.libretas (cc_sistema): la deja la primera pasada.
		emitir(compartida);
		return compartida;
	}

	// ------------------------------------------------------------------------------------------------ E27 (INDECOPI) y C1

	/**
	 * E27 y C1 en MySQL real (LibretaConDeudaTest): la familia que debe (Mateo, con la matrícula vencida), la que está al día
	 * (Lucas, con su beca) y la de la matrícula RESERVADA (Sofía, año siguiente) tienen su libreta, emitida por
	 * sistema.libretas con cc_sistema; las tres la descargan con su cuenta y su sesión (cc_app: la entrega PORTAL pasa por
	 * trg_entrega_libreta_nace); el aviso es el mismo para las tres (lo crea cc_sistema).
	 */
	@Test
	void libretaConDeudaTresFamilias() {
		Escuela s = escuela();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND estado = 'PENDIENTE' AND "
				+ "fecha_vencimiento < ?", Long.class, s.alumnoMateo(), hoy())).as("Mateo debe la matrícula vencida")
				.isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND estado IN ('PENDIENTE', 'PARCIAL') "
				+ "AND fecha_vencimiento < ?", Long.class, s.alumnoLucas(), hoy())).as("Lucas está al día").isZero();
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, s.reservada()))
				.isEqualTo("RESERVADA");
		for (long[] par : new long[][] { { s.mateo(), s.p1() }, { s.lucas(), s.p1() }, { s.sofia(), s.p1() },
				{ s.reservada(), s.periodoSiguiente() } }) {
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? "
					+ "AND creado_por = 'sistema.libretas'", Long.class, par[0], par[1])).as("matrícula " + par[0])
					.isGreaterThanOrEqualTo(1);
		}
		// Las tres familias descargan con 200 (el PDF de sus notas, sin cobranza).
		for (long[] par : new long[][] { { s.apoderadoMateo(), s.mateo(), s.p1() }, { s.apoderadoLucas(), s.lucas(), s.p1() },
				{ s.apoderadoSofia(), s.reservada(), s.periodoSiguiente() } }) {
			UsuarioAutenticado familia = cuentaDe(s, par[0], "d");
			long libreta = vigente(par[1], par[2]);
			Descarga d = como(familia, () -> portal.descargar(libreta));
			assertThat(d.enRevision()).as("libreta " + libreta).isFalse();
			assertThat(ContenidoSinCobranza.hablaDeCobranza(texto(d.pdf()))).isFalse();
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entrega_libreta WHERE libreta_id = ? AND canal = 'PORTAL' AND "
					+ "apoderado_id = ?", Long.class, libreta, par[0])).isGreaterThanOrEqualTo(1);
		}
		// El aviso lo crea cc_sistema y es el mismo para las tres familias (sin contacto verificado aún, ninguna recibe:
		// igual para todas; el mensaje creado lo prueban LibretaConDeudaTest en H2 y los rechazos de trg_mensaje_nace aquí).
		int avisadas = avisos.enColegio(s.colegio(), LocalDateTime.of(hoy(), LocalTime.of(10, 0)));
		List<Long> porFamilia = new ArrayList<>();
		for (long apoderado : new long[] { s.apoderadoMateo(), s.apoderadoLucas(), s.apoderadoSofia() }) {
			porFamilia.add(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'",
					Long.class, apoderado));
		}
		assertThat(porFamilia).as("el mismo trato para las tres (avisadas: " + avisadas + ")").containsOnly(porFamilia.get(0));
	}

	/** E14: con la clave de cc_app nadie crea, edita ni borra una libreta (1142). */
	@Test
	void e14LibretaConCcAppFalla() {
		Escuela s = escuela();
		assertThat(codigoAl(() -> jdbc.update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.mateo(),
				s.alumnoMateo(), 9, null, "CIERRE", null, 0, null))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("UPDATE libreta_emitida SET version = version WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM libreta_emitida WHERE 1 = 0"))).isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("UPDATE libreta_emitida SET version = version WHERE 1 = 0")))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> sistema().update("DELETE FROM libreta_emitida WHERE 1 = 0"))).isEqualTo(1142);
		for (String tabla : List.of("entrega_libreta", "exportacion_calificaciones", "delegacion_siagie")) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
	}

	/** Ni sistema.libretas emite una versión 2 sin una corrección aprobada (el trigger lee su propia tabla). */
	@Test
	void libretaVersion2SinCorreccionFalla() {
		Escuela s = escuela();
		long v1 = vigente(s.sofia(), s.p1());
		long hasta = jdbc.queryForObject("SELECT hasta_calificacion_id FROM libreta_emitida WHERE id = ?", Long.class, v1);
		int version = jdbc.queryForObject("SELECT numero_version FROM libreta_emitida WHERE id = ?", Integer.class, v1);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.sofia(),
				s.alumnoSofia(), version + 1, v1, "CORRECCION", null, hasta + 1000, "Cambio sin aprobación"))).isEqualTo(1644);
		// Ni a otra versión que no sea la siguiente de la vigente.
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.sofia(),
				s.alumnoSofia(), version + 2, v1, "CORRECCION", null, hasta, "Salto de versión"))).isEqualTo(1644);
		// Ni como otra persona (creado_por debe ser sistema.libretas) ni con cc_app.
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA.replace("'sistema.libretas'", "'sistema.panel'"),
				s.colegio(), s.anio(), s.p1(), s.seccion(), s.sofia(), s.alumnoSofia(), version + 1, v1, "CORRECCION", null,
				hasta, "Otro proceso"))).isIn(1644, 3819);
	}

	/** C1: una RETIRADA no recibe libreta; la RESERVADA sí (la emitió el proceso). */
	@Test
	void libretaDeMatriculaRetiradaFallaYC1ReservadaPasa() {
		Escuela s = escuela();
		long retirada = alumnoNuevo(s, "Retirado");
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", hoy(), retirada);
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM matricula WHERE id = ?", Long.class, retirada);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), retirada,
				alumno, 1, null, "CIERRE", null, 0, null))).isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", Long.class,
				s.reservada())).as("c1LibretaDeMatriculaReservadaPasa").isEqualTo(1);
		// De un periodo ABIERTO tampoco.
		long nueva = alumnoNuevo(s, "Abierto");
		long alumnoNueva = jdbc.queryForObject("SELECT alumno_id FROM matricula WHERE id = ?", Long.class, nueva);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p2(), s.seccion(), nueva,
				alumnoNueva, 1, null, "CIERRE", null, 0, null))).isEqualTo(1644);
	}

	/**
	 * Sección 3 del brief: la versión 1 es lo registrado hasta el cerrado_en del periodo (hora de Lima de la base y de la
	 * aplicación): una marca de agua mayor se rechaza; la del proceso es exactamente la última nota anterior al cierre.
	 */
	@Test
	void version1ConMarcaPosteriorAlCierreFalla() {
		Escuela s = escuela();
		long nueva = alumnoNuevo(s, "Marca");
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM matricula WHERE id = ?", Long.class, nueva);
		long ultima = jdbc.queryForObject("SELECT MAX(id) FROM calificacion", Long.class);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), nueva, alumno,
				1, null, "CIERRE", null, ultima, null))).isEqualTo(1644);
		// La marca de agua de la versión 1 de Mateo: la última nota suya registrada (registrado_bd, hora de Lima de la base)
		// hasta el cerrado_en del periodo (hora de Lima de la aplicación).
		long v1 = jdbc.queryForObject("SELECT id FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? AND "
				+ "numero_version = 1", Long.class, s.mateo(), s.p1());
		Long esperada = jdbc.queryForObject("SELECT COALESCE(MAX(c.id), 0) FROM calificacion c JOIN periodo_evaluacion p ON "
				+ "p.id = ? WHERE c.matricula_id = ? AND c.registrado_bd <= p.cerrado_en", Long.class, s.p1(), s.mateo());
		assertThat(jdbc.queryForObject("SELECT hasta_calificacion_id FROM libreta_emitida WHERE id = ?", Long.class, v1))
				.isEqualTo(esperada).isPositive();
		LocalDateTime cerrado = jdbc.queryForObject("SELECT cerrado_en FROM periodo_evaluacion WHERE id = ?",
				LocalDateTime.class, s.p1());
		LocalDateTime ahoraLima = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6) - INTERVAL 5 HOUR", LocalDateTime.class);
		assertThat(java.time.Duration.between(cerrado, ahoraLima).abs().toHours()).as("cerrado_en en hora de Lima")
				.isLessThan(6);
	}

	/** A3: una REEMISION sin su REEMISION_LIBRETA (u otra solicitud) se rechaza. */
	@Test
	void a3ReemisionSinSolicitudFalla() {
		Escuela s = escuela();
		long v = vigente(s.lucas(), s.p1());
		int version = jdbc.queryForObject("SELECT numero_version FROM libreta_emitida WHERE id = ?", Integer.class, v);
		long hasta = jdbc.queryForObject("SELECT hasta_calificacion_id FROM libreta_emitida WHERE id = ?", Long.class, v);
		Long otraSolicitud = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = ? AND tipo = "
				+ "'ASIGNACION_DOCENTE'", Long.class, s.colegio());
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.lucas(),
				s.alumnoLucas(), version + 1, v, "REEMISION", otraSolicitud, hasta, "Reemitida tras una revisión de "
						+ "integridad"))).isEqualTo(1644);
	}

	/** A3: la reemisión la aprueba PROMOTORÍA; aprobada por otra persona de Dirección, la libreta no nace. */
	@Test
	void reemisionAprobadaPorDireccionFalla() {
		Escuela s = escuela();
		long v = vigente(s.lucas(), s.p1());
		int version = jdbc.queryForObject("SELECT numero_version FROM libreta_emitida WHERE id = ?", Integer.class, v);
		long hasta = jdbc.queryForObject("SELECT hasta_calificacion_id FROM libreta_emitida WHERE id = ?", Long.class, v);
		Usuario otraDireccion = otraDireccion(s);
		Long solicitud = enTransaccionComprometida(s.directora(), () -> {
			jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, "
					+ "pendiente, solicitado_por, creado_en, creado_por, actualizado_en) VALUES (?, 'REEMISION_LIBRETA', "
					+ "'libreta_emitida', ?, 'Reemitir la libreta', '{}', 'Revisión de integridad de la prueba', 'PENDIENTE', "
					+ "TRUE, ?, NOW(6), ?, NOW(6))", s.colegio(), v, s.directora().getNombreUsuario(),
					s.directora().getNombreUsuario());
			return jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE colegio_id = ? AND tipo = "
					+ "'REEMISION_LIBRETA'", Long.class, s.colegio());
		});
		aprobarCrudo(solicitud, otraDireccion);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("APROBADA");
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.lucas(),
				s.alumnoLucas(), version + 1, v, "REEMISION", solicitud, hasta, "Reemitida tras una revisión de integridad")))
				.isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ Entregas (E32, M9)

	/** E32: un apoderado no deja constancia de la libreta de OTRA familia (ni con su sesión). */
	@Test
	void e32EntregaAOtraFamiliaFalla() {
		Escuela s = escuela();
		UsuarioAutenticado deMateo = cuentaDe(s, s.apoderadoMateo(), "e");
		long deLucas = vigente(s.lucas(), s.p1());
		Integer codigo = enTransaccion(deMateo, () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), deLucas,
				"PORTAL", s.apoderadoMateo(), sesion(deMateo), deMateo.getUsername())));
		assertThat(codigo).isEqualTo(1644);
		// Control: la suya, sí.
		long suya = vigente(s.mateo(), s.p1());
		assertThat(enTransaccion(deMateo, () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), suya, "PORTAL",
				s.apoderadoMateo(), sesion(deMateo), deMateo.getUsername())))).isNull();
	}

	/** M9: el tutor VIGENTE imprime las de su sección; el tutor de otra sección y la cajera, no. */
	@Test
	void m9TutorImprimeSoloSuSeccion() {
		Escuela s = escuela();
		long libreta = vigente(s.mateo(), s.p1());
		assertThat(enTransaccion(s.tito(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"IMPRESA", null, sesion(s.tito()), s.tito().getNombreUsuario())))).isNull();
		assertThat(enTransaccion(s.otroTutor(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"IMPRESA", null, sesion(s.otroTutor()), s.otroTutor().getNombreUsuario())))).isEqualTo(1644);
		assertThat(enTransaccion(s.ana(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"IMPRESA", null, sesion(s.ana()), s.ana().getNombreUsuario())))).as("docente que no es tutor").isEqualTo(1644);
		// Por la aplicación: el tutor imprime (constancia IMPRESA a su nombre).
		Descarga d = como(s.tito(), () -> libretasColegio.imprimir(libreta));
		assertThat(d.enRevision()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entrega_libreta WHERE libreta_id = ? AND canal = 'IMPRESA' AND "
				+ "creado_por = ?", Long.class, libreta, s.tito().getNombreUsuario())).isGreaterThanOrEqualTo(1);
	}

	/** M9: la entrega en la entrevista es solo de Inicial (en Primaria, 1644). */
	@Test
	void entrevistaFueraDeInicialFalla() {
		Escuela s = escuela();
		long libreta = vigente(s.mateo(), s.p1());
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"ENTREVISTA", null, sesion(s.directora()), s.directora().getNombreUsuario())))).isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"IMPRESA", null, sesion(s.directora()), s.directora().getNombreUsuario())))).as("Dirección imprime").isNull();
	}

	// ------------------------------------------------------------------------------------------------ SIAGIE (M12, A6)

	/** M12: sin delegación, Administración no saca la exportación (ni con su sesión). Dirección sí. */
	@Test
	void m12SinDelegacionFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.administracion(), () -> codigoAl(() -> jdbc.update(INSERTAR_EXPORTACION, s.colegio(),
				s.p1(), s.seccion(), "CAL-M12A" + numero(2), sesion(s.administracion()),
				s.administracion().getNombreUsuario())))).isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_EXPORTACION, s.colegio(), s.p1(),
				s.seccion(), "CAL-M12B" + numero(2), sesion(s.directora()), s.directora().getNombreUsuario())))).isNull();
	}

	/** M12: una delegación que todavía no rige (o ya venció) no deja exportar hoy. */
	@Test
	void m12DelegacionVencidaFalla() {
		Escuela s = escuela();
		Usuario secretaria = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "secre.venc." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		LocalDate manana = hoy().plusDays(1);
		enTransaccionComprometida(s.directora(), () -> {
			firma.firmar(ClaveFirma.delegacionSiagie(secretaria.getId(), manana));
			assertThat(codigoAl(() -> jdbc.update(INSERTAR_DELEGACION, s.colegio(), secretaria.getId(), manana,
					manana.plusDays(5), s.directora().getNombreUsuario(), s.directora().getNombreUsuario()))).isNull();
			return null;
		});
		assertThat(enTransaccion(UsuariosDePrueba.autenticado(secretaria), () -> codigoAl(() -> jdbc.update(
				INSERTAR_EXPORTACION, s.colegio(), s.p1(), s.seccion(), "CAL-VENC" + numero(2),
				sesion(UsuariosDePrueba.autenticado(secretaria)), secretaria.getNombreUsuario())))).isEqualTo(1644);
		// Una que empezó ayer no se puede crear (desde es hoy o después): no hay delegaciones retroactivas.
		assertThat(enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.delegacionSiagie(secretaria.getId(), hoy().minusDays(1)));
			return codigoAl(() -> jdbc.update(INSERTAR_DELEGACION, s.colegio(), secretaria.getId(), hoy().minusDays(1),
					hoy().plusDays(3), s.directora().getNombreUsuario(), s.directora().getNombreUsuario()));
		})).isEqualTo(1644);
	}

	/** Decisión 189 ajustada: nunca a Caja. */
	@Test
	void delegacionACajaFalla() {
		Escuela s = escuela();
		assertThat(delegar(s, s.cajera(), hoy(), hoy().plusDays(5))).isEqualTo(1644);
		assertThat(delegar(s, s.ana(), hoy(), hoy().plusDays(5))).as("ni a un docente").isEqualTo(1644);
	}

	/** Decisión 189 ajustada: ni a una cuenta que combine Administración con Caja (si la base la dejara existir). */
	@Test
	void delegacionACuentaConCajaFalla() {
		Escuela s = escuela();
		Usuario mixta = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "mixta.lib." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		Integer rol = codigoAl(() -> sistema().update("INSERT INTO usuario_rol (usuario_id, rol) VALUES (?, 'CAJA')",
				mixta.getId()));
		if (rol != null) {
			// La base ni siquiera deja combinar Administración con Caja: la cuenta mixta no existe.
			assertThat(rol).isIn(1644, 3819, 1142);
			return;
		}
		assertThat(delegar(s, mixta, hoy(), hoy().plusDays(5))).isEqualTo(1644);
	}

	/** M12: hasta 15 días. */
	@Test
	void delegacionDeMasDe15DiasFalla() {
		Escuela s = escuela();
		Usuario secretaria = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "secre.15." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		assertThat(delegar(s, secretaria, hoy(), hoy().plusDays(16))).isEqualTo(1644);
		assertThat(delegar(s, secretaria, hoy(), hoy().plusDays(15))).as("control: 15 días").isNull();
		// Sin la firma de Dirección, tampoco.
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_DELEGACION, s.colegio(),
				secretaria.getId(), hoy(), hoy().plusDays(2), s.directora().getNombreUsuario(),
				s.directora().getNombreUsuario())))).isEqualTo(1644);
	}

	/** La exportación de un periodo ABIERTO se rechaza. */
	@Test
	void exportacionDePeriodoAbiertoFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_EXPORTACION, s.colegio(), s.p2(),
				s.seccion(), "CAL-ABIE" + numero(2), sesion(s.directora()), s.directora().getNombreUsuario()))))
				.isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ Avisos (E30)

	/**
	 * E30: un aviso de libreta que habla de cobranza no nace (ni lo crea cc_app). Uno limpio pasa la regla de las libretas
	 * (lo frena después la del contacto sin verificar): la plantilla con el nombre que guarda la aplicación vale.
	 */
	@Test
	void e30AvisoDeLibretaConCobranzaFalla() {
		Escuela s = escuela();
		String correo = jdbc.queryForObject("SELECT correo FROM apoderado WHERE id = ?", String.class, s.apoderadoMateo());
		long familia = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, s.apoderadoMateo());
		String colegio = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		assertThat(mensajeDe(() -> sistema().update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-cob-" + sufijo,
				"LIBRETA_DISPONIBLE", "CORREO", familia, s.apoderadoMateo(), correo, "LIBRETA",
				colegio + "\nI bimestre\nMateo. Debe S/ 350.00", familia, "sistema.libretas"))).contains("1644")
				.contains("cobranza");
		assertThat(mensajeDe(() -> jdbc.update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-app-" + sufijo, "LIBRETA_DISPONIBLE",
				"CORREO", familia, s.apoderadoMateo(), correo, "LIBRETA", colegio + "\nI bimestre\nMateo", familia,
				"sistema.libretas"))).contains("1644");
		// Con otra plantilla (de cobranza) para un aviso de libreta, o la de libretas para un recordatorio: 1644.
		assertThat(codigoAl(() -> sistema().update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-pla-" + sufijo,
				"LIBRETA_DISPONIBLE", "CORREO", familia, s.apoderadoMateo(), correo, "RECORDATORIO",
				colegio + "\nI bimestre\nMateo", familia, "sistema.libretas"))).isEqualTo(1644);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-rec-" + sufijo,
				"RECORDATORIO_VENCIMIENTO", "CORREO", familia, s.apoderadoMateo(), correo, "LIBRETA",
				colegio + "\nI bimestre\nMateo", familia, "sistema.mensajeria"))).isEqualTo(1644);
		// Limpio: pasa la regla de las libretas (el rechazo es el del contacto todavía no verificado).
		String limpio = mensajeDe(() -> sistema().update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-ok-" + sufijo,
				"LIBRETA_DISPONIBLE", "CORREO", familia, s.apoderadoMateo(), correo, "LIBRETA", colegio + "\nI bimestre\nMateo",
				familia, "sistema.libretas"));
		assertThat(limpio).doesNotContain("cobranza").doesNotContain("plantilla").doesNotContain("proceso");
	}

	/** Decisión 180: por WhatsApp, solo con el último consentimiento LIBRETAS otorgado. */
	@Test
	void avisoSinConsentimientoLibretasPorWhatsappFalla() {
		Escuela s = escuela();
		long familia = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, s.apoderadoLucas());
		String colegio = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = ?", String.class, s.colegio());
		assertThat(mensajeDe(() -> sistema().update(INSERTAR_MENSAJE, s.colegio(), "ci-lib-wa-" + sufijo,
				"LIBRETA_CORREGIDA", "WHATSAPP", familia, s.apoderadoLucas(), "+51900000000", "LIBRETA_CORREGIDA",
				colegio + "\nI bimestre\nLucas", familia, "sistema.libretas"))).contains("1644").contains("consentimiento");
	}

	// ------------------------------------------------------------------------------------------------ CHECK con NULL

	/** E42: los CHECK con columnas que admiten NULL (la cadena de la libreta y el canal de la entrega) rechazan (3819). */
	@Test
	void checksConNull() {
		Escuela s = escuela();
		long nueva = alumnoNuevo(s, "Nulos");
		long alumno = jdbc.queryForObject("SELECT alumno_id FROM matricula WHERE id = ?", Long.class, nueva);
		// Versión 1 con cambios (debe ser NULL), con solicitud (debe ser NULL): el trigger la deja pasar, el CHECK no.
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), nueva, alumno,
				1, null, "CIERRE", null, 0, "No va en la versión 1"))).isEqualTo(3819);
		Long unaSolicitud = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = ?", Long.class,
				s.colegio());
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), nueva, alumno,
				1, null, "CIERRE", unaSolicitud, 0, null))).isEqualTo(3819);
		// Una versión 2 sin reemplaza (NULL) ni cambios (NULL): 1644 (el trigger) o 3819 (el CHECK).
		long v = vigente(s.sofia(), s.p1());
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.sofia(),
				s.alumnoSofia(), 2, null, "CORRECCION", null, 0, null))).isIn(1644, 3819);
		assertThat(codigoAl(() -> sistema().update(INSERTAR_LIBRETA, s.colegio(), s.anio(), s.p1(), s.seccion(), s.sofia(),
				s.alumnoSofia(), 2, v, "CORRECCION", null, 0, null))).isIn(1644, 3819);
		// IMPRESA con apoderado (debe ser NULL): el trigger la deja pasar a Dirección, el CHECK no.
		long libreta = vigente(s.mateo(), s.p1());
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta,
				"IMPRESA", s.apoderadoMateo(), sesion(s.directora()), s.directora().getNombreUsuario())))).isEqualTo(3819);
		// PORTAL sin apoderado (NULL): 1644 (el trigger) o 3819.
		UsuarioAutenticado deMateo = cuentaDe(s, s.apoderadoMateo(), "n");
		assertThat(enTransaccion(deMateo, () -> codigoAl(() -> jdbc.update(INSERTAR_ENTREGA, s.colegio(), libreta, "PORTAL",
				null, sesion(deMateo), deMateo.getUsername())))).isIn(1644, 3819);
	}

	// ------------------------------------------------------------------------------------------------ Flujo y carga

	/**
	 * El flujo con los permisos mínimos de cc_app y cc_sistema (deja sus datos para el job de respaldo): una corrección
	 * aprobada da la versión 2 (emitida por sistema.libretas), la familia la descarga, el tutor imprime la sección,
	 * Dirección verifica el código y exporta para el SIAGIE (A6), delega con su firma a Administración y la delegada exporta.
	 */
	@Test
	void flujoLibretasConPermisosMinimos() {
		Escuela s = escuela();
		long competencia = competencias("MAT").get(0);
		long antes = vigente(s.mateo(), s.p1());
		long nota = vigenteNota(s.mateo(), competencia, s.p1());
		Long solicitud = como(s.ana(), () -> correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION));
		como(s.promotora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		EmisorLibretas.Resultado r = emitir(s);
		assertThat(r.corregidas()).isGreaterThanOrEqualTo(1);
		long v2 = vigente(s.mateo(), s.p1());
		assertThat(v2).isNotEqualTo(antes);
		assertThat(jdbc.queryForMap("SELECT motivo, reemplaza_id, cambios, registrado_bd FROM libreta_emitida WHERE id = ?",
				v2)).containsEntry("motivo", "CORRECCION").containsEntry("reemplaza_id", antes)
				.hasEntrySatisfying("cambios", c -> assertThat((String) c).contains("A → AD"));
		// La familia la descarga (PORTAL) y el tutor imprime la sección (IMPRESA).
		UsuarioAutenticado familia = cuentaDe(s, s.apoderadoMateo(), "f");
		assertThat(como(familia, () -> portal.descargar(v2)).enRevision()).isFalse();
		Descarga seccion = como(s.tito(), () -> libretasColegio.imprimirSeccion(s.seccion(), s.p1()));
		assertThat(seccion.libretas()).isGreaterThanOrEqualTo(3);
		// Dirección verifica el código: vigente y con los niveles.
		String codigo = "LIB-" + v2 + "-" + jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?",
				String.class, v2).substring(0, 16);
		VerificacionVista vista = como(s.directora(), () -> verificacion.verificar(codigo));
		assertThat(vista.existe()).isTrue();
		assertThat(vista.vigente()).isTrue();
		assertThat(vista.coincide()).isTrue();
		// La exportación (A6): de las libretas vigentes; la huella la calcula Java y la base la guarda.
		CalificacionesSiagie.Archivo archivo = como(s.directora(), () -> calificaciones.exportar(s.seccion(), s.p1()));
		assertThat(archivo.filas()).isGreaterThanOrEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT huella_libretas FROM exportacion_calificaciones WHERE codigo = ?",
				String.class, archivo.codigo())).matches("[0-9a-f]{64}");
		// La delegación a Administración (con la firma de Dirección) y la exportación de la delegada con su sesión.
		Usuario secretaria = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "secre.flujo." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		como(s.directora(), () -> {
			delegaciones.delegar(secretaria.getId(), hoy(), hoy().plusDays(10));
			return null;
		});
		CalificacionesSiagie.Archivo delegada = como(secretaria, () -> calificaciones.exportar(s.seccion(), s.p1()));
		assertThat(jdbc.queryForObject("SELECT creado_por FROM exportacion_calificaciones WHERE codigo = ?", String.class,
				delegada.codigo())).isEqualTo(secretaria.getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE colegio_id = ?", Long.class, s.colegio()))
				.isGreaterThanOrEqualTo(5);
	}

	/** Carga (riesgo 1, decisión 191): 300 libretas emitidas al cerrar en una pasada, en menos de 60 s. */
	@Test
	void cargaTrescientasLibretasEnMenosDeUnMinuto() {
		Escuela s = escuela();
		String base = numero(3);
		for (int sec = 0; sec < 10; sec++) {
			int n = sec;
			long seccion = como(s.administracion(), () -> estructura.crearSeccion(s.anio(), new CrearSeccionRequest(
					Grado.SECUNDARIA_1, "Q" + n + sufijo.substring(sufijo.length() - 3))));
			for (int a = 0; a < 30; a++) {
				int k = n * 30 + a;
				String letras = "" + (char) ('a' + k / 26 % 26) + (char) ('a' + k % 26);
				como(s.administracion(), () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("8" + base
						+ String.format("%04d", k), "Carga", "Libreta", "Alumn" + letras, LocalDate.of(2013, 5, 5), "5" + base
						+ String.format("%04d", k), "Carga", "Padre", "Padr" + letras, null, "cargalib" + k + "." + sufijo
						+ "@correo.pe", seccion)));
			}
		}
		long inicio = System.nanoTime();
		EmisorLibretas.Resultado r = emitir(s);
		long ms = (System.nanoTime() - inicio) / 1_000_000;
		org.slf4j.LoggerFactory.getLogger(getClass()).info("Carga de libretas: {} emitidas en {} ms", r.total(), ms);
		assertThat(r.alCierre()).isGreaterThanOrEqualTo(300);
		assertThat(ms).as("300 libretas en milisegundos").isLessThan(60_000);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND accion = "
				+ "'LIBRETAS_EMITIDAS'", Long.class, s.colegio())).as("un evento por sección (decisión 191)")
				.isLessThan(r.alCierre());
	}

	// ------------------------------------------------------------------------------------------------ Ayudas

	private EmisorLibretas.Resultado emitir(Escuela s) {
		SecurityContextHolder.clearContext();
		return emision.enColegio(s.colegio());
	}

	private Integer delegar(Escuela s, Usuario quien, LocalDate desde, LocalDate hasta) {
		return enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.delegacionSiagie(quien.getId(), desde));
			return codigoAl(() -> jdbc.update(INSERTAR_DELEGACION, s.colegio(), quien.getId(), desde, hasta,
					s.directora().getNombreUsuario(), s.directora().getNombreUsuario()));
		});
	}

	/** Una alumna nueva ACTIVA en la sección (sin libreta ni notas). @return su matrícula */
	private long alumnoNuevo(Escuela s, String nombre) {
		long alumno = como(s.administracion(), () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("6" + numero(7),
				"Nueva", "Prueba", nombre, LocalDate.of(2016, 4, 4), "3" + numero(7), "Nueva", "Madre", "Ana", null,
				"nueva." + nombre.toLowerCase() + "." + sufijo + "@correo.pe", s.seccion())).alumnoId());
		return matricula(alumno);
	}

	private UsuarioAutenticado cuentaDe(Escuela s, Long apoderado, String marca) {
		String documento = jdbc.queryForObject("SELECT numero_documento FROM apoderado WHERE id = ?", String.class, apoderado);
		String nombre = "familia.lib." + marca + "." + sufijo + "." + apoderado;
		Usuario cuenta = RutaConexion.identidad(() -> ContextoColegio.en(s.colegio(), () -> usuarios.findByApoderadoId(
				apoderado).orElseGet(() -> usuarios.saveAndFlush(Usuario.deApoderado(documento, nombre, null,
						codificador.encode(UsuariosDePrueba.CLAVE), apoderado)))));
		return new UsuarioAutenticado(cuenta.getId(), s.colegio(), cuenta.getNombreUsuario(), nombre, null, true, false,
				false, EnumSet.of(Rol.APODERADO), apoderado);
	}

	/** Otra persona de Dirección: Promotoría le da el rol y la Dirección actual lo aprueba (CAMBIO_ROLES). */
	private Usuario otraDireccion(Escuela s) {
		Usuario cuenta = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "dir2.lib." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		como(s.promotora(), () -> servicioUsuarios.cambiarRoles(cuenta.getId(), new CambiarRolesRequest(
				EnumSet.of(Rol.DIRECTOR), "Subdirección del colegio para las pruebas de libretas")));
		Long solicitud = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE colegio_id = ? AND estado = "
				+ "'PENDIENTE' AND entidad_id = ?", Long.class, s.colegio(), cuenta.getId());
		como(s.directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		return ContextoColegio.en(s.colegio(), () -> usuarios.findById(cuenta.getId()).orElseThrow());
	}

	/** Aprueba la solicitud por SQL a nombre de {@code quien} con SU firma (sin la bandeja ni su manejador). */
	private void aprobarCrudo(Long solicitud, Usuario quien) {
		enTransaccionComprometida(quien, () -> {
			firma.firmar(ClaveFirma.solicitud(solicitud, EstadoSolicitud.APROBADA));
			jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, resuelto_en = "
					+ "NOW(6), version = version + 1 WHERE id = ?", quien.getNombreUsuario(), solicitud);
			return null;
		});
	}

	private Usuario docente(EscuelaConDocentesMySql.Escuela e, String nombre) {
		return UsuariosDePrueba.guardar(usuarios, codificador, e.colegioId(), nombre, UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
	}

	private void asignar(EscuelaConDocentesMySql.Escuela e, long seccion, Usuario docente, TipoAsignacion tipo,
			List<Long> areas) {
		Long id = como(e.equipo().administracion(), () -> asignaciones.proponer(ProponerAsignacionRequest.conAreas(seccion,
				docente.getId(), tipo, MOTIVO, areas)));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, id);
		como(e.equipo().directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private void guardar(Usuario quien, long seccion, String codigoArea, long periodo, List<Long> matriculas, String nivel,
			String conclusion) {
		List<NotaPedida> notas = new ArrayList<>();
		for (Long m : matriculas) {
			for (Long c : competencias(codigoArea)) {
				notas.add(new NotaPedida(m, c, nivel, conclusion));
			}
		}
		como(quien, () -> registro.guardar(seccion, area(codigoArea), periodo, new NotasRequest(notas, null)));
	}

	private long vigente(long matricula, long periodo) {
		return jdbc.queryForObject("SELECT id FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? ORDER BY "
				+ "numero_version DESC LIMIT 1", Long.class, matricula, periodo);
	}

	private long vigenteNota(long matricula, long competencia, long periodo) {
		return jdbc.queryForObject("SELECT c.id FROM calificacion c WHERE c.matricula_id = ? AND c.competencia_id = ? AND "
				+ "c.periodo_id = ? AND NOT EXISTS (SELECT 1 FROM calificacion x WHERE x.reemplaza_id = c.id)", Long.class,
				matricula, competencia, periodo);
	}

	private Long sesion(UsuarioAutenticado quien) {
		org.springframework.security.core.Authentication antes = SecurityContextHolder.getContext().getAuthentication();
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return tokens.actual().map(SesionAbierta::sesionId).orElseThrow();
		}
		finally {
			SecurityContextHolder.getContext().setAuthentication(antes);
		}
	}

	private Long sesion(Usuario quien) {
		return sesion(UsuariosDePrueba.autenticado(quien));
	}

	private long matricula(long alumno) {
		return jdbc.queryForObject("SELECT MIN(id) FROM matricula WHERE alumno_id = ?", Long.class, alumno);
	}

	private long area(String codigo) {
		return jdbc.queryForObject("SELECT a.id FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id WHERE "
				+ "v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = ?", Long.class, codigo);
	}

	private List<Long> competencias(String codigoArea) {
		return jdbc.queryForList("SELECT id FROM competencia WHERE area_id = ? AND (solo_grados IS NULL OR "
				+ "FIND_IN_SET('PRIMARIA_5', solo_grados) > 0) ORDER BY orden", Long.class, area(codigoArea));
	}

	private ConfiguracionEvaluacion.Valores bimestres() {
		Long version = jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
		return new ConfiguracionEvaluacion.Valores(version, TipoPeriodo.BIMESTRE, 500, false, 7, "Castellano",
				"DRE Lima Metropolitana", "UGEL 03");
	}

	private String numero(int digitos) {
		long n = Math.floorMod(System.nanoTime(), (long) Math.pow(10, digitos));
		return String.format("%0" + digitos + "d", n);
	}

	private static LocalDate hoy() {
		return LocalDate.now(LIMA);
	}

	private static boolean enVentana() {
		LocalDate hoy = hoy();
		return !hoy.isBefore(LocalDate.of(hoy.getYear(), 1, 20)) && !hoy.isAfter(LocalDate.of(hoy.getYear(), 12, 15));
	}

	private static String texto(byte[] pdf) {
		try (var documento = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
		}
		catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private <T> T como(Usuario quien, Supplier<T> operacion) {
		return como(UsuariosDePrueba.autenticado(quien), operacion);
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

	/** En una transacción con la persona YA en sesión, que se revierte al terminar. */
	private <T> T enTransaccion(Usuario quien, Supplier<T> operacion) {
		return enTransaccion(UsuariosDePrueba.autenticado(quien), operacion);
	}

	private <T> T enTransaccion(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			TransactionCallback<T> callback = t -> {
				t.setRollbackOnly();
				return operacion.get();
			};
			return new TransactionTemplate(transacciones).execute(callback);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** En una transacción que se confirma (firmar exige una transacción abierta). */
	private <T> T enTransaccionComprometida(Usuario quien, Supplier<T> operacion) {
		return como(quien, () -> new TransactionTemplate(transacciones).execute(t -> operacion.get()));
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
			return "sin error";
		}
		catch (DataAccessException e) {
			StringBuilder texto = new StringBuilder();
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					texto.append(sql.getErrorCode()).append(' ').append(sql.getMessage()).append(' ');
				}
			}
			return texto.toString();
		}
	}
}
