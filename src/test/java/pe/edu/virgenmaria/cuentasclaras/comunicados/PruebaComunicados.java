package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
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
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.ConsultaComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.EnvioComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.HuellaComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.LecturaFamilia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Sprint 9, tanda 3: la base de las pruebas de comunicados (H2, aplicación completa, reloj en un martes de clases). 2026
 * EN_CURSO: 5.° Primaria A con Mateo y Valeria (familia de Rosa) y Lucas (familia de Pedro); 5.° Primaria B con Sofía
 * (familia de Carmen). «ana.docente» es tutora de 5.° A, «luis.docente» docente de 5.° B y «eva.auxiliar» auxiliar de
 * 5.° A (aprobadas por Dirección en la bandeja). Los contactos están verificados.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
public abstract class PruebaComunicados {

	/** Martes 6 de octubre de 2026, 08:00 en Lima. */
	public static final Instant MARTES_8 = Instant.parse("2026-10-06T13:00:00Z");

	public static final String TEXTO = "Estimadas familias: el viernes celebraremos el Día del Logro en el patio principal. "
			+ "Los esperamos a las 9:00.";

	@Autowired
	protected EnvioComunicados envio;

	@Autowired
	protected ConsultaComunicados consulta;

	@Autowired
	protected LecturaFamilia lectura;

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

	protected Long seccionA;

	protected Long seccionB;

	protected Long familiaRosa;

	protected Long familiaPedro;

	protected Long familiaCarmen;

	protected Long rosa;

	protected Long pedro;

	protected Long carmen;

	protected Usuario tutora;

	protected Usuario docenteB;

	protected Usuario auxiliar;

	@BeforeEach
	void prepararEscenario() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		reloj.fijar(MARTES_8);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		EscenarioEscolar.Estructura escuela = EscenarioEscolar.crearEstructuraConAnioEnCurso(estructura);
		seccionA = escuela.primaria5A2026();
		seccionB = escuela.primaria5B2026();
		alumnos.registrar(EscenarioEscolar.mateoConRosa(seccionA));
		alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(seccionA));
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234568", "Flores", "Rojas", "Lucas",
				LocalDate.of(2015, 2, 1), "41234567", "Flores", "Díaz", "Pedro", "987111222", "pedro.flores@gmail.com",
				seccionA));
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234569", "Vega", "Ruiz", "Sofía",
				LocalDate.of(2015, 3, 1), "41234569", "Vega", "Soto", "Carmen", "987333444", "carmen.vega@gmail.com",
				seccionB));
		EscenarioEscolar.contactosConfirmados(jdbc);
		SecurityContextHolder.clearContext();
		tutora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "ana.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		docenteB = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "luis.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		auxiliar = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "eva.auxiliar", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		asignar(seccionA, tutora, TipoAsignacion.TUTOR, null);
		asignar(seccionB, docenteB, TipoAsignacion.DOCENTE, "Matemática");
		asignar(seccionA, auxiliar, TipoAsignacion.AUXILIAR, null);
		rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = ?", Long.class,
				EscenarioEscolar.DNI_ROSA);
		pedro = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '41234567'", Long.class);
		carmen = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '41234569'", Long.class);
		familiaRosa = familia(rosa);
		familiaPedro = familia(pedro);
		familiaCarmen = familia(carmen);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiarEscenario() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	private void asignar(Long seccion, Usuario docente, TipoAsignacion tipo, String areas) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long id = asignaciones.proponer(new ProponerAsignacionRequest(seccion, docente.getId(), tipo, areas,
				"Carga horaria aprobada por Dirección"));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
	}

	private Long familia(Long apoderado) {
		return jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, apoderado);
	}

	/** Rosa (familia de Mateo y Valeria) con su cuenta del portal. */
	protected UsuarioAutenticado rosaEnLinea() {
		return new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), rosa);
	}

	protected UsuarioAutenticado carmenEnLinea() {
		return new UsuarioAutenticado(42L, 1L, "carmen.familia", "Carmen Vega", null, true, false, false,
				EnumSet.of(Rol.APODERADO), carmen);
	}

	/** Un comunicado a la sección A. */
	protected static BorradorComunicado aLaSeccion(EmisorRol emisor, Long seccion, String titulo) {
		return new BorradorComunicado(emisor, AlcanceComunicado.SECCION, null, null, seccion, titulo, TEXTO, false, true);
	}

	protected static BorradorComunicado aTodoElColegio(EmisorRol emisor, String titulo) {
		return new BorradorComunicado(emisor, AlcanceComunicado.COLEGIO, null, null, null, titulo, TEXTO, false, true);
	}

	/** Un PDF ya revisado (sin pasar por el proceso hijo: lo prueban AnalizadorPdfTest y AnalizadorHostilesTest). */
	protected static AdjuntoValidado pdf(boolean requiereRevision) {
		byte[] bytes = PdfsDePrueba.normal();
		String sha = HuellaComunicado.sha256(bytes);
		return new AdjuntoValidado(bytes, 1, sha, sha, requiereRevision);
	}

	/** Corre la operación como esa persona y deja la sesión vacía. */
	protected <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	protected <T> T como(Usuario quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	protected long contar(String sql, Object... argumentos) {
		Long n = jdbc.queryForObject(sql, Long.class, argumentos);
		return n == null ? 0 : n;
	}
}
