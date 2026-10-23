package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.CompetenciaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Catalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.ConfiguracionesEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.Periodos;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AvanceRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.CorreccionesNota;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.Exoneraciones;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.RegistroNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sprint 10, tanda 2: el escenario de las notas en H2 (sin triggers: aquí se prueba la aplicación; la base, en
 * {@code AuditoriaSprint10NotasMySqlTest}). 2026 en curso con su evaluación (bimestres, 7 días de cierre) y el I bimestre
 * ABIERTO desde el 2 de marzo; en 5.° A de Primaria: Ana califica Matemática y Ciencia y Tecnología, Luis Comunicación, Tito
 * es el tutor (con Personal Social: también las transversales) y Rita es auxiliar. Tres alumnos: Mateo, Valeria y Lucas.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
abstract class PruebaNotas {

	static final LocalDate INICIO = LocalDate.of(2026, 3, 2);

	static final LocalDate FIN = LocalDate.of(2026, 5, 8);

	static final String MOTIVO = "Carga horaria 2026 aprobada por Dirección";

	static final String MOTIVO_CORRECCION = "El examen recalificado muestra que logró lo esperado";

	@Autowired
	protected RegistroNotas registro;

	@Autowired
	protected CorreccionesNota correcciones;

	@Autowired
	protected Exoneraciones exoneraciones;

	@Autowired
	protected AvanceRegistro avance;

	@Autowired
	protected ConfiguracionesEvaluacion configuraciones;

	@Autowired
	protected Periodos periodos;

	@Autowired
	protected Catalogo catalogo;

	@Autowired
	protected Asignaciones asignaciones;

	@Autowired
	protected BandejaAprobaciones bandeja;

	@Autowired
	protected ServicioEstructura estructura;

	@Autowired
	protected ServicioAlumnos alumnos;

	@Autowired
	protected UsuarioRepository usuarios;

	@Autowired
	protected PasswordEncoder codificador;

	@Autowired
	protected TokenDeSesionDePrueba tokens;

	@Autowired
	protected RelojAjustable reloj;

	@Autowired
	protected JdbcTemplate jdbc;

	protected EscenarioEscolar.Estructura escuela;

	protected Long seccion;

	protected Long mateo;

	protected Long valeria;

	protected Long lucas;

	protected Usuario ana;

	protected Usuario luis;

	protected Usuario tito;

	protected Usuario rita;

	protected Long periodo;

