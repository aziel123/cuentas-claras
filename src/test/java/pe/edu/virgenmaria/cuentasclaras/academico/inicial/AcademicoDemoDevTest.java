package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.DatosDemoColegioDevDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PersonasDemoDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.inicial.ComunicadosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.comunicados.inicial.EjemploComunicados;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El arranque de desarrollo deja lo académico listo para una demostración en vivo, todo por los servicios del colegio:
 * la evaluación por bimestres, las asignaciones de «docente» con áreas del catálogo aprobadas por otra persona, el I
 * bimestre cerrado con notas y libretas (también de los hijos de la cuenta «apoderado», con la versión 2 de Mateo), el II
 * abierto, asistencia de días pasados y de hoy, y un comunicado enviado y otro con PDF por aprobar.
 */
@PruebaIntegracion
@Import({ ConfiguracionRelojAjustable.class, DatosDemoColegioDevDePrueba.class })
class AcademicoDemoDevTest {

	/** Mateo Quispe Huamán y Valeria Quispe Huamán, hijos de Rosa Huamán Ccori (cuenta «apoderado»). */
	private static final String MATEO = "78451236";

	private static final String VALERIA = "80127745";

	@Autowired
	private DatosDemoColegioDevDePrueba colegioDemo;

	@Autowired
	private AutowireCapableBeanFactory fabrica;

	@Autowired
	private PersonaDemo personaDemo;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		// Miércoles 21 de octubre de 2026, 09:00 en Lima: el I bimestre ya terminó y hoy hay clases.
		reloj.fijar(Instant.parse("2026-10-21T14:00:00Z"));
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void dejaLoAcademicoListoPorLosServiciosDelColegio() {
		assertThat(colegioDemo.crear()).isTrue();
		assertThat(demo("jdbc:h2:mem:demo").crearSiCorresponde()).isTrue();
		assertThat(new ComunicadosDemoDev(fabrica.createBean(EjemploComunicados.class), personaDemo, "jdbc:h2:mem:demo",
				true).crearSiCorresponde()).isTrue();

		// La evaluación por bimestres: el I cerrado y el II abierto.
		assertThat(jdbc.queryForList("SELECT estado FROM periodo_evaluacion WHERE colegio_id = ? ORDER BY numero",
				String.class, DatosDemoDev.COLEGIO_PRINCIPAL)).containsExactly("CERRADO", "ABIERTO");
		// Las tres asignaciones de «docente», con sus 7 áreas del catálogo, propuestas por una persona y aprobadas por otra.
		assertThat(jdbc.queryForList("SELECT CONCAT(s.grado, ' ', a.tipo, ' ', a.estado, ' ', a.propuesto_por, '→', "
				+ "a.resuelto_por) FROM asignacion_docente a JOIN seccion s ON s.id = a.seccion_id JOIN usuario u ON "
				+ "u.id = a.usuario_id WHERE u.nombre_usuario = 'docente' ORDER BY s.grado", String.class))
				.containsExactly("PRIMARIA_3 TUTOR VIGENTE administracion→director",
						"PRIMARIA_4 DOCENTE VIGENTE administracion→director",
						"PRIMARIA_5 DOCENTE VIGENTE administracion→director");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_area", Long.class)).isEqualTo(7L);
		// Notas del I bimestre en las tres secciones; el II, sin notas (la hoja lista para calificar).
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT c.seccion_id) FROM calificacion c JOIN periodo_evaluacion p "
				+ "ON p.id = c.periodo_id WHERE p.numero = 1", Long.class)).isEqualTo(3L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calificacion c JOIN periodo_evaluacion p ON p.id = "
				+ "c.periodo_id WHERE p.numero = 2", Long.class)).isZero();
		// Libretas de los hijos de la cuenta «apoderado»; la de Mateo, en versión 2 por la corrección que aprobó Promotoría.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida l JOIN alumno a ON a.id = l.alumno_id WHERE "
				+ "a.numero_documento IN (?, ?) AND l.numero_version = 1 AND l.creado_por = 'sistema.libretas'", Long.class,
				MATEO, VALERIA)).isEqualTo(2L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida l JOIN alumno a ON a.id = l.alumno_id WHERE "
				+ "a.numero_documento = ? AND l.numero_version = 2 AND l.motivo = 'CORRECCION'", Long.class, MATEO))
				.isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT resuelto_por FROM solicitud_cambio WHERE tipo = 'CORRECCION_CALIFICACION'",
				String.class)).isEqualTo("promotor");
		// Asistencia: 3 días pasados (registro tardío aprobado por «director») en 3 secciones y hoy en 3.° Primaria A.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_asistencia WHERE solicitud_id IS NOT NULL",
				Long.class)).isEqualTo(9L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'REGISTRO_ASISTENCIA_TARDIO' "
				+ "AND estado = 'APROBADA' AND solicitado_por = 'docente' AND resuelto_por = 'director'", Long.class))
				.isEqualTo(9L);
		assertThat(jdbc.queryForList("SELECT CONCAT(s.grado, ' ', r.registrado_por) FROM registro_asistencia r JOIN seccion s "
				+ "ON s.id = r.seccion_id WHERE r.fecha = '2026-10-21'", String.class)).containsExactly("PRIMARIA_3 docente");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marca_asistencia WHERE estado <> 'PRESENTE'", Long.class))
				.isPositive();
		// Un comunicado enviado (la tutora) y uno con PDF por aprobar (Dirección; lo aprueba Promotoría en la bandeja).
		assertThat(jdbc.queryForList("SELECT CONCAT(c.emisor_rol, ' ', c.estado, ' ', COALESCE(s.estado, '-')) FROM "
				+ "comunicado c LEFT JOIN solicitud_cambio s ON s.id = c.solicitud_id ORDER BY c.id", String.class))
				.containsExactly("DOCENTE ENVIADO -", "DIRECCION POR_APROBAR PENDIENTE");
		// Todo quedó en la bitácora a nombre de quien lo hizo.
		assertThat(jdbc.queryForList("SELECT DISTINCT nombre_usuario FROM evento_auditoria WHERE accion IN "
				+ "('ASIGNACION_DOCENTE_PROPUESTA', 'ASISTENCIA_REGISTRADA', 'LIBRETAS_EMITIDAS') ORDER BY nombre_usuario",
				String.class)).containsExactly("administracion", "docente", "sistema.libretas");
	}

	@Test
	void noCargaFueraDeH2EnMemoria() {
		assertThat(colegioDemo.crear()).isTrue();
		assertThat(demo("jdbc:mysql://localhost/cuentasclaras").crearSiCorresponde()).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_docente", Long.class)).isZero();
	}

	private AcademicoDemoDev demo(String url) {
		PersonasDemoDePrueba.asegurar(usuarios, codificador, jdbc, "promotor", "director", "administracion");
		if (jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = 'docente'", Long.class) == 0) {
			UsuariosDePrueba.guardar(usuarios, codificador, DatosDemoDev.COLEGIO_PRINCIPAL, "docente", UsuariosDePrueba.CLAVE,
					false, Rol.DOCENTE);
		}
		// Los beans compartidos con la demo son de los perfiles dev y demo: aquí se arman con los servicios del contexto.
		return new AcademicoDemoDev(fabrica.createBean(EjemploAcademico.class), personaDemo, reloj, url, true);
	}
}
