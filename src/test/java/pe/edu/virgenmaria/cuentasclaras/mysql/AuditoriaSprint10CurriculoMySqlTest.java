package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.proceso.AperturaPeriodos;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprint 10, tanda 1 (brief, sección 8, «MySQL real»; diseño, secciones 3.2 a 3.4, 3.13 y 9) contra MySQL 8 real, con los
 * permisos mínimos de 02 y los triggers de 03. Con la clave de {@code cc_app} o de {@code cc_sistema}: el catálogo no se
 * escribe (E12, 1142); la evaluación del año la guarda solo Dirección con su firma, la versión de a una, y con un periodo
 * abierto no cambian el tipo ni los días de cierre (E11, M7); un periodo nace PROGRAMADO, dentro del año, en orden y sin
 * solaparse (E41); abre el sistema en su fecha o Dirección con su firma; lo cierra solo Dirección con su firma desde
 * {@code fin − 7} según la hora de la BASE (E9) y con el anterior cerrado; cerrado no cambia (E10); en ABIERTO el fin solo
 * se extiende (M7); las áreas de una asignación van en su propuesta, por quien la propuso, antes de la solicitud, son del
 * nivel y no son transversales, y al aprobar son las aprobadas (A2, E24, E25). Y el flujo con los permisos mínimos.
 * <p>
 * Usa UN colegio para toda la clase y tres años: el anterior (el flujo, que deja sus periodos abiertos y el I cerrado),
 * el actual (solo en transacciones que se revierten) y el siguiente (sus secciones, su evaluación y su I bimestre
 * PROGRAMADO). Las clases de MySQL anteriores del job ya llenan los 25 colegios de la instalación: como la del sprint 9,
 * tanda 3, esta sube el tope a 30 solo para sí.
 */