	@BeforeEach
	void prepararNotas() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		hoyEs(INICIO);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructuraConAnioEnCurso(estructura);
		seccion = escuela.primaria5A2026();
		Long mateoId = alumnos.registrar(EscenarioEscolar.mateoConRosa(seccion)).alumnoId();
		Long valeriaId = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(seccion)).alumnoId();
		Long lucasId = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234568", "Flores", "Rojas", "Lucas",
				LocalDate.of(2015, 2, 1), "41234567", "Flores", "Díaz", "Pedro", "987111222", "pedro.flores@gmail.com",
				seccion)).alumnoId();
		mateo = matricula(mateoId);
		valeria = matricula(valeriaId);
		lucas = matricula(lucasId);
		SecurityContextHolder.clearContext();
		// La cuenta de «director» (EscenarioCobranza.DIRECCION) en la base: al aprobar una corrección que pidió Dirección se
		// vuelve a comprobar que sigue siendo de Dirección.
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, EscenarioCobranza.DIRECCION.getUsername(), UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		ana = docente("ana.notas");
		luis = docente("luis.notas");
		tito = docente("tito.tutor");
		rita = docente("rita.auxiliar");
		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(false)));
		asignar(ana, TipoAsignacion.DOCENTE, List.of(area("MAT"), area("CYT")));
		asignar(luis, TipoAsignacion.DOCENTE, List.of(area("COM")));
		asignar(tito, TipoAsignacion.TUTOR, List.of(area("PS")));
		asignar(rita, TipoAsignacion.AUXILIAR, List.of());
		periodo = como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(), INICIO, FIN));
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(periodo));
	}

	@AfterEach
	void limpiarNotas() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	protected ConfiguracionEvaluacion.Valores valores(boolean conclusionSiempre) {
		Long version = jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
		return new ConfiguracionEvaluacion.Valores(version, TipoPeriodo.BIMESTRE, 500, conclusionSiempre, 7, "Castellano",
				"DRE Lima Metropolitana", "UGEL 03");
	}

	protected Usuario docente(String nombre, Rol... otros) {
		Rol[] roles = new Rol[otros.length + 1];
		roles[0] = Rol.DOCENTE;
		System.arraycopy(otros, 0, roles, 1, otros.length);
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, roles);
	}

	/** Administración propone con esas áreas y Dirección aprueba en la bandeja. @return la asignación */
	protected Long asignar(Usuario quien, TipoAsignacion tipo, List<Long> areas) {
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(ProponerAsignacionRequest.conAreas(
				seccion, quien.getId(), tipo, MOTIVO, areas)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		return id;
	}

	protected Long matricula(Long alumno) {
		return jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ? AND anio_escolar_id = ?", Long.class, alumno,
				escuela.anio2026());
	}

	/** El área de Primaria con ese código. */
	protected Long area(String codigo) {
		return area(Nivel.PRIMARIA, codigo);
	}

	protected Long area(Nivel nivel, String codigo) {
		return como(EscenarioCobranza.DIRECCION, () -> catalogo.areasDe(version(), nivel)).stream()
				.filter(a -> a.codigo().equals(codigo)).findFirst().map(AreaDelCatalogo::id).orElseThrow();
	}

	protected Long version() {
		return jdbc.queryForObject("SELECT id FROM curriculo_version WHERE codigo = 'CNEB-2016.1'", Long.class);
	}

	/** Las competencias del área en 5.° de Primaria (ids, en su orden). */
	protected List<Long> competencias(String codigoArea) {
		Long area = area(codigoArea);
		return como(EscenarioCobranza.DIRECCION, () -> catalogo.competenciasDe(version(), Grado.PRIMARIA_5)).stream()
				.filter(c -> c.areaId().equals(area)).map(CompetenciaDelCatalogo::id).toList();
	}

	/** La hoja con ese nivel (y esa conclusión) para cada alumno en cada competencia del área. */
	protected NotasRequest hoja(String codigoArea, List<Long> matriculas, String nivel, String conclusion) {
		List<NotaPedida> notas = new ArrayList<>();
		for (Long m : matriculas) {
			for (Long c : competencias(codigoArea)) {
				notas.add(new NotaPedida(m, c, nivel, conclusion));
			}
		}
		return new NotasRequest(notas, null);
	}

	protected ResultadoNotas guardar(Usuario quien, String codigoArea, NotasRequest hoja) {
		return como(UsuariosDePrueba.autenticado(quien), () -> registro.guardar(seccion, area(codigoArea), periodo, hoja));
	}

	/** Ana pone A (sin conclusión: en Primaria no la exige) en Matemática a los tres. */
	protected ResultadoNotas anaPoneA() {
		return guardar(ana, "MAT", hoja("MAT", List.of(mateo, valeria, lucas), "A", null));
	}

	/** Lleva el reloj al día del cierre y Dirección cierra el I bimestre. */
	protected void cerrarPeriodo() {
		hoyEs(LocalDate.of(2026, 5, 4));
		como(EscenarioCobranza.DIRECCION, () -> avance.cerrar(periodo));
	}

	protected Long vigente(Long matricula, Long competencia) {
		return jdbc.queryForObject("SELECT c.id FROM calificacion c WHERE c.matricula_id = ? AND c.competencia_id = ? AND "
				+ "c.periodo_id = ? AND NOT EXISTS (SELECT 1 FROM calificacion x WHERE x.reemplaza_id = c.id)", Long.class,
				matricula, competencia, periodo);
	}

	protected void hoyEs(LocalDate dia) {
		reloj.fijar(LocalDateTime.of(dia, java.time.LocalTime.of(8, 0)).atZone(ZoneId.of("America/Lima")).toInstant());
	}

	/** Corre la operación como esa persona y deja la sesión como estaba (las llamadas se pueden anidar). */
	protected <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		org.springframework.security.core.Authentication antes = SecurityContextHolder.getContext().getAuthentication();
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
			if (antes != null) {
				SecurityContextHolder.getContext().setAuthentication(antes);
			}
		}
	}

	protected void como(UsuarioAutenticado quien, Runnable operacion) {
		como(quien, () -> {
			operacion.run();
			return null;
		});
	}

	protected long contar(String sql, Object... argumentos) {
		return jdbc.queryForObject(sql, Long.class, argumentos);
	}
}
