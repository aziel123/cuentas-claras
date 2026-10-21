package pe.edu.virgenmaria.cuentasclaras.academico;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Sprint 9, tanda 2: el escenario de las pruebas de asistencia. 2026 EN_CURSO con 5.° Primaria A (Mateo y Valeria, de la
 * familia de Rosa, y Lucas, de la familia de Pedro) y 5.° B. «ana.docente» es tutora de 5.° A y «luis.docente», docente
 * de 5.° B (las propone Administración y las aprueba Dirección en la bandeja). Los contactos están verificados.
 */
final class EscenarioAsistencia {

	/** Martes 6 de octubre de 2026, 08:00 en Lima (un día de clases). */
	static final Instant MARTES_8 = Instant.parse("2026-10-06T13:00:00Z");

	static final LocalDate MARTES = LocalDate.of(2026, 10, 6);

	static final String MOTIVO = "Corrección pedida por la tutora con su registro en papel";

	record Datos(EscenarioEscolar.Estructura escuela, Long seccion, Long mateo, Long valeria, Long lucas, Long familiaRosa,
			Long rosa, Long familiaPedro, Long pedro, Usuario tutora, Usuario otroDocente) {

		/** Rosa con su cuenta del portal (en H2 la cuenta suplente la crea la prueba de la sesión). */
		UsuarioAutenticado rosaEnLinea() {
			return new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
					EnumSet.of(Rol.APODERADO), rosa);
		}

		UsuarioAutenticado pedroEnLinea() {
			return new UsuarioAutenticado(41L, 1L, "pedro.familia", "Pedro Flores", null, true, false, false,
					EnumSet.of(Rol.APODERADO), pedro);
		}
	}

	private EscenarioAsistencia() {
	}

	static Datos preparar(ServicioEstructura estructura, ServicioAlumnos alumnos, Asignaciones asignaciones,
			BandejaAprobaciones bandeja, UsuarioRepository usuarios, PasswordEncoder codificador, JdbcTemplate jdbc) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		EscenarioEscolar.Estructura escuela = EscenarioEscolar.crearEstructuraConAnioEnCurso(estructura);
		Long seccion = escuela.primaria5A2026();
		Long mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(seccion)).alumnoId();
		Long valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(seccion)).alumnoId();
		Long lucas = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234568", "Flores", "Rojas", "Lucas",
				LocalDate.of(2015, 2, 1), "41234567", "Flores", "Díaz", "Pedro", "987111222", "pedro.flores@gmail.com",
				seccion)).alumnoId();
		EscenarioEscolar.contactosConfirmados(jdbc);
		SecurityContextHolder.clearContext();
		Usuario tutora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "ana.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		Usuario otro = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "luis.docente", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		asignar(asignaciones, bandeja, jdbc, seccion, tutora, TipoAsignacion.TUTOR);
		asignar(asignaciones, bandeja, jdbc, escuela.primaria5B2026(), otro, TipoAsignacion.DOCENTE);
		Long rosa = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = ?", Long.class,
				EscenarioEscolar.DNI_ROSA);
		Long pedro = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '41234567'", Long.class);
		Long familiaRosa = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, rosa);
		Long familiaPedro = jdbc.queryForObject("SELECT familia_id FROM apoderado WHERE id = ?", Long.class, pedro);
		SecurityContextHolder.clearContext();
		return new Datos(escuela, seccion, mateo, valeria, lucas, familiaRosa, rosa, familiaPedro, pedro, tutora, otro);
	}

	/** Administración propone y Dirección aprueba en la bandeja (con su firma). */
	static Long asignar(Asignaciones asignaciones, BandejaAprobaciones bandeja, JdbcTemplate jdbc, Long seccion,
			Usuario docente, TipoAsignacion tipo) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long id = asignaciones.proponer(new ProponerAsignacionRequest(seccion, docente.getId(), tipo, null,
				"Carga horaria aprobada por Dirección"));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		return id;
	}

	/** La hoja con esos estados (los demás alumnos, presentes). */
	static HojaRequest hoja(Map<Long, EstadoAsistencia> estados, List<Long> alumnos) {
		List<MarcaPedida> marcas = new ArrayList<>();
		for (Long alumno : alumnos) {
			marcas.add(new MarcaPedida(alumno, estados.getOrDefault(alumno, EstadoAsistencia.PRESENTE), null));
		}
		return new HojaRequest(marcas, null);
	}

	static Long marcaVigente(JdbcTemplate jdbc, Long alumno, LocalDate fecha) {
		return jdbc.queryForObject("SELECT m.id FROM marca_asistencia m WHERE m.alumno_id = ? AND m.fecha = ? AND NOT "
				+ "EXISTS (SELECT 1 FROM marca_asistencia s WHERE s.reemplaza_id = m.id)", Long.class, alumno, fecha);
	}

	static String estadoVigente(JdbcTemplate jdbc, Long alumno, LocalDate fecha) {
		return jdbc.queryForObject("SELECT estado FROM marca_asistencia WHERE id = ?", String.class,
				marcaVigente(jdbc, alumno, fecha));
	}
}