@SpringBootTest(properties = "cuentasclaras.plataforma.max-colegios=30")
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class AuditoriaSprint10CurriculoMySqlTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	private static final String MOTIVO = "Carga horaria aprobada por Dirección para el año";

	private static final String INSERTAR_PERIODO = "INSERT INTO periodo_evaluacion (colegio_id, anio_escolar_id, numero, "
			+ "nombre, inicio, fin, estado, abierto_por, abierto_en, cerrado_por, cerrado_en, actualizado_por, creado_en, "
			+ "creado_por, actualizado_en) VALUES (?, ?, ?, 'Periodo de prueba', ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), ?, NOW(6))";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private ConfiguracionesEvaluacion configuraciones;

	@Autowired
	private Periodos periodos;

	@Autowired
	private AperturaPeriodos apertura;

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private RegistroSolicitudes solicitudes;

	@Autowired
	private FirmaSesion firma;

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

	/** El colegio de la clase con sus tres años, dos secciones del año siguiente, un docente y un auxiliar. */
	private record Escuela(EscuelaConDocentesMySql.Escuela e, long pasado, long actual, long futuro, long primaria,
			long secundaria, Usuario auxiliar) {

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

		Usuario docente() {
			return e.docente();
		}

		Usuario otroDocente() {
			return e.otroDocente();
		}
	}

	private static Escuela compartida;

	/** Los periodos del año anterior después del flujo: el I CERRADO y el II, III y IV ABIERTOS. */
	private static long[] delPasado;

	/** El I bimestre del año siguiente, PROGRAMADO (aún no empieza). */
	private static Long primeroDelFuturo;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	private Escuela escuela() {
		if (compartida != null) {
			return compartida;
		}
		EscuelaConDocentesMySql.Escuela e = EscuelaConDocentesMySql.crear(sufijo + "cur", "20600000056", "987654711", alta,
				servicioUsuarios, usuarios, codificador, colegios, estructura, jdbc);
		int y = hoy().getYear();
		long pasado = como(e.equipo().directora(), () -> anio(e.colegioId(), y - 1));
		long actual = como(e.equipo().directora(), () -> anio(e.colegioId(), y));
		long futuro = como(e.equipo().directora(), () -> anio(e.colegioId(), y + 1));
		long primaria = como(e.equipo().administracion(), () -> estructura.crearSeccion(futuro,
				new CrearSeccionRequest(Grado.PRIMARIA_5, "Curriculo")));
		long secundaria = como(e.equipo().administracion(), () -> estructura.crearSeccion(futuro,
				new CrearSeccionRequest(Grado.SECUNDARIA_1, "Curriculo")));
		Usuario auxiliar = UsuariosDePrueba.guardar(usuarios, codificador, e.colegioId(), "aux.cur." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		compartida = new Escuela(e, pasado, actual, futuro, primaria, secundaria, auxiliar);
		// El año siguiente: su evaluación (bimestres, 7 días) y su I bimestre PROGRAMADO, con la firma de Dirección.
		como(compartida.directora(), () -> configuraciones.guardar(futuro, bimestres(7)));
		LocalDate inicio = inicioClases(futuro);
		primeroDelFuturo = como(compartida.directora(), () -> periodos.programar(futuro, inicio, inicio.plusDays(60)));
		return compartida;
	}

	/** El año del colegio (lo crea Administración si no existe), con clases del 2 de enero al 30 de diciembre. */
	private long anio(long colegio, int numero) {
		Long existente = jdbc.queryForObject("SELECT MAX(id) FROM anio_escolar WHERE colegio_id = ? AND anio = ?", Long.class,
				colegio, numero);
		return existente != null ? existente : estructura.crearAnio(new CrearAnioEscolarRequest(numero,
				LocalDate.of(numero, 1, 2), LocalDate.of(numero, 12, 30), false));
	}

	/**
	 * El flujo del año anterior (una vez por clase): Dirección guarda la evaluación y programa los 4 bimestres con su firma;
	 * {@code sistema.academico} los abre en su fecha (con cc_sistema, en orden); Dirección extiende el fin del I con su
	 * firma y lo cierra con su firma.
	 */
	private long[] pasado() {
		Escuela s = escuela();
		if (delPasado != null) {
			return delPasado;
		}
		int y = hoy().getYear() - 1;
		como(s.directora(), () -> configuraciones.guardar(s.pasado(), bimestres(7)));
		long[] ids = new long[4];
		LocalDate[][] fechas = { { LocalDate.of(y, 3, 2), LocalDate.of(y, 5, 8) }, { LocalDate.of(y, 5, 18),
				LocalDate.of(y, 7, 24) }, { LocalDate.of(y, 8, 10), LocalDate.of(y, 10, 9) }, { LocalDate.of(y, 10, 19),
						LocalDate.of(y, 12, 18) } };
		for (int i = 0; i < 4; i++) {
			int n = i;
			ids[i] = como(s.directora(), () -> periodos.programar(s.pasado(), fechas[n][0], fechas[n][1]));
		}
		assertThat(apertura.abrir(s.colegio())).as("sistema.academico abre los 4 en su fecha y en orden").isEqualTo(4);
		como(s.directora(), () -> {
			periodos.moverFechas(ids[0], fechas[0][0], fechas[0][1].plusDays(3));
			periodos.cerrar(ids[0]);
			return null;
		});
		delPasado = ids;
		return ids;
	}

	// ------------------------------------------------------------------------------------------------ E12 y B13

	@Test
	void e12CatalogoNoSeEscribe() {
		for (JdbcTemplate conexion : List.of(jdbc, sistema())) {
			for (String sql : List.of(
					"INSERT INTO competencia (version_id, area_id, codigo, nombre, orden) SELECT version_id, id, 'PRI-MAT-9', "
							+ "'Competencia inventada', 9 FROM area_curricular WHERE codigo = 'MAT' AND nivel = 'PRIMARIA'",
					"UPDATE competencia SET nombre = 'Otro nombre' WHERE codigo = 'PRI-MAT-1'",
					"UPDATE escala_logro SET exige_conclusion = FALSE WHERE valor = 'C'",
					"INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion) SELECT id, "
							+ "'PRIMARIA', 'AD', 'Otro', 'Una descripción cualquiera', 9, FALSE FROM curriculo_version",
					"INSERT INTO curriculo_version (codigo, nombre, normas, vigente_desde, creado_en) VALUES ('CNEB-2099.1', "
							+ "'Inventada', 'Ninguna', '2099-01-01', NOW(6))",
					"UPDATE curriculo_version SET vigente_desde = vigente_desde WHERE 1 = 0",
					"UPDATE area_curricular SET exonerable = TRUE WHERE codigo = 'MAT'",
					"DELETE FROM competencia WHERE codigo = 'PRI-MAT-1'")) {
				assertThat(codigoAl(() -> conexion.update(sql))).as(sql).isEqualTo(1142);
			}
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competencia", Integer.class)).isEqualTo(75);
	}

	/** B13: ni el DBA (cc_migrador) mete una barra vertical o un salto de línea en un texto del catálogo (3819). */
	@Test
	void b13SeparadorEnElCatalogoFalla() {
		JdbcTemplate migrador = migrador();
		for (String[] caso : new String[][] {
				{ "UPDATE competencia SET nombre = CONCAT(nombre, '|x') WHERE codigo = 'PRI-MAT-1'", "competencia" },
				{ "UPDATE competencia SET nombre = CONCAT(nombre, CHAR(10)) WHERE codigo = 'PRI-MAT-1'", "competencia" },
				{ "UPDATE area_curricular SET nombre = CONCAT(nombre, CHAR(124)) WHERE codigo = 'MAT' AND nivel = 'PRIMARIA'",
						"area_curricular" },
				{ "UPDATE escala_logro SET descripcion = CONCAT(descripcion, CHAR(13)) WHERE valor = 'B' AND nivel = 'PRIMARIA'",
						"escala_logro" },
				{ "UPDATE escala_logro SET nombre = CONCAT(nombre, '|') WHERE valor = 'B' AND nivel = 'PRIMARIA'",
						"escala_logro" } }) {
			Integer codigo = codigoAl(() -> migrador.update(caso[0]));
			assertThat(codigo).as(caso[0]).isEqualTo(3819);
		}
	}

	// ------------------------------------------------------------------------------------------------ Configuración

	@Test
	void configuracionSinDireccionNiFirmaFalla() {
		Escuela s = escuela();
		String insertar = "INSERT INTO configuracion_evaluacion (colegio_id, anio_escolar_id, curriculo_version_id, "
				+ "actualizado_por, creado_en, creado_por, actualizado_en, version) VALUES (?, ?, ?, ?, NOW(6), ?, NOW(6), 0)";
		// Administración, aun con SU firma de la clave: no es Dirección.
		Integer administracion = enTransaccion(s.administracion(), () -> {
			firma.firmar(ClaveFirma.configuracionEvaluacion(s.actual(), 0));
			return codigoAl(() -> jdbc.update(insertar, s.colegio(), s.actual(), version(),
					s.administracion().getNombreUsuario(), s.administracion().getNombreUsuario()));
		});
		assertThat(administracion).isEqualTo(1644);
		// Dirección sin su firma.
		Integer sinFirma = enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(insertar, s.colegio(), s.actual(),
				version(), s.directora().getNombreUsuario(), s.directora().getNombreUsuario())));
		assertThat(sinFirma).isEqualTo(1644);
		// UPDATE sin firma, y con la firma de Administración.
		String cambiar = "UPDATE configuracion_evaluacion SET conclusion_max = 800, version = version + 1, actualizado_por = ? "
				+ "WHERE anio_escolar_id = ?";
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(cambiar, s.directora().getNombreUsuario(),
				s.futuro())))).isEqualTo(1644);
		Integer conAdministracion = enTransaccion(s.administracion(), () -> {
			firma.firmar(ClaveFirma.configuracionEvaluacion(s.futuro(), versionDe(s.futuro()) + 1));
			return codigoAl(() -> jdbc.update(cambiar, s.administracion().getNombreUsuario(), s.futuro()));
		});
		assertThat(conAdministracion).isEqualTo(1644);
		// Y el camino legítimo: Dirección con su firma.
		Integer legitimo = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.configuracionEvaluacion(s.futuro(), versionDe(s.futuro()) + 1));
			return codigoAl(() -> jdbc.update(cambiar, s.directora().getNombreUsuario(), s.futuro()));
		});
		assertThat(legitimo).isNull();
	}

	@Test
	void configuracionQueSaltaVersionesFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.directora(), () -> {
			long saltada = versionDe(s.futuro()) + 2;
			firma.firmar(ClaveFirma.configuracionEvaluacion(s.futuro(), saltada));
			return codigoAl(() -> jdbc.update("UPDATE configuracion_evaluacion SET conclusion_max = 700, version = ?, "
					+ "actualizado_por = ? WHERE anio_escolar_id = ?", saltada, s.directora().getNombreUsuario(), s.futuro()));
		});
		assertThat(codigo).isEqualTo(1644);
	}

	/** E11: con un periodo ABIERTO, el tipo de periodo (y la versión) ya no cambian. */
	@Test
	void e11ConfiguracionFijaConPeriodoAbierto() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			return cambiarConfiguracion(s, "tipo_periodo = 'TRIMESTRE', numero_periodos = 3");
		});
		assertThat(codigo).isEqualTo(1644);
		// Sin el periodo abierto, el mismo cambio sí entra (y se revierte).
		assertThat(enTransaccion(s.directora(), () -> cambiarConfiguracion(s,
				"tipo_periodo = 'TRIMESTRE', numero_periodos = 3"))).isNull();
	}

	/** M7: los días de cierre anticipado tampoco cambian con un periodo abierto; la DRE sí. */
	@Test
	void m7DiasDeCierreFijos() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			return cambiarConfiguracion(s, "dias_cierre_anticipado = 2");
		})).isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			return cambiarConfiguracion(s, "dre = 'DRE Callao'");
		})).isNull();
	}

	// ------------------------------------------------------------------------------------------------ Periodos (E41)

	@Test
	void e41PeriodoQueNaceCerradoFalla() {
		Escuela s = escuela();
		LocalDate inicio = inicioClases(s.futuro()).plusDays(70);
		Integer codigo = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.periodoProgramado(s.futuro(), 2));
			String yo = s.directora().getNombreUsuario();
			return codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), s.futuro(), 2, inicio, inicio.plusDays(30),
					"CERRADO", yo, java.sql.Timestamp.valueOf("2000-01-01 00:00:00"), yo,
					java.sql.Timestamp.valueOf("2000-01-01 00:00:00"), yo, yo));
		});
		assertThat(codigo).isEqualTo(1644);
		Integer abierto = enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.periodoProgramado(s.futuro(), 2));
			String yo = s.directora().getNombreUsuario();
			return codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), s.futuro(), 2, inicio, inicio.plusDays(30),
					"ABIERTO", yo, java.sql.Timestamp.valueOf("2000-01-01 00:00:00"), null, null, yo, yo));
		});
		assertThat(abierto).isEqualTo(1644);
	}

	@Test
	void e41FechasFueraDelAnioFalla() {
		Escuela s = escuela();
		LocalDate fin = jdbc.queryForObject("SELECT fin_clases FROM anio_escolar WHERE id = ?", LocalDate.class, s.futuro());
		assertThat(programarDirecto(s, s.futuro(), 2, fin.minusDays(5), fin.plusDays(1))).isEqualTo(1644);
		// Sin la firma de Dirección tampoco nace (aunque las fechas estén bien).
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), s.futuro(),
				2, fin.minusDays(20), fin.minusDays(5), "PROGRAMADO", null, null, null, null,
				s.directora().getNombreUsuario(), s.directora().getNombreUsuario())))).isEqualTo(1644);
		assertThat(programarDirecto(s, s.futuro(), 2, fin.minusDays(20), fin.minusDays(5))).as("control").isNull();
	}

	@Test
	void periodosSolapadosFallan() {
		Escuela s = escuela();
		LocalDate finDelPrimero = finDe(primeroDelFuturo);
		assertThat(programarDirecto(s, s.futuro(), 2, finDelPrimero, finDelPrimero.plusDays(20))).isEqualTo(1644);
		// El BEFORE UPDATE lee su propia tabla: el fin del I no puede llegar al inicio del II.
		Integer alMover = enTransaccion(s.directora(), () -> {
			Long segundo = periodos.programar(s.futuro(), finDelPrimero.plusDays(10), finDelPrimero.plusDays(40));
			assertThat(segundo).isNotNull();
			long version = jdbc.queryForObject("SELECT version FROM periodo_evaluacion WHERE id = ?", Long.class,
					primeroDelFuturo);
			firma.firmar(ClaveFirma.periodoFechas(primeroDelFuturo, version + 1));
			return codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET fin = ?, version = version + 1, "
					+ "actualizado_por = ? WHERE id = ?", finDelPrimero.plusDays(10), s.directora().getNombreUsuario(),
					primeroDelFuturo));
		});
		assertThat(alMover).isEqualTo(1644);
	}

	@Test
	void periodoDeMasFalla() {
		Escuela s = escuela();
		LocalDate fin = jdbc.queryForObject("SELECT fin_clases FROM anio_escolar WHERE id = ?", LocalDate.class, s.futuro());
		// Con BIMESTRE no hay periodo 5 (el trigger responde antes que el CHECK del número).
		assertThat(programarDirecto(s, s.futuro(), 5, fin.minusDays(10), fin.minusDays(2))).isEqualTo(1644);
		// Con TRIMESTRE no hay periodo 4 (el CHECK lo admitiría: lo rechaza el trigger).
		Integer conTrimestres = enTransaccion(s.directora(), () -> {
			cambiarConfiguracion(s, "tipo_periodo = 'TRIMESTRE', numero_periodos = 3");
			firma.firmar(ClaveFirma.periodoProgramado(s.futuro(), 4));
			String yo = s.directora().getNombreUsuario();
			return codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), s.futuro(), 4, fin.minusDays(10),
					fin.minusDays(2), "PROGRAMADO", null, null, null, null, yo, yo));
		});
		assertThat(conTrimestres).isEqualTo(1644);
	}

	@Test
	void periodoNumeroNoCambia() {
		Escuela s = escuela();
		assertThat(codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET numero = numero WHERE id = ?",
				primeroDelFuturo))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET anio_escolar_id = anio_escolar_id WHERE id = ?",
				primeroDelFuturo))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("UPDATE configuracion_evaluacion SET anio_escolar_id = anio_escolar_id "
				+ "WHERE anio_escolar_id = ?", s.futuro()))).isEqualTo(1143);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM periodo_evaluacion WHERE id = ?", primeroDelFuturo)))
				.isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM configuracion_evaluacion WHERE anio_escolar_id = ?",
				s.futuro()))).isEqualTo(1142);
	}

	// ------------------------------------------------------------------------------------------------ Apertura y cierre

	/** E9: el cierre lo decide la hora de la BASE (cc_hoy_lima), aunque la sesión y la aplicación estén en otra zona. */
	@Test
	void e9CierreAntesDeLaFechaFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			jdbc.execute("SET time_zone = '-08:00'");
			try {
				// cc_hoy_lima() (que cc_app no puede llamar: solo la usan los triggers) es UTC menos 5 horas, no la zona de la
				// sesión ni la de la aplicación.
				assertThat(jdbc.queryForObject("SELECT DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR)", LocalDate.class))
						.isEqualTo(hoy());
				return cerrarDirecto(s, primeroDelFuturo, true);
			}
			finally {
				jdbc.execute("SET time_zone = '+00:00'");
			}
		});
		assertThat(codigo).isEqualTo(1644);
		// El borde, en el año actual: con fin = hoy + 8 no cierra; con fin = hoy + 7 sí.
		assumeTrue(enVentana(), "fuera de la ventana del año para las fechas relativas a hoy");
		for (int dias : new int[] { 8, 7 }) {
			Integer borde = enTransaccion(s.directora(), () -> {
				configuraciones.guardar(s.actual(), bimestres(7));
				Long periodo = periodos.programar(s.actual(), hoy().minusDays(10), hoy().plusDays(dias));
				periodos.abrir(periodo);
				return cerrarDirecto(s, periodo, true);
			});
			assertThat(borde).as("fin = hoy + " + dias).isEqualTo(dias == 8 ? 1644 : null);
		}
	}

	@Test
	void cierreSinFirmaFalla() {
		Escuela s = escuela();
		long[] p = pasado();
		assertThat(enTransaccion(s.directora(), () -> cerrarDirecto(s, p[1], false))).isEqualTo(1644);
		// Con la firma de Promotoría tampoco: el periodo lo cierra Dirección.
		Integer promotoria = enTransaccion(s.promotora(), () -> {
			firma.firmar(ClaveFirma.periodoCerrado(p[1]));
			return codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET estado = 'CERRADO', cerrado_por = ?, "
					+ "cerrado_en = NOW(6) WHERE id = ?", s.promotora().getNombreUsuario(), p[1]));
		});
		assertThat(promotoria).isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> cerrarDirecto(s, p[1], true))).as("control").isNull();
		assertThat(estado(p[1])).isEqualTo("ABIERTO");
	}

	@Test
	void cierreConElAnteriorAbiertoFalla() {
		Escuela s = escuela();
		long[] p = pasado();
		assertThat(estado(p[1])).isEqualTo("ABIERTO");
		assertThat(enTransaccion(s.directora(), () -> cerrarDirecto(s, p[2], true))).isEqualTo(1644);
	}

	/** E10: un periodo CERRADO no cambia sus fechas, su estado ni su nombre (ni con la firma de Dirección). */
	@Test
	void e10PeriodoCerradoNoCambia() {
		Escuela s = escuela();
		long cerrado = pasado()[0];
		assertThat(estado(cerrado)).isEqualTo("CERRADO");
		String yo = s.directora().getNombreUsuario();
		for (String cambio : new String[] { "fin = fin + INTERVAL 1 DAY", "estado = 'ABIERTO', cerrado_por = NULL, "
				+ "cerrado_en = NULL", "nombre = 'I bimestre reabierto'", "cerrado_por = '" + yo + "'" }) {
			Integer codigo = enTransaccion(s.directora(), () -> {
				long version = jdbc.queryForObject("SELECT version FROM periodo_evaluacion WHERE id = ?", Long.class, cerrado);
				firma.firmar(ClaveFirma.periodoFechas(cerrado, version + 1));
				firma.firmar(ClaveFirma.periodoAbierto(cerrado));
				return codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET " + cambio + ", version = version + 1, "
						+ "actualizado_por = ? WHERE id = ?", yo, cerrado));
			});
			assertThat(codigo).as(cambio).isEqualTo(1644);
		}
		assertThat(estado(cerrado)).isEqualTo("CERRADO");
	}

	@Test
	void aperturaEscritaDosVecesFalla() {
		Escuela s = escuela();
		long abierto = pasado()[1];
		assertThat(codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET abierto_por = ? WHERE id = ?",
				s.directora().getNombreUsuario(), abierto))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET abierto_en = NOW(6) WHERE id = ?", abierto)))
				.isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT abierto_por FROM periodo_evaluacion WHERE id = ?", String.class, abierto))
				.isEqualTo("sistema.academico");
	}

	/** El sistema abre en su fecha, nunca antes; y con la clave de cc_app nadie abre «como» el sistema. */
	@Test
	void aperturaPorSistemaAntesDeSuFechaFalla() {
		escuela();
		String abrir = "UPDATE periodo_evaluacion SET estado = 'ABIERTO', abierto_por = 'sistema.academico', abierto_en = "
				+ "NOW(6) WHERE id = ?";
		assertThat(codigoAl(() -> sistema().update(abrir, primeroDelFuturo))).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(abrir, primeroDelFuturo))).isEqualTo(1644);
		assertThat(estado(primeroDelFuturo)).isEqualTo("PROGRAMADO");
	}

	/** M7: con el periodo ABIERTO, su fin no se adelanta (con firma); se extiende, sí. */
	@Test
	void m7FinAdelantadoFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			return moverFin(s, primeroDelFuturo, -1);
		})).isEqualTo(1644);
		assertThat(enTransaccion(s.directora(), () -> {
			periodos.abrir(primeroDelFuturo);
			return moverFin(s, primeroDelFuturo, 1);
		})).as("extender").isNull();
	}

	// ------------------------------------------------------------------------------------------------ Áreas (A2, E24, E25)

	@Test
	void e24AreaEnAsignacionVigenteFalla() {
		Escuela s = escuela();
		Long vigente = proponerYAprobar(s, s.primaria(), s.docente(), TipoAsignacion.DOCENTE, List.of(area("PRIMARIA", "MAT")));
		assertThat(insertarArea(vigente, area("PRIMARIA", "CYT"), s.administracion())).isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update("UPDATE asignacion_area SET area_id = area_id WHERE asignacion_id = ?",
				vigente))).isEqualTo(1142);
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM asignacion_area WHERE asignacion_id = ?", vigente)))
				.isEqualTo(1142);
	}

	@Test
	void e24AreaPorOtroQueQuienProponeFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.administracion(), () -> {
			Long propuesta = propuestaDirecta(s, s.primaria(), s.otroDocente(), "DOCENTE", null);
			Integer otro = insertarArea(propuesta, area("PRIMARIA", "COM"), s.directora());
			assertThat(insertarArea(propuesta, area("PRIMARIA", "MAT"), s.administracion())).as("quien propuso").isNull();
			return otro;
		});
		assertThat(codigo).isEqualTo(1644);
	}

	@Test
	void areaDeOtroNivelFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.administracion(), () -> insertarArea(propuestaDirecta(s, s.primaria(), s.otroDocente(),
				"DOCENTE", null), area("SECUNDARIA", "MAT"), s.administracion()))).isEqualTo(1644);
	}

	@Test
	void areaTransversalFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.administracion(), () -> insertarArea(propuestaDirecta(s, s.primaria(), s.otroDocente(),
				"TUTOR", null), area("PRIMARIA", "TRV"), s.administracion()))).isEqualTo(1644);
	}

	/** E25: una cobertura (con hasta) no califica. */
	@Test
	void e25CoberturaNoCalifica() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.administracion(), () -> insertarArea(propuestaDirecta(s, s.secundaria(), s.otroDocente(),
				"DOCENTE", hoy().plusDays(2)), area("SECUNDARIA", "MAT"), s.administracion()))).isEqualTo(1644);
	}

	@Test
	void areaAuxiliarFalla() {
		Escuela s = escuela();
		assertThat(enTransaccion(s.administracion(), () -> insertarArea(propuestaDirecta(s, s.secundaria(), s.auxiliar(),
				"AUXILIAR", null), area("SECUNDARIA", "MAT"), s.administracion()))).isEqualTo(1644);
	}

	/** A2: con la solicitud ya enlazada, nadie agrega áreas (ni quien propuso). */
	@Test
	void a2AreaDespuesDeLaSolicitudFalla() {
		Escuela s = escuela();
		Integer codigo = enTransaccion(s.administracion(), () -> {
			Long id = asignaciones.proponer(ProponerAsignacionRequest.conAreas(s.secundaria(), s.otroDocente().getId(),
					TipoAsignacion.DOCENTE, MOTIVO, List.of(area("SECUNDARIA", "CYT"))));
			return insertarArea(id, area("SECUNDARIA", "MAT"), s.administracion());
		});
		assertThat(codigo).isEqualTo(1644);
	}

	/** A2: la solicitud aprobada dice MAT y CYT; la asignación tiene solo MAT: al pasar a VIGENTE la base lo rechaza. */
	@Test
	void a2AreasDistintasALasAprobadasFallan() {
		Escuela s = escuela();
		long matematica = area("SECUNDARIA", "MAT");
		long ciencia = area("SECUNDARIA", "CYT");
		for (boolean iguales : new boolean[] { false, true }) {
			Integer codigo = enTransaccion(s.administracion(), () -> {
				Long id = propuestaDirecta(s, s.secundaria(), s.otroDocente(), "TUTOR", null);
				assertThat(insertarArea(id, matematica, s.administracion())).isNull();
				Map<String, String> datos = new LinkedHashMap<>();
				datos.put("seccion", Long.toString(s.secundaria()));
				datos.put("docente", s.otroDocente().getId().toString());
				datos.put("tipo", "TUTOR");
				datos.put("areas", iguales ? Long.toString(matematica) : Math.min(matematica, ciencia) + ","
						+ Math.max(matematica, ciencia));
				Long solicitud = solicitudes.crear(TipoSolicitud.ASIGNACION_DOCENTE, "asignacion_docente", id,
						"Tutoría de prueba de las áreas", datos, MOTIVO).getId();
				jdbc.update("UPDATE asignacion_docente SET solicitud_id = ? WHERE id = ?", solicitud, id);
				UsuariosDePrueba.iniciarSesion(s.directora());
				return codigoAlAprobar(solicitud);
			});
			assertThat(codigo).as(iguales ? "las mismas áreas" : "otras áreas").isEqualTo(iguales ? null : 1644);
		}
	}

	/** Sección 3.4: al aprobar otra con las mismas claves, la vigente termina en la misma transacción (los UNIQUE). */
	@Test
	void reemplazoDeAsignacionVigenteEnUnaTransaccion() {
		Escuela s = escuela();
		Usuario tutora = UsuariosDePrueba.guardar(usuarios, codificador, s.colegio(), "tutora.cur." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		Long anterior = proponerYAprobar(s, s.primaria(), tutora, TipoAsignacion.TUTOR, List.of(area("PRIMARIA", "MAT")));
		Long nueva = como(s.administracion(), () -> asignaciones.proponer(ProponerAsignacionRequest.conAreas(s.primaria(),
				tutora.getId(), TipoAsignacion.TUTOR, MOTIVO, List.of(area("PRIMARIA", "MAT"), area("PRIMARIA", "COM")))));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, nueva);
		como(s.promotora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		assertThat(jdbc.queryForMap("SELECT estado, terminada_por FROM asignacion_docente WHERE id = ?", anterior))
				.containsEntry("estado", "TERMINADA").containsEntry("terminada_por", s.promotora().getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, nueva))
				.isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_docente WHERE seccion_id = ? AND tutor_vigente",
				Integer.class, s.primaria())).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------------------------ CHECK con NULL

	@Test
	void checksConNull() {
		Escuela s = escuela();
		LocalDate fin = jdbc.queryForObject("SELECT fin_clases FROM anio_escolar WHERE id = ?", LocalDate.class, s.futuro());
		String yo = s.directora().getNombreUsuario();
		// PROGRAMADO con abierto_por o cerrado_por escritos: el CHECK (el trigger deja pasar un PROGRAMADO firmado).
		for (String[] caso : new String[][] { { yo, null }, { null, yo } }) {
			Integer codigo = enTransaccion(s.directora(), () -> {
				firma.firmar(ClaveFirma.periodoProgramado(s.futuro(), 2));
				return codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), s.futuro(), 2, fin.minusDays(20),
						fin.minusDays(5), "PROGRAMADO", caso[0], null, caso[1], null, yo, yo));
			});
			assertThat(codigo).as("abierto_por " + caso[0] + ", cerrado_por " + caso[1]).isEqualTo(3819);
		}
		// Abrir sin quién (abierto_por NULL): lo rechaza el trigger o el CHECK, nunca pasa.
		assertThat(enTransaccion(s.directora(), () -> codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET estado = "
				+ "'ABIERTO', abierto_por = NULL, abierto_en = NOW(6) WHERE id = ?", primeroDelFuturo)))).isIn(1644, 3819);
		// La DRE vacía no pasa (NULL sí).
		assertThat(enTransaccion(s.directora(), () -> cambiarConfiguracion(s, "dre = ' '"))).isEqualTo(3819);
		assertThat(enTransaccion(s.directora(), () -> cambiarConfiguracion(s, "dre = NULL"))).isNull();
		// solo_grados: NULL vale (todos los grados); una lista inválida no (como cc_migrador, que es quien la escribe).
		JdbcTemplate migrador = migrador();
		assertThat(codigoAl(() -> migrador.update("UPDATE competencia SET solo_grados = 'INICIAL_9' WHERE codigo = "
				+ "'INI-TRV-1'"))).isEqualTo(3819);
		assertThat(migrador.queryForObject("SELECT COUNT(*) FROM competencia WHERE solo_grados IS NULL", Integer.class))
				.isEqualTo(72);
		// reemplaza_id: NULL en la primera versión; dos versiones no corrigen la misma (UNIQUE), en una transacción que se
		// revierte (una versión sin su .sha256 no dejaría arrancar prod).
		DataSourceTransactionManager deMigrador = new DataSourceTransactionManager(migrador.getDataSource());
		Integer dos = new TransactionTemplate(deMigrador).execute(t -> {
			t.setRollbackOnly();
			String version = "INSERT INTO curriculo_version (codigo, nombre, normas, vigente_desde, reemplaza_id, creado_en) "
					+ "SELECT ?, 'Corrección de prueba', 'Ninguna norma', '2099-01-01', id, NOW(6) FROM curriculo_version "
					+ "WHERE codigo = 'CNEB-2016.1'";
			JdbcTemplate enLaTransaccion = new JdbcTemplate(migrador.getDataSource());
			assertThat(enLaTransaccion.update(version, "CNEB-2016.8")).isOne();
			return codigoAl(() -> enLaTransaccion.update(version, "CNEB-2016.9"));
		});
		assertThat(dos).isEqualTo(1062);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curriculo_version", Integer.class)).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------------------------ Flujo

	/**
	 * Con los permisos mínimos: Dirección guarda la evaluación con su firma y programa 4 bimestres; cc_sistema los abre en
	 * su fecha; Dirección extiende el fin del I con su firma y lo cierra con su firma; Administración propone una asignación
	 * con áreas y otra persona de Dirección la aprueba con su firma.
	 */
	@Test
	void flujoCurriculoConPermisosMinimos() {
		Escuela s = escuela();
		long[] p = pasado();
		assertThat(estado(p[0])).isEqualTo("CERRADO");
		assertThat(List.of(estado(p[1]), estado(p[2]), estado(p[3]))).containsOnly("ABIERTO");
		assertThat(jdbc.queryForObject("SELECT cerrado_por FROM periodo_evaluacion WHERE id = ?", String.class, p[0]))
				.isEqualTo(s.directora().getNombreUsuario());
		// La base puso la hora de apertura y de cierre (hora de Lima de la base).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM periodo_evaluacion WHERE id = ? AND cerrado_en IS NOT NULL "
				+ "AND abierto_en IS NOT NULL", Integer.class, p[0])).isOne();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND accion IN "
				+ "('PERIODO_PROGRAMADO', 'PERIODO_ABIERTO', 'PERIODO_FECHAS_CAMBIADAS', 'PERIODO_CERRADO')", Integer.class,
				s.colegio())).isGreaterThanOrEqualTo(10);
		Long asignacion = proponerYAprobar(s, s.secundaria(), s.docente(), TipoAsignacion.DOCENTE,
				List.of(area("SECUNDARIA", "MAT"), area("SECUNDARIA", "CYT")));
		assertThat(jdbc.queryForObject("SELECT estado FROM asignacion_docente WHERE id = ?", String.class, asignacion))
				.isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_area WHERE asignacion_id = ?", Integer.class,
				asignacion)).isEqualTo(2);
	}

	// ------------------------------------------------------------------------------------------------ Ayudas

	private Long proponerYAprobar(Escuela s, long seccion, Usuario docente, TipoAsignacion tipo, List<Long> areas) {
		Long id = como(s.administracion(), () -> asignaciones.proponer(ProponerAsignacionRequest.conAreas(seccion,
				docente.getId(), tipo, MOTIVO, areas)));
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM asignacion_docente WHERE id = ?", Long.class, id);
		como(s.directora(), () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
		return id;
	}

	/** Una asignación PROPUESTA sin su solicitud, por SQL (trg_asignacion_docente_nace la admite así). @return su id */
	private Long propuestaDirecta(Escuela s, long seccion, Usuario docente, String tipo, LocalDate hasta) {
		jdbc.update("INSERT INTO asignacion_docente (colegio_id, anio_escolar_id, seccion_id, usuario_id, tipo, estado, "
				+ "pendiente, propuesto_por, hasta, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, 'PROPUESTA', "
				+ "TRUE, ?, ?, NOW(6), ?, NOW(6))", s.colegio(), s.futuro(), seccion, docente.getId(), tipo,
				s.administracion().getNombreUsuario(), hasta, s.administracion().getNombreUsuario());
		return jdbc.queryForObject("SELECT MAX(id) FROM asignacion_docente WHERE colegio_id = ? AND seccion_id = ? AND "
				+ "usuario_id = ?", Long.class, s.colegio(), seccion, docente.getId());
	}

	private Integer insertarArea(Long asignacion, long area, Usuario quien) {
		return codigoAl(() -> jdbc.update("INSERT INTO asignacion_area (colegio_id, asignacion_id, area_id, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, ?, ?, NOW(6), ?, NOW(6))", escuela().colegio(), asignacion, area,
				quien.getNombreUsuario()));
	}

	private Integer codigoAlAprobar(Long solicitud) {
		try {
			bandeja.aprobar(solicitud, null);
			return null;
		}
		catch (DataAccessException e) {
			return codigoDe(e);
		}
		catch (RuntimeException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			throw e;
		}
	}

	private long area(String nivel, String codigo) {
		return jdbc.queryForObject("SELECT a.id FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id WHERE "
				+ "v.codigo = 'CNEB-2016.1' AND a.nivel = ? AND a.codigo = ?", Long.class, nivel, codigo);
	}

	private Long version() {
		return jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
	}

	private ConfiguracionEvaluacion.Valores bimestres(int dias) {
		return new ConfiguracionEvaluacion.Valores(version(), TipoPeriodo.BIMESTRE, 500, false, dias, "Castellano", null,
				null);
	}

	private long versionDe(long anio) {
		return jdbc.queryForObject("SELECT version FROM configuracion_evaluacion WHERE anio_escolar_id = ?", Long.class, anio);
	}

	/** En la transacción de Dirección: cambia la configuración del año siguiente con su firma (la versión, de a una). */
	private Integer cambiarConfiguracion(Escuela s, String cambio) {
		long siguiente = versionDe(s.futuro()) + 1;
		firma.firmar(ClaveFirma.configuracionEvaluacion(s.futuro(), siguiente));
		return codigoAl(() -> jdbc.update("UPDATE configuracion_evaluacion SET " + cambio + ", version = ?, actualizado_por "
				+ "= ? WHERE anio_escolar_id = ?", siguiente, s.directora().getNombreUsuario(), s.futuro()));
	}

	/** Dirección programa por SQL (con su firma) el periodo {@code numero}; en una transacción que se revierte. */
	private Integer programarDirecto(Escuela s, long anio, int numero, LocalDate inicio, LocalDate fin) {
		return enTransaccion(s.directora(), () -> {
			firma.firmar(ClaveFirma.periodoProgramado(anio, numero));
			String yo = s.directora().getNombreUsuario();
			return codigoAl(() -> jdbc.update(INSERTAR_PERIODO, s.colegio(), anio, numero, inicio, fin, "PROGRAMADO", null,
					null, null, null, yo, yo));
		});
	}

	/** En la transacción de Dirección: cierra el periodo por SQL, con o sin su firma. */
	private Integer cerrarDirecto(Escuela s, long periodo, boolean conFirma) {
		if (conFirma) {
			firma.firmar(ClaveFirma.periodoCerrado(periodo));
		}
		return codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET estado = 'CERRADO', cerrado_por = ?, cerrado_en = "
				+ "NOW(6) WHERE id = ?", s.directora().getNombreUsuario(), periodo));
	}

	/** En la transacción de Dirección: mueve el fin del periodo {@code dias} días, con su firma. */
	private Integer moverFin(Escuela s, long periodo, int dias) {
		long version = jdbc.queryForObject("SELECT version FROM periodo_evaluacion WHERE id = ?", Long.class, periodo);
		firma.firmar(ClaveFirma.periodoFechas(periodo, version + 1));
		return codigoAl(() -> jdbc.update("UPDATE periodo_evaluacion SET fin = fin + INTERVAL ? DAY, version = version + 1, "
				+ "actualizado_por = ? WHERE id = ?", dias, s.directora().getNombreUsuario(), periodo));
	}

	private String estado(long periodo) {
		return jdbc.queryForObject("SELECT estado FROM periodo_evaluacion WHERE id = ?", String.class, periodo);
	}

	private LocalDate finDe(long periodo) {
		return jdbc.queryForObject("SELECT fin FROM periodo_evaluacion WHERE id = ?", LocalDate.class, periodo);
	}

	private LocalDate inicioClases(long anio) {
		return jdbc.queryForObject("SELECT inicio_clases FROM anio_escolar WHERE id = ?", LocalDate.class, anio);
	}

	private static LocalDate hoy() {
		return LocalDate.now(LIMA);
	}

	/** Las pruebas con fechas relativas a hoy en el año actual necesitan unos días antes y después dentro de él. */
	private static boolean enVentana() {
		LocalDate hoy = hoy();
		return !hoy.isBefore(LocalDate.of(hoy.getYear(), 1, 20)) && !hoy.isAfter(LocalDate.of(hoy.getYear(), 12, 15));
	}

	private JdbcTemplate sistema() {
		FuenteDatosEnrutada enrutada = (FuenteDatosEnrutada) fuenteDatos;
		assertThat(enrutada.separadas()).isTrue();
		return new JdbcTemplate(enrutada.sistema());
	}

	private static JdbcTemplate migrador() {
		String clave = System.getenv("CC_MYSQL_CLAVE_MIGRADOR");
		assumeTrue(clave != null && !clave.isBlank(), "Falta CC_MYSQL_CLAVE_MIGRADOR");
		return new JdbcTemplate(new DriverManagerDataSource(System.getenv().getOrDefault("CC_MYSQL_URL",
				"jdbc:mysql://127.0.0.1:3306/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"), "cc_migrador", clave));
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

	/**
	 * En una transacción con la persona YA en sesión (Hibernate fija el colegio al empezar la transacción), que se revierte
	 * al terminar.
	 */
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

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			return codigoDe(e);
		}
	}

	private static Integer codigoDe(DataAccessException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		throw e;
	}
}
