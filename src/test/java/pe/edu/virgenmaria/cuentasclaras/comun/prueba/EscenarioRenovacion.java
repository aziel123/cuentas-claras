package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Sprint 5, tanda 2: 2026 EN CURSO y 2027 PLANIFICADO (la matrícula de 2027 nace RESERVADA). Rosa es la responsable de
 * Mateo (5.° B 2026) y Valeria (2.° B 2026); Pedro, de Sebastián (5.° de secundaria A 2026, no se le propone). En 2027
 * hay 6.° A y B de primaria y 3.° A (no hay 3.° B: Valeria va a la primera). El plan 2027 de primaria cobra S/ 300 de
 * matrícula y S/ 450 de pensión. Con el reloj de {@link ConfiguracionRelojAjustable} hoy es el 02/10/2026.
 */
public final class EscenarioRenovacion {

	public static final LocalDate RESPONDEN_HASTA = LocalDate.of(2027, 1, 31);

	public record Datos(Long anio2026, Long anio2027, Long quispe, Long mateo, Long valeria, Long rosa, Long flores,
			Long sebastian, Long pedro, Long p6A2027, Long p6B2027, Long p3A2027) {

		/** Rosa con su cuenta en línea (el usuario 40 no existe en la base: en H2 no hay triggers que lo pidan). */
		public UsuarioAutenticado rosaEnLinea() {
			return new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
					EnumSet.of(Rol.APODERADO), rosa);
		}

		public UsuarioAutenticado pedroEnLinea() {
			return new UsuarioAutenticado(41L, 1L, "pedro.familia", "Pedro Flores", null, true, false, false,
					EnumSet.of(Rol.APODERADO), pedro);
		}
	}

	private EscenarioRenovacion() {
	}

	/** Lo arma Administración; el plan 2027 lo aprueba Dirección. Deja la sesión en Administración. */
	public static Datos preparar(ServicioEstructura estructura, ServicioAlumnos alumnos, ServicioPlanesPension planes,
			JdbcTemplate jdbc, boolean conPlan2027) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		EscenarioEscolar.Estructura escuela = EscenarioEscolar.crearEstructuraConAnioEnCurso(estructura);
		Long p6B = estructura.crearSeccion(escuela.anio2027(), new CrearSeccionRequest(Grado.PRIMARIA_6, "B"));
		Long p3A = estructura.crearSeccion(escuela.anio2027(), new CrearSeccionRequest(Grado.PRIMARIA_3, "A"));
		Long s5A = estructura.crearSeccion(escuela.anio2026(), new CrearSeccionRequest(Grado.SECUNDARIA_5, "A"));
		var mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria5B2026()));
		var valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));
		var sebastian = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(EscenarioCaja.DNI_SEBASTIAN, "Flores", "Rojas",
				"Sebastián", LocalDate.of(2009, 8, 21), EscenarioCaja.DNI_PEDRO, "Flores", "Díaz", "Pedro", "912345678",
				null, s5A));
		if (conPlan2027) {
			EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027, Nivel.PRIMARIA, "450", "300", null);
		}
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, mateo.alumnoId());
		Long pedro = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				sebastian.alumnoId());
		return new Datos(escuela.anio2026(), escuela.anio2027(), mateo.familiaId(), mateo.alumnoId(), valeria.alumnoId(),
				rosa, sebastian.familiaId(), sebastian.alumnoId(), pedro, escuela.primaria6A2027(), p6B, p3A);
	}

	public static Long renovacionDe(JdbcTemplate jdbc, Long alumnoId) {
		return jdbc.queryForObject("SELECT id FROM renovacion_matricula WHERE alumno_id = ?", Long.class, alumnoId);
	}
}
