package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

/**
 * Sprint 9, tanda 1 (MySQL real): un colegio NUEVO con su equipo por el camino legítimo ({@link ColegioConEquipoMySql}),
 * el año 2027 del alta con dos secciones (3.° y 4.° de Primaria A) creadas por su Administración, y dos cuentas DOCENTE.
 * Así las asignaciones de prueba no tocan el colegio 1 que usan las demás pruebas de MySQL.
 */
final class EscuelaConDocentesMySql {

	record Escuela(ColegioConEquipoMySql.Equipo equipo, long anioId, long seccionA, long seccionB, Usuario docente,
			Usuario otroDocente) {

		long colegioId() {
			return equipo.colegioId();
		}
	}

	private EscuelaConDocentesMySql() {
	}

	static Escuela crear(String sufijo, String ruc, String celular, AltaColegio alta, ServicioUsuarios servicioUsuarios,
			UsuarioRepository usuarios, PasswordEncoder codificador, ColegioRepository colegios,
			ServicioEstructura estructura, org.springframework.jdbc.core.JdbcTemplate jdbc) {
		ColegioConEquipoMySql.Equipo equipo = ColegioConEquipoMySql.crear(sufijo, ruc, celular, alta, servicioUsuarios,
				usuarios, codificador, colegios);
		// Sprint 11, tanda 1 (sección 3.7): el alta ya no crea el año; lo crea la Dirección con su firma (2027, en curso).
		UsuariosDePrueba.iniciarSesion(equipo.directora());
		long anio = estructura.crearAnio(new pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest(2027,
				java.time.LocalDate.of(2027, 3, 1), java.time.LocalDate.of(2027, 12, 17), true));
		UsuariosDePrueba.iniciarSesion(equipo.administracion());
		long a = estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_3, "A"));
		long b = estructura.crearSeccion(anio, new CrearSeccionRequest(Grado.PRIMARIA_4, "A"));
		SecurityContextHolder.clearContext();
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, equipo.colegioId(), "docente.s9." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		Usuario otro = UsuariosDePrueba.guardar(usuarios, codificador, equipo.colegioId(), "docente2.s9." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DOCENTE);
		return new Escuela(equipo, anio, a, b, docente, otro);
	}
}
