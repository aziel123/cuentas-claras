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
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExonerarRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.HuellaConclusion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.Exoneraciones;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprint 10, tanda 2 (brief, sección 8, «MySQL real»; diseño, secciones 3.5, 3.6, 3.13 y 6.3) contra MySQL 8 real, con los
 * permisos mínimos de 02 y los triggers de 03. La nota la pone solo el docente del área (el tutor, en las transversales) en
 * un lote firmado con su sesión, con el periodo ABIERTO; la matrícula RESERVADA recibe nota (C1); no se edita ni se borra
 * (1142); la conclusión que exige la norma con el SHA-256 de la base; después del cierre, solo una corrección aprobada y
 * firmada por OTRA persona (que no la pidió ni registró la nota) con lo aprobado y el texto de su borrador (M8), o una nota
 * tardía en 48 horas; la exoneración la autoriza Dirección desde un periodo abierto (A3). Y la prueba de carga.
 * <p>
 * UN colegio para toda la clase (los 25 de la instalación ya los llenan las clases anteriores del job: como las del sprint
 * 10, tanda 1, sube el tope a 30). El año del alta (2027, en curso) pasa a cubrir hoy; su I bimestre (de hace 90 a hace 50
 * días) queda CERRADO con notas y su II bimestre ABIERTO. Las pruebas que cambian algo corren en una transacción que se
 * revierte; el escenario y el flujo con permisos mínimos quedan (el job de respaldo los usa).
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=30")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint10NotasMySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final String MOTIVO = "Carga horaria aprobada por Dirección para el año";

	private static final String MOTIVO_CORRECCION = "El examen recalificado muestra que logró lo esperado";

	private static final String CONCLUSION = "Logró resolver problemas con fracciones; se recomienda la práctica en casa.";

	private static final String INSERTAR_LOTE = "INSERT INTO lote_calificacion (colegio_id, periodo_id, seccion_id, area_id, "
			+ "secuencia, solicitud_id, sesion_id, filas, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, 1, "
			+ "NOW(6), ?, NOW(6))";

	private static final String INSERTAR_NOTA = "INSERT INTO calificacion (colegio_id, anio_escolar_id, periodo_id, seccion_id, "
			+ "matricula_id, alumno_id, competencia_id, nivel_logro, conclusion, conclusion_sha256, origen, inicial, reemplaza_id, "
			+ "lote_id, solicitud_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
			+ "NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private RegistroNotas registro;

	@Autowired
	private CorreccionesNota correcciones;

	@Autowired
	private Exoneraciones exoneraciones;

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
	private PlatformTransactionManager transacciones;

	/**
	 * El colegio de la clase: 5.° de Primaria con tres alumnos; Ana (MAT y CYT), Luis (COM), Tito (tutor, con PS: también las
	 * transversales), Rita (auxiliar), Rel (REL) y un director que también enseña (AYC). P1 CERRADO y P2 ABIERTO del año en
	 * curso; y un año siguiente PLANIFICADO con una matrícula RESERVADA (C1).
	 */
	private record Escuela(EscuelaConDocentesMySql.Escuela e, long anio, long seccion, long mateo, long lucas, long valeria,
			long alumnoMateo, long alumnoLucas, long alumnoValeria, Usuario tito, Usuario rita, Usuario rel, Usuario dirDocente,
			long p1, long p2, long siguiente, long seccionSiguiente, long reservada, long periodoSiguiente) {

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

		Usuario luis() {
			return e.otroDocente();
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
		EscuelaConDocentesMySql.Escuela e = EscuelaConDocentesMySql.crear(sufijo + "not", "20600000056", "987654712", alta,
				servicioUsuarios, usuarios, codificador, colegios, estructura, jdbc);
		LocalDate hoy = hoy();
		long anio = e.anioId();
		// El año del alta (2027, en curso) pasa a cubrir hoy (cc_app tiene UPDATE de anio_escolar; sin trigger).
		jdbc.update("UPDATE anio_escolar SET inicio_clases = ?, fin_clases = ? WHERE id = ?", hoy.minusDays(100),
				hoy.plusDays(100), anio);
		long seccion = como(e.equipo().administracion(), () -> estructura.crearSeccion(anio,
				new CrearSeccionRequest(Grado.PRIMARIA_5, "N" + sufijo.substring(sufijo.length() - 4))));
		long[] alumnosIds = new long[3];
		String[] nombres = { "Mateo", "Lucas", "Valeria" };
		for (int i = 0; i < 3; i++) {
			int n = i;
			alumnosIds[i] = como(e.equipo().administracion(), () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(
					"7" + n + numero(6), "Quispe", "Rojas", nombres[n], LocalDate.of(2016, 3, 3), "4" + n + numero(6), "Rojas",
					"Paz", "Rosa", null, "familia" + n + "." + sufijo + "@correo.pe", seccion)).alumnoId());
		}
		Usuario tito = docente(e, "tito.not." + sufijo);
		Usuario rita = docente(e, "rita.not." + sufijo);
		Usuario rel = docente(e, "rel.not." + sufijo);
		// Un director que también enseña, por el camino legítimo: Promotoría pide los roles y Dirección los aprueba.
		Usuario soloDocente = docente(e, "dirdoc.not." + sufijo);
		como(e.equipo().promotora(), () -> {
			servicioUsuarios.cambiarRoles(soloDocente.getId(), new pe.edu.virgenmaria.cuentasclaras.seguridad.dto
					.CambiarRolesRequest(java.util.EnumSet.of(Rol.DOCENTE, Rol.DIRECTOR), "Director que también enseña Arte"));
			return null;
		});
		Long rolesPedidos = jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio WHERE tipo = 'CAMBIO_ROLES' AND "
				+ "entidad_id = ? AND estado = 'PENDIENTE'", Long.class, soloDocente.getId());
		como(e.equipo().directora(), () -> {
			bandeja.aprobar(rolesPedidos, "Aprobado en la prueba de MySQL real");
			return null;
		});
		Usuario dirDocente = pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(e.colegioId(),
				() -> usuarios.findById(soloDocente.getId()).orElseThrow());
		assertThat(dirDocente.getRoles()).contains(Rol.DOCENTE, Rol.DIRECTOR);
		como(e.equipo().directora(), () -> configuraciones.guardar(anio, bimestres()));
		asignar(e, seccion, e.docente(), TipoAsignacion.DOCENTE, List.of(area("MAT"), area("CYT")));
		asignar(e, seccion, e.otroDocente(), TipoAsignacion.DOCENTE, List.of(area("COM")));
		asignar(e, seccion, tito, TipoAsignacion.TUTOR, List.of(area("PS")));
		asignar(e, seccion, rita, TipoAsignacion.AUXILIAR, List.of());
		asignar(e, seccion, rel, TipoAsignacion.DOCENTE, List.of(area("REL")));
		asignar(e, seccion, dirDocente, TipoAsignacion.DOCENTE, List.of(area("AYC")));
		long mateo = matricula(alumnosIds[0]);
		long lucas = matricula(alumnosIds[1]);
		long valeria = matricula(alumnosIds[2]);
		// P1: lo abre Dirección, Ana pone A en Matemática a los tres, Rel en Religión y el director-docente en Arte; se cierra.
		long p1 = como(e.equipo().directora(), () -> periodos.programar(anio, hoy.minusDays(90), hoy.minusDays(50)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(p1);
			return null;
		});
		guardar(e.docente(), seccion, "MAT", p1, List.of(mateo, lucas, valeria), "A", null);
		guardar(rel, seccion, "REL", p1, List.of(mateo, lucas, valeria), "A", null);
		guardar(dirDocente, seccion, "AYC", p1, List.of(mateo), "A", null);
		como(e.equipo().directora(), () -> {
			avance.cerrar(p1);
			return null;
		});
		// P2 ABIERTO con notas de Matemática de Mateo y Lucas.
		long p2 = como(e.equipo().directora(), () -> periodos.programar(anio, hoy.minusDays(40), hoy.plusDays(60)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(p2);
			return null;
		});
		guardar(e.docente(), seccion, "MAT", p2, List.of(mateo, lucas), "A", null);
		// C1: el año siguiente PLANIFICADO (que también cubre hoy), una sección, Ana con Matemática y la matrícula RESERVADA
		// de Mateo (la deja nacer así trg_matricula_nace: hay un año en curso).
		long siguiente = como(e.equipo().administracion(), () -> estructura.crearAnio(new CrearAnioEscolarRequest(2028,
				LocalDate.of(2028, 1, 2), LocalDate.of(2028, 12, 30), false)));
		jdbc.update("UPDATE anio_escolar SET inicio_clases = ?, fin_clases = ? WHERE id = ?", hoy.minusDays(100),
				hoy.plusDays(100), siguiente);
		long seccionSiguiente = como(e.equipo().administracion(), () -> estructura.crearSeccion(siguiente,
				new CrearSeccionRequest(Grado.PRIMARIA_5, "R" + sufijo.substring(sufijo.length() - 4))));
		jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'RESERVADA', NOW(6), ?, NOW(6))", e.colegioId(),
				alumnosIds[0], siguiente, seccionSiguiente, hoy, e.equipo().administracion().getNombreUsuario());
		long reservada = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ? AND anio_escolar_id = ?", Long.class,
				alumnosIds[0], siguiente);
		como(e.equipo().directora(), () -> configuraciones.guardar(siguiente, bimestres()));
		asignar(e, seccionSiguiente, e.docente(), TipoAsignacion.DOCENTE, List.of(area("MAT")));
		long periodoSiguiente = como(e.equipo().directora(), () -> periodos.programar(siguiente, hoy.minusDays(30),
				hoy.plusDays(60)));
		como(e.equipo().directora(), () -> {
			periodos.abrir(periodoSiguiente);
			return null;
		});
		compartida = new Escuela(e, anio, seccion, mateo, lucas, valeria, alumnosIds[0], alumnosIds[1], alumnosIds[2], tito,
				rita, rel, dirDocente, p1, p2, siguiente, seccionSiguiente, reservada, periodoSiguiente);
		return compartida;
	}

	// ------------------------------------------------------------------------------------------------ E1, E2, E3

	/** E1: con el periodo CERRADO, un lote sin solicitud (y su nota) se rechaza aunque lleve la firma del docente. */
	@Test
	void e1CambioDespuesDelCierreFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.ana(), () -> lote(s.ana(), s.p1(), s.seccion(), area("MAT"), null, true));
		assertThat(codigo).isEqualTo(1644);
		long vigente = vigente(s.mateo(), competencias("MAT").get(0), s.p1());
		long loteViejo = jdbc.queryForObject("SELECT lote_id FROM calificacion WHERE id = ?", Long.class, vigente);
		assertThat(enTransaccion(s.ana(), () -> nota(s, s.p1(), s.mateo(), s.alumnoMateo(), competencias("MAT").get(0), "AD",
				null, "CAMBIO_ABIERTO", null, vigente, loteViejo, null, s.ana()))).isEqualTo(1644);
	}

	/** E2: el docente de otra área de la misma sección no guarda un lote de Matemática. */
	@Test
	void e2LoteDeAreaAjenaFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.luis(), () -> lote(s.luis(), s.p2(), s.seccion(), area("MAT"), null, true)))
				.isEqualTo(1644);
		assertThat(enTransaccion(s.ana(), () -> lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)))
				.as("control: el docente del área").isNull();
	}

	/** E25: ni el auxiliar ni Dirección (sin asignación de área) guardan un lote. */
	@Test
	void e25AuxiliarYDireccionNoGuardanLotes() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.rita(), () -> lote(s.rita(), s.p2(), s.seccion(), area("MAT"), null, true)))
				.isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> lote(s.directora(), s.p2(), s.seccion(), area("MAT"), null, true)))
				.isEqualTo(1644);
	}

	/** E3: sin firma (o sin una sesión abierta suya) no hay lote. */
	@Test
	void e3LoteSinSesionOFirmaFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.ana(), () -> lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, false)))
				.isEqualTo(1644);
		Integer sinSesion = enTransaccion(s.ana(), () -> {
			int secuencia = siguienteSecuencia(s.p2(), s.seccion(), area("MAT"));
			firma.firmar(ClaveFirma.loteCalificacion(s.p2(), s.seccion(), area("MAT"), secuencia));
			Long ajena = sesion(s.luis());
			return codigoAl(() -> jdbc.update(INSERTAR_LOTE, s.colegio(), s.p2(), s.seccion(), area("MAT"), secuencia, null,
					ajena, s.ana().getNombreUsuario()));
		});
		assertThat(sinSesion).isEqualTo(1644);
	}

	/** E3: la nota de un lote es de quien guardó el lote. */
	@Test
	void e3NotaConOtroAutorQueElLoteFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.ana(), () -> {
			assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
			long lote = ultimoLote();
			return nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencias("MAT").get(0), "A", null, "REGISTRO", true,
					null, lote, null, s.luis());
		});
		assertThat(codigo).isEqualTo(1644);
	}

	/** E20: la secuencia del lote es la siguiente (no se salta ni se repite). */
	@Test
	void e20SecuenciaSaltadaFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.ana(), () -> {
			int saltada = siguienteSecuencia(s.p2(), s.seccion(), area("MAT")) + 1;
			firma.firmar(ClaveFirma.loteCalificacion(s.p2(), s.seccion(), area("MAT"), saltada));
			return codigoAl(() -> jdbc.update(INSERTAR_LOTE, s.colegio(), s.p2(), s.seccion(), area("MAT"), saltada, null,
					sesion(s.ana()), s.ana().getNombreUsuario()));
		});
		assertThat(codigo).isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ E7, E8

	/** E7: las notas, sus lotes, las exoneraciones y los borradores no se editan ni se borran (1142). */
	@Test
	void e7NotasNoSeEditanNiSeBorran() {
		escuela();
		for (String tabla : List.of("calificacion", "lote_calificacion", "exoneracion_area", "borrador_conclusion")) {
			assertThat(codigoAl(() -> jdbc.update("UPDATE " + tabla + " SET version = version WHERE 1 = 0"))).as(tabla)
					.isEqualTo(1142);
			assertThat(codigoAl(() -> jdbc.update("DELETE FROM " + tabla + " WHERE 1 = 0"))).as(tabla).isEqualTo(1142);
		}
		assertThat(codigoAl(() -> jdbc.update("UPDATE calificacion SET nivel_logro = 'AD' WHERE 1 = 0"))).isEqualTo(1142);
	}

	/**
	 * E8: una nota tiene UNA sucesora. Reemplazar una que ya fue reemplazada se rechaza (la vigente es la que nadie
	 * reemplaza: el trigger responde antes que {@code uk_calificacion_reemplaza}, 1062).
	 */
	@Test
	void e8DosSucesorasFallan() {
		Escuela s = escuela();
		Long competencia = competencias("MAT").get(0);
		Integer segunda = enTransaccion(s.ana(), () -> {
			long vigente = vigente(s.mateo(), competencia, s.p2());
			assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
			long lote = ultimoLote();
			assertThat(nota(s, s.p2(), s.mateo(), s.alumnoMateo(), competencia, "AD", null, "CAMBIO_ABIERTO", null, vigente,
					lote, null, s.ana())).isNull();
			return nota(s, s.p2(), s.mateo(), s.alumnoMateo(), competencia, "B", CONCLUSION, "CAMBIO_ABIERTO", null, vigente,
					lote, null, s.ana());
		});
		assertThat(segunda).isIn(1644, 1062);
	}

	// ------------------------------------------------------------------------------------------------ E18, E19, C1

	/** E18: en Primaria, B y C exigen la conclusión; A no. La base calcula su SHA-256 (con tildes y eñe, igual que Java). */
	@Test
	void e18ConclusionExigidaYHuellaDeLaBase() {
		Escuela s = escuela();
		Long competencia = competencias("MAT").get(0);
		for (String nivel : List.of("B", "C")) {
			Integer codigo = enTransaccion(s.ana(), () -> {
				assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
				return nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencia, nivel, null, "REGISTRO", true, null,
						ultimoLote(), null, s.ana());
			});
			assertThat(codigo).as(nivel).isEqualTo(1644);
		}
		String conTildes = "Logró comprender el ñandú y la canción; año próximo: más lectura crítica.";
		String huella = enTransaccion(s.ana(), () -> {
			assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
			assertThat(nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencia, "B", conTildes, "REGISTRO", true, null,
					ultimoLote(), null, s.ana())).isNull();
			return jdbc.queryForObject("SELECT conclusion_sha256 FROM calificacion WHERE id = (SELECT MAX(id) FROM "
					+ "calificacion)", String.class);
		});
		assertThat(huella).isEqualTo(HuellaConclusion.de(conTildes));
		assertThat(jdbc.queryForList("SELECT CONCAT(nivel, ':', valor) FROM escala_logro WHERE exige_conclusion ORDER BY "
				+ "nivel, valor", String.class)).contains("INICIAL:A", "PRIMARIA:B", "SECUNDARIA:C")
				.doesNotContain("PRIMARIA:A", "SECUNDARIA:B");
	}

	/** E19: una matrícula RETIRADA no recibe nota. */
	@Test
	void e19MatriculaRetiradaFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.ana(), () -> {
			jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", hoy(), s.valeria());
			assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
			return nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencias("MAT").get(0), "A", null, "REGISTRO", true,
					null, ultimoLote(), null, s.ana());
		});
		assertThat(codigo).isEqualTo(1644);
	}

	/**
	 * QA del sprint 10 (QA-1): un alumno trasladado a mitad de periodo. Su nota de la sección anterior sigue siendo la
	 * vigente y el docente de la sección nueva la cambia: la base acepta el CAMBIO_ABIERTO en la sección del lote y de la
	 * matrícula, reemplazando la vigente de la otra sección (la cadena es de la matrícula). Antes la aplicación intentaba una
	 * nota inicial y chocaba con uk_calificacion_inicial.
	 */
	@Test
	void qaTrasladoAMitadDePeriodoContinuaLaCadenaEnLaSeccionNueva() {
		Escuela s = escuela();
		long otra = como(s.administracion(), () -> estructura.crearSeccion(s.anio(),
				new CrearSeccionRequest(Grado.PRIMARIA_5, "T" + sufijo.substring(sufijo.length() - 4))));
		asignar(s.e(), otra, s.ana(), TipoAsignacion.DOCENTE, List.of(area("MAT")));
		long competencia = competencias("MAT").get(0);
		long anterior = jdbc.queryForObject("SELECT c.id FROM calificacion c WHERE c.periodo_id = ? AND c.matricula_id = ? "
				+ "AND c.competencia_id = ? AND NOT EXISTS (SELECT 1 FROM calificacion x WHERE x.reemplaza_id = c.id)",
				Long.class, s.p2(), s.lucas(), competencia);
		Long[] nueva = new Long[2];
		Integer cambios = enTransaccion(s.ana(), () -> {
			jdbc.update("UPDATE matricula SET seccion_id = ? WHERE id = ?", otra, s.lucas());
			int r = registro.guardar(otra, area("MAT"), s.p2(), new NotasRequest(List.of(new NotaPedida(s.lucas(),
					competencia, "AD", null)), null)).cambios();
			nueva[0] = jdbc.queryForObject("SELECT seccion_id FROM calificacion WHERE reemplaza_id = ?", Long.class,
					anterior);
			nueva[1] = jdbc.queryForObject("SELECT COUNT(*) FROM calificacion WHERE reemplaza_id = ? AND origen = "
					+ "'CAMBIO_ABIERTO' AND nivel_logro = 'AD'", Long.class, anterior);
			return r;
		});
		assertThat(cambios).isEqualTo(1);
		assertThat(nueva[0]).isEqualTo(otra);
		assertThat(nueva[1]).isEqualTo(1L);
	}

	/** C1 (crítico): la matrícula RESERVADA (impaga, del año siguiente) recibe nota igual, por la aplicación y la base. */
	@Test
	void c1NotaAMatriculaReservadaPasa() {
		Escuela s = escuela();
		assertThat(jdbc.queryForObject("SELECT estado FROM matricula WHERE id = ?", String.class, s.reservada()))
				.isEqualTo("RESERVADA");
		Integer filas = enTransaccion(s.ana(), () -> {
			List<NotaPedida> notas = competencias("MAT").stream().map(c -> new NotaPedida(s.reservada(), c, "A", null))
					.toList();
			return registro.guardar(s.seccionSiguiente(), area("MAT"), s.periodoSiguiente(), new NotasRequest(notas, null))
					.nuevas();
		});
		assertThat(filas).isEqualTo(competencias("MAT").size());
	}

	// ------------------------------------------------------------------------------------------------ E4, E5, E6, M8

	/** E4: la corrección la aprueba OTRA persona: la que la pidió no la firma como aprobada (cc_solicitud_firmada). */
	@Test
	void e4QuienPideNoApruebaFalla() {
		Escuela s = escuela();
		Long nota = vigente(s.mateo(), competencias("MAT").get(1), s.p1());
		// La base ni siquiera deja aprobarla a nombre de quien la pidió (ck_solicitud_cambio_resolucion: 3819); y una nota
		// con la solicitud todavía PENDIENTE la rechaza trg_calificacion_nace (1644).
		Integer alAprobar = enTransaccion(s.directora(), () -> {
			Long solicitud = correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION);
			firma.firmar(ClaveFirma.solicitud(solicitud, EstadoSolicitud.APROBADA));
			return codigoAl(() -> jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, "
					+ "resuelto_por = ?, resuelto_en = NOW(6), version = version + 1 WHERE id = ?",
					s.directora().getNombreUsuario(), solicitud));
		});
		assertThat(alAprobar).isEqualTo(3819);
		Integer codigo = enTransaccion(s.directora(), () -> {
			Long solicitud = correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION);
			return nota(s, s.p1(), s.mateo(), s.alumnoMateo(), competencias("MAT").get(1), "AD", null, "CORRECCION_APROBADA",
					null, nota, null, solicitud, s.directora());
		});
		assertThat(codigo).isEqualTo(1644);
	}

	/** E5: quien registró la nota que se reemplaza (un director que también enseña) no aprueba su corrección. */
	@Test
	void e5AutorDeLaNotaNoApruebaFalla() {
		Escuela s = escuela();
		Long nota = vigente(s.mateo(), competencias("AYC").get(0), s.p1());
		Long solicitud = enTransaccionComprometida(s.directora(), () -> correcciones.pedir(nota, "AD", null,
				MOTIVO_CORRECCION));
		Integer codigo = enTransaccion(s.dirDocente(), () -> {
			aprobarCrudo(solicitud, s.dirDocente());
			return nota(s, s.p1(), s.mateo(), s.alumnoMateo(), competencias("AYC").get(0), "AD", null, "CORRECCION_APROBADA",
					null, nota, null, solicitud, s.dirDocente());
		});
		assertThat(codigo).isEqualTo(1644);
		// La aplicación tampoco la deja: es un involucrado (la bandeja lo rechaza antes).
		Integer porLaBandeja = enTransaccion(s.dirDocente(), () -> {
			try {
				bandeja.aprobar(solicitud, null);
				return null;
			}
			catch (pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException ex) {
				return -1;
			}
		});
		assertThat(porLaBandeja).isEqualTo(-1);
	}

	/** E6 y M8: entra exactamente lo aprobado: el nivel, la huella y el texto del borrador de esa misma nota. */
	@Test
	void e6LoAprobadoEsLoQueEntraYM8ConclusionDistintaAlBorradorFalla() {
		Escuela s = escuela();
		Long competencia = competencias("MAT").get(2);
		Long nota = vigente(s.lucas(), competencia, s.p1());
		Long solicitud = enTransaccionComprometida(s.ana(), () -> correcciones.pedir(nota, "B", CONCLUSION,
				MOTIVO_CORRECCION));
		String datos = jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE id = ?", String.class, solicitud);
		assertThat(datos).doesNotContain("fracciones").contains(HuellaConclusion.de(CONCLUSION));
		for (String[] caso : new String[][] { { "C", CONCLUSION }, { "B", CONCLUSION + " Además." } }) {
			Integer codigo = enTransaccion(s.promotora(), () -> {
				aprobarCrudo(solicitud, s.promotora());
				return nota(s, s.p1(), s.lucas(), s.alumnoLucas(), competencia, caso[0], caso[1], "CORRECCION_APROBADA", null,
						nota, null, solicitud, s.promotora());
			});
			assertThat(codigo).as(caso[0] + " / " + caso[1]).isEqualTo(1644);
		}
		Integer control = enTransaccion(s.promotora(), () -> {
			aprobarCrudo(solicitud, s.promotora());
			return nota(s, s.p1(), s.lucas(), s.alumnoLucas(), competencia, "B", CONCLUSION, "CORRECCION_APROBADA", null, nota,
					null, solicitud, s.promotora());
		});
		assertThat(control).as("control: lo aprobado, con el texto de su borrador").isNull();
	}

	/** M8: el borrador lo escribe el docente del área de esa nota (u otro de Dirección), nunca otro docente. */
	@Test
	void m8BorradorDeOtroDocenteFalla() {
		Escuela s = escuela();
		Long nota = vigente(s.mateo(), competencias("MAT").get(0), s.p1());
		assertThat(enTransaccion(s.luis(), () -> borrador(nota, s.luis()))).isEqualTo(1644);
		assertThat(enTransaccion(s.ana(), () -> borrador(nota, s.ana()))).as("control").isNull();
		assertThat(enTransaccion(s.directora(), () -> borrador(nota, s.directora()))).as("Dirección").isNull();
	}

	/** M8: con el periodo abierto no hay borrador (la nota se cambia en la hoja). */
	@Test
	void m8BorradorConPeriodoAbiertoFalla() {
		Escuela s = escuela();
		Long nota = vigente(s.mateo(), competencias("MAT").get(0), s.p2());
		assertThat(enTransaccion(s.ana(), () -> borrador(nota, s.ana()))).isEqualTo(1644);
	}

	/** «Sin nota» en una corrección: la solicitud no lleva la clave nivel y la fila entra con nivel NULL (JSON sin nivel). */
	@Test
	void correccionSinNotaConJsonSinNivel() {
		Escuela s = escuela();
		Long competencia = competencias("MAT").get(3);
		Long nota = vigente(s.valeria(), competencia, s.p1());
		Integer codigo = enTransaccion(s.promotora(), () -> {
			UsuariosDePrueba.iniciarSesion(s.ana());
			Long solicitud = correcciones.pedir(nota, "SIN", null, MOTIVO_CORRECCION);
			UsuariosDePrueba.iniciarSesion(s.promotora());
			assertThat(jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
					.doesNotContain("nivel");
			aprobarCrudo(solicitud, s.promotora());
			return nota(s, s.p1(), s.valeria(), s.alumnoValeria(), competencia, null, null, "CORRECCION_APROBADA", null, nota,
					null, solicitud, s.promotora());
		});
		assertThat(codigo).isNull();
	}

	// ------------------------------------------------------------------------------------------------ E21, E22, A3

	/** E21: la exoneración la autoriza Dirección con su firma; ni Administración ni un docente. */
	@Test
	void e21ExoneracionSinDireccionOFirmaFalla() {
		Escuela s = escuela();
		for (Usuario quien : List.of(s.administracion(), s.ana())) {
			assertThat(enTransaccion(quien, () -> exoneracion(s, s.lucas(), "EF", s.p2(), quien, true))).as(quien
					.getNombreUsuario()).isEqualTo(1644);
		}
		assertThat(enTransaccion(s.directora(), () -> exoneracion(s, s.lucas(), "EF", s.p2(), s.directora(), false)))
				.as("sin firma").isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> exoneracion(s, s.lucas(), "MAT", s.p2(), s.directora(), true)))
				.as("un área no exonerable").isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> exoneracion(s, s.lucas(), "EF", s.p2(), s.directora(), true)))
				.as("control").isNull();
	}

	/** A3: la exoneración rige desde un periodo ABIERTO: desde uno CERRADO se rechaza. */
	@Test
	void a3ExoneracionEnPeriodoCerradoFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.directora(), () -> exoneracion(s, s.lucas(), "REL", s.p1(), s.directora(), true)))
				.isEqualTo(1644);
	}

	/** A3 y E22: exonerado desde el II bimestre, su nota del I queda y en el II el área no se califica. */
	@Test
	void a3NotaAnteriorALaExoneracionSeConservaYE22NotaEnAreaExoneradaFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.rel(), () -> {
			UsuariosDePrueba.iniciarSesion(s.directora());
			assertThat(exoneracion(s, s.lucas(), "REL", s.p2(), s.directora(), true)).isNull();
			UsuariosDePrueba.iniciarSesion(s.rel());
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calificacion WHERE matricula_id = ? AND periodo_id = ? AND "
					+ "competencia_id IN (SELECT id FROM competencia WHERE area_id = ?)", Long.class, s.lucas(), s.p1(),
					area("REL"))).as("las notas del I bimestre quedan").isPositive();
			assertThat(lote(s.rel(), s.p2(), s.seccion(), area("REL"), null, true)).isNull();
			return nota(s, s.p2(), s.lucas(), s.alumnoLucas(), competencias("REL").get(0), "A", null, "REGISTRO", true, null,
					ultimoLote(), null, s.rel());
		});
		assertThat(codigo).isEqualTo(1644);
	}

	// ------------------------------------------------------------------------------------------------ E23 (tardía)

	/** E23: la nota tardía va con SU solicitud aprobada por otra persona, del mismo periodo y área, por quien la pidió. */
	@Test
	void e23NotaTardiaConSuSolicitud() {
		Escuela s = escuela();
		Long solicitud = enTransaccionComprometida(s.ana(), () -> correcciones.pedirTardia(s.seccion(), area("CYT"), s.p1(),
				MOTIVO_CORRECCION));
		Integer sinAprobar = enTransaccion(s.ana(), () -> lote(s.ana(), s.p1(), s.seccion(), area("CYT"), solicitud, true));
		assertThat(sinAprobar).as("sin aprobar").isEqualTo(1644);
		Integer otraArea = enTransaccion(s.promotora(), () -> {
			aprobarCrudo(solicitud, s.promotora());
			UsuariosDePrueba.iniciarSesion(s.ana());
			return lote(s.ana(), s.p1(), s.seccion(), area("MAT"), solicitud, true);
		});
		assertThat(otraArea).as("de otra área").isEqualTo(1644);
		Integer otroDocente = enTransaccion(s.promotora(), () -> {
			aprobarCrudo(solicitud, s.promotora());
			UsuariosDePrueba.iniciarSesion(s.luis());
			return lote(s.luis(), s.p1(), s.seccion(), area("CYT"), solicitud, true);
		});
		assertThat(otroDocente).as("por otro docente").isEqualTo(1644);
		Integer legitimo = enTransaccion(s.promotora(), () -> {
			aprobarCrudo(solicitud, s.promotora());
			UsuariosDePrueba.iniciarSesion(s.ana());
			assertThat(lote(s.ana(), s.p1(), s.seccion(), area("CYT"), solicitud, true)).isNull();
			return nota(s, s.p1(), s.mateo(), s.alumnoMateo(), competencias("CYT").get(0), "A", null, "REGISTRO_TARDIO", true,
					null, ultimoLote(), null, s.ana());
		});
		assertThat(legitimo).as("control").isNull();
	}

	// ------------------------------------------------------------------------------------------------ CHECK con NULL

	/** Lección de V32 a V35: un CHECK que evalúa NULL pasa; cada caso NULL de la sección 3 del brief se rechaza (3819). */
	@Test
	void checksConNull() {
		Escuela s = escuela();
		Long competencia = competencias("MAT").get(0);
		Object[][] casos = {
				// REGISTRO sin nivel; REGISTRO sin inicial; CAMBIO_ABIERTO sin reemplaza_id; conclusión de 5 caracteres.
				{ null, null, "REGISTRO", true, null },
				{ "A", null, "REGISTRO", null, null },
				{ "A", null, "CAMBIO_ABIERTO", null, null },
				{ "A", "corta", "REGISTRO", true, null } };
		for (Object[] caso : casos) {
			Integer codigo = enTransaccion(s.ana(), () -> {
				assertThat(lote(s.ana(), s.p2(), s.seccion(), area("MAT"), null, true)).isNull();
				return nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencia, (String) caso[0], (String) caso[1],
						(String) caso[2], (Boolean) caso[3], (Long) caso[4], ultimoLote(), null, s.ana());
			});
			assertThat(codigo).as(java.util.Arrays.toString(caso)).isIn(3819, 1644);
		}
		// Sin lote ni solicitud: el CHECK de los orígenes (o el trigger) la rechaza.
		assertThat(enTransaccion(s.ana(), () -> nota(s, s.p2(), s.valeria(), s.alumnoValeria(), competencia, "A", null,
				"REGISTRO", true, null, null, null, s.ana()))).isIn(3819, 1644);
		// Un borrador de menos de 10 caracteres (el trigger lo deja pasar: lo rechaza el CHECK).
		Long cerrada = vigente(s.mateo(), competencia, s.p1());
		assertThat(enTransaccion(s.ana(), () -> codigoAl(() -> jdbc.update("INSERT INTO borrador_conclusion (colegio_id, "
				+ "calificacion_id, texto, sha256, sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, '  corto ', "
				+ "?, ?, NOW(6), ?, NOW(6))", s.colegio(), cerrada, "0".repeat(64), sesion(s.ana()),
				s.ana().getNombreUsuario())))).isEqualTo(3819);
	}

	// ------------------------------------------------------------------------------------------------ Flujo y carga

	/**
	 * El flujo con los permisos mínimos de cc_app (y deja sus datos para el job de respaldo): configurar, abrir, guardar un
	 * lote, cambiar con el periodo abierto, cerrar, pedir una corrección con borrador y aprobarla con otra persona, pedir y
	 * registrar una tardía, exonerar.
	 */
	@Test
	void flujoNotasConPermisosMinimos() {
		Escuela s = escuela();
		// Cambio con el periodo abierto (P2): Mateo pasa de A a AD en la primera competencia.
		guardar(s.ana(), s.seccion(), "MAT", s.p2(), List.of(s.mateo()), "AD", null);
		// Corrección con borrador en P1, aprobada por Promotoría.
		Long nota = vigente(s.valeria(), competencias("MAT").get(0), s.p1());
		Long solicitud = como(s.ana(), () -> correcciones.pedir(nota, "B", CONCLUSION, MOTIVO_CORRECCION));
		como(s.promotora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		Long corregida = vigente(s.valeria(), competencias("MAT").get(0), s.p1());
		assertThat(jdbc.queryForMap("SELECT nivel_logro, conclusion, origen, creado_por FROM calificacion WHERE id = ?",
				corregida)).containsEntry("nivel_logro", "B").containsEntry("conclusion", CONCLUSION)
				.containsEntry("origen", "CORRECCION_APROBADA")
				.containsEntry("creado_por", s.promotora().getNombreUsuario());
		// Tardía de COM (Luis no registró en P1): la pide, la aprueba Dirección y la registra.
		Long tardia = como(s.luis(), () -> correcciones.pedirTardia(s.seccion(), area("COM"), s.p1(), MOTIVO_CORRECCION));
		Long asignacion = jdbc.queryForObject("SELECT entidad_id FROM solicitud_cambio WHERE id = ?", Long.class, tardia);
		assertThat(asignacion).isNotNull();
		como(s.directora(), () -> {
			bandeja.aprobar(tardia, null);
			return null;
		});
		List<NotaPedida> pendientes = competencias("COM").stream().map(c -> new NotaPedida(s.mateo(), c, "A", null)).toList();
		assertThat(como(s.luis(), () -> registro.guardar(s.seccion(), area("COM"), s.p1(), new NotasRequest(pendientes,
				tardia))).nuevas()).isEqualTo(pendientes.size());
		// Exoneración de Valeria en Educación Física desde P2.
		como(s.directora(), () -> exoneraciones.exonerar(new ExonerarRequest(s.valeria(), area("EF"), "EXP-" + sufijo,
				"Escrito del padre por indicación médica")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calificacion WHERE colegio_id = ?", Long.class, s.colegio()))
				.isGreaterThanOrEqualTo(12);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exoneracion_area WHERE colegio_id = ?", Long.class,
				s.colegio())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM borrador_conclusion WHERE colegio_id = ?", Long.class,
				s.colegio())).isGreaterThanOrEqualTo(1);
	}

	/**
	 * Carga (riesgo 3): 40 docentes (5 secciones de 30 alumnos, 8 áreas cada una) guardan A LA VEZ el lote de su área para los
	 * 30 alumnos (Matemática: 30 × 4 = 120 filas con su trigger). Un lote solo tarda menos de 2 s y los 40 a la vez, menos
	 * de 4 s (la cadena única de la bitácora serializa el final de cada guardado).
	 */
	@Test
	void cargaCuarentaDocentesGuardanALaVez() throws Exception {
		Escuela s = escuela();
		List<String> areas = List.of("PS", "EF", "COM", "AYC", "ING", "MAT", "CYT", "REL");
		List<Usuario> docentes = new ArrayList<>();
		List<long[]> trabajo = new ArrayList<>();
		List<List<Long>> matriculasPorSeccion = new ArrayList<>();
		String base = numero(3);
		for (int sec = 0; sec < 5; sec++) {
			int n = sec;
			long seccion = como(s.administracion(), () -> estructura.crearSeccion(s.anio(), new CrearSeccionRequest(
					Grado.PRIMARIA_5, "C" + n + sufijo.substring(sufijo.length() - 3))));
			List<Long> matriculas = new ArrayList<>();
			for (int a = 0; a < 30; a++) {
				int k = n * 30 + a;
				String letras = "" + (char) ('a' + k / 26 % 26) + (char) ('a' + k % 26);
				long alumno = como(s.administracion(), () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(
						"8" + base + String.format("%04d", k), "Carga", "Prueba", "Alumn" + letras, LocalDate.of(2016, 5, 5),
						"5" + base + String.format("%04d", k),
						"Carga", "Padre", "Padr" + letras, null, "carga" + k + "." + sufijo + "@correo.pe", seccion))
						.alumnoId());
				matriculas.add(matricula(alumno));
			}
			matriculasPorSeccion.add(matriculas);
			for (String codigo : areas) {
				Usuario d = docente(s.e(), "carga" + n + codigo.toLowerCase() + "." + sufijo);
				asignar(s.e(), seccion, d, TipoAsignacion.DOCENTE, List.of(area(codigo)));
				docentes.add(d);
				trabajo.add(new long[] { seccion, area(codigo), n });
			}
		}
		assertThat(docentes).hasSize(40);
		// Las sesiones de la base de cada docente, antes de largar (la firma de cada lote las usa).
		for (Usuario d : docentes) {
			como(d, () -> tokens.actual().orElseThrow());
		}
		ExecutorService hilos = Executors.newFixedThreadPool(40);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<long[]>> resultados = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			Usuario d = docentes.get(i);
			long[] t = trabajo.get(i);
			List<NotaPedida> notas = new ArrayList<>();
			for (Long m : matriculasPorSeccion.get((int) t[2])) {
				for (Long c : competenciasDe(t[1])) {
					notas.add(new NotaPedida(m, c, "A", null));
				}
			}
			resultados.add(hilos.submit(() -> {
				largada.await();
				UsuariosDePrueba.iniciarSesion(d);
				try {
					long inicio = System.nanoTime();
					int filas = registro.guardar(t[0], t[1], s.p2(), new NotasRequest(notas, null)).nuevas();
					return new long[] { (System.nanoTime() - inicio) / 1_000_000, filas };
				}
				finally {
					SecurityContextHolder.clearContext();
				}
			}));
		}
		largada.countDown();
		long maximo = 0;
		long filas = 0;
		List<Long> tiempos = new ArrayList<>();
		for (Future<long[]> r : resultados) {
			long[] valor = r.get(5, TimeUnit.MINUTES);
			maximo = Math.max(maximo, valor[0]);
			filas += valor[1];
			tiempos.add(valor[0]);
		}
		hilos.shutdown();
		tiempos.sort(null);
		// Línea base: el mismo docente de Matemática de la primera sección, solo (sin los otros 39), cambia sus 120 notas.
		int mat = areas.indexOf("MAT");
		long[] t0 = trabajo.get(mat);
		List<NotaPedida> cambio = new ArrayList<>();
		for (Long m : matriculasPorSeccion.get(0)) {
			for (Long c : competenciasDe(t0[1])) {
				cambio.add(new NotaPedida(m, c, "AD", null));
			}
		}
		long solo = System.nanoTime();
		como(docentes.get(mat), () -> registro.guardar(t0[0], t0[1], s.p2(), new NotasRequest(cambio, null)));
		long msSolo = (System.nanoTime() - solo) / 1_000_000;
		org.slf4j.LoggerFactory.getLogger(getClass()).info("Carga de notas: un lote de {} filas, solo, en {} ms",
				cambio.size(), msSolo);
		org.slf4j.LoggerFactory.getLogger(getClass()).info("Carga de notas: {} filas en 40 lotes; tiempos (ms): {}", filas,
				tiempos);
		assertThat(filas).as("todas las filas, cada una con su trigger").isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM "
				+ "calificacion WHERE origen = 'REGISTRO' AND periodo_id = ? AND seccion_id IN (SELECT id FROM seccion WHERE nombre LIKE 'C%' AND "
				+ "anio_escolar_id = ?)", Long.class, s.p2(), s.anio()));
		// El brief pide menos de 2 s por guardado. Un lote solo (120 filas) tarda ~120 ms en el CI; con los 40 a la vez en un
		// runner de 4 vCPU (aplicación y MySQL en la misma máquina) el último espera la cadena de la bitácora, que es una sola
		// para toda la instalación y serializa el final de cada transacción (como los cobros): ~2,2 s. Se exige el lote solo
		// en menos de 2 s y los 40 a la vez en menos de 4 s (decisión 191, confirmada por el usuario; riesgo 3).
		assertThat(msSolo).as("un lote de 120 filas, solo, en milisegundos").isLessThan(2000);
		assertThat(maximo).as("el guardado más lento de los 40 a la vez, en milisegundos").isLessThan(4000);
	}

	// ------------------------------------------------------------------------------------------------ Ayudas

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

	/** En la transacción de quien está en sesión: un lote del área con o sin la firma, con su sesión. */
	private Integer lote(Usuario quien, long periodo, long seccion, long area, Long solicitud, boolean conFirma) {
		int secuencia = siguienteSecuencia(periodo, seccion, area);
		if (conFirma) {
			firma.firmar(ClaveFirma.loteCalificacion(periodo, seccion, area, secuencia));
		}
		Long sesion = sesion(quien);
		return codigoAl(() -> jdbc.update(INSERTAR_LOTE, compartida.colegio(), periodo, seccion, area, secuencia, solicitud,
				sesion, quien.getNombreUsuario()));
	}

	private Integer nota(Escuela s, long periodo, long matricula, long alumno, long competencia, String nivel,
			String conclusion, String origen, Boolean inicial, Long reemplaza, Long lote, Long solicitud, Usuario autor) {
		String sha = conclusion == null ? null : HuellaConclusion.de(conclusion);
		long anio = jdbc.queryForObject("SELECT anio_escolar_id FROM periodo_evaluacion WHERE id = ?", Long.class, periodo);
		long seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE id = ?", Long.class, matricula);
		return codigoAl(() -> jdbc.update(INSERTAR_NOTA, s.colegio(), anio, periodo, seccion, matricula, alumno, competencia,
				nivel, conclusion, sha, origen, inicial, reemplaza, lote, solicitud, autor.getNombreUsuario()));
	}

	private Integer borrador(Long calificacion, Usuario quien) {
		return codigoAl(() -> jdbc.update("INSERT INTO borrador_conclusion (colegio_id, calificacion_id, texto, sha256, "
				+ "sesion_id, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, NOW(6), ?, NOW(6))",
				compartida.colegio(), calificacion, CONCLUSION, HuellaConclusion.de(CONCLUSION), sesion(quien),
				quien.getNombreUsuario()));
	}

	private Integer exoneracion(Escuela s, long matricula, String codigoArea, long desde, Usuario quien, boolean conFirma) {
		if (conFirma) {
			firma.firmar(ClaveFirma.exoneracionArea(matricula, area(codigoArea)));
		}
		return codigoAl(() -> jdbc.update("INSERT INTO exoneracion_area (colegio_id, anio_escolar_id, matricula_id, area_id, "
				+ "desde_periodo_id, documento, motivo, autorizado_por, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, "
				+ "?, 'EXP-CI', 'Escrito del padre de la prueba', ?, NOW(6), ?, NOW(6))", s.colegio(), s.anio(), matricula,
				area(codigoArea), desde, quien.getNombreUsuario(), quien.getNombreUsuario()));
	}

	/** Aprueba la solicitud por SQL a nombre de {@code quien} con SU firma (sin la bandeja ni su manejador). */
	private void aprobarCrudo(Long solicitud, Usuario quien) {
		UsuariosDePrueba.iniciarSesion(quien);
		firma.firmar(ClaveFirma.solicitud(solicitud, EstadoSolicitud.APROBADA));
		jdbc.update("UPDATE solicitud_cambio SET estado = 'APROBADA', pendiente = NULL, resuelto_por = ?, resuelto_en = NOW(6), "
				+ "version = version + 1 WHERE id = ?", quien.getNombreUsuario(), solicitud);
	}

	private Long sesion(Usuario quien) {
		org.springframework.security.core.Authentication antes = SecurityContextHolder.getContext().getAuthentication();
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return tokens.actual().map(SesionAbierta::sesionId).orElseThrow();
		}
		finally {
			SecurityContextHolder.getContext().setAuthentication(antes);
		}
	}

	private int siguienteSecuencia(long periodo, long seccion, long area) {
		return jdbc.queryForObject("SELECT COALESCE(MAX(secuencia), 0) + 1 FROM lote_calificacion WHERE periodo_id = ? AND "
				+ "seccion_id = ? AND area_id = ?", Integer.class, periodo, seccion, area);
	}

	private long ultimoLote() {
		return jdbc.queryForObject("SELECT MAX(id) FROM lote_calificacion WHERE colegio_id = ?", Long.class,
				compartida.colegio());
	}

	private long vigente(long matricula, long competencia, long periodo) {
		return jdbc.queryForObject("SELECT c.id FROM calificacion c WHERE c.matricula_id = ? AND c.competencia_id = ? AND "
				+ "c.periodo_id = ? AND NOT EXISTS (SELECT 1 FROM calificacion x WHERE x.reemplaza_id = c.id)", Long.class,
				matricula, competencia, periodo);
	}

	private long matricula(long alumno) {
		return jdbc.queryForObject("SELECT MIN(id) FROM matricula WHERE alumno_id = ?", Long.class, alumno);
	}

	private long area(String codigo) {
		return jdbc.queryForObject("SELECT a.id FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id WHERE "
				+ "v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = ?", Long.class, codigo);
	}

	private List<Long> competencias(String codigoArea) {
		return competenciasDe(area(codigoArea));
	}

	private List<Long> competenciasDe(long area) {
		return jdbc.queryForList("SELECT id FROM competencia WHERE area_id = ? AND (solo_grados IS NULL OR "
				+ "FIND_IN_SET('PRIMARIA_5', solo_grados) > 0) ORDER BY orden", Long.class, area);
	}

	private ConfiguracionEvaluacion.Valores bimestres() {
		Long version = jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
		return new ConfiguracionEvaluacion.Valores(version, TipoPeriodo.BIMESTRE, 500, false, 7, "Castellano", null, null);
	}

	private String numero(int digitos) {
		long n = Math.floorMod(System.nanoTime(), (long) Math.pow(10, digitos));
		return String.format("%0" + digitos + "d", n);
	}

	private static LocalDate hoy() {
		return LocalDate.now(LIMA);
	}

	/** Las fechas relativas a hoy necesitan unos meses del año antes y después (como la tanda 1). */
	private static boolean enVentana() {
		LocalDate hoy = hoy();
		return !hoy.isBefore(LocalDate.of(hoy.getYear(), 1, 20)) && !hoy.isAfter(LocalDate.of(hoy.getYear(), 12, 15));
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

	/** En una transacción con la persona YA en sesión, que se revierte al terminar. */
	private <T> T enTransaccion(Usuario quien, Supplier<T> operacion) {
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

	/** Igual, pero que queda (una solicitud que las pruebas siguientes de la clase usan en sus transacciones). */
	private <T> T enTransaccionComprometida(Usuario quien, Supplier<T> operacion) {
		return como(quien, operacion);
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
}
