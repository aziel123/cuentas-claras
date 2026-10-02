package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistrarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Datos escolares para las pruebas del sprint 2: años, secciones y solicitudes de registro. Los DNI y celulares son
 * ficticios. Con el reloj de {@link ConfiguracionRelojAjustable} hoy es el 02/10/2026.
 */
public final class EscenarioEscolar {

	public static final String DNI_MATEO = "78451236";

	public static final String DNI_VALERIA = "80127745";

	public static final String DNI_ROSA = "45678912";

	public static final String CELULAR_ROSA = "987654321";

	public static final String CORREO_ROSA = "rosa.huaman@gmail.com";

	public static final LocalDate NACIMIENTO_MATEO = LocalDate.of(2015, 6, 14);

	public static final LocalDate NACIMIENTO_VALERIA = LocalDate.of(2018, 9, 3);

	public static final String MOTIVO = "Lo pidió la familia en secretaría";

	private EscenarioEscolar() {
	}

	/** Años y secciones creados por una persona de Administración (ya en sesión). */
	public record Estructura(Long anio2026, Long anio2027, Long primaria5A2026, Long primaria5B2026, Long primaria2B2026,
			Long secundaria1A2026, Long primaria6A2027) {
	}

	public static Estructura crearEstructura(ServicioEstructura estructura) {
		Long a2026 = estructura.crearAnio(new CrearAnioEscolarRequest(2026, LocalDate.of(2026, 3, 2),
				LocalDate.of(2026, 12, 18), true));
		Long a2027 = estructura.crearAnio(new CrearAnioEscolarRequest(2027, LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 12, 17), false));
		return new Estructura(a2026, a2027,
				estructura.crearSeccion(a2026, new CrearSeccionRequest(Grado.PRIMARIA_5, "A")),
				estructura.crearSeccion(a2026, new CrearSeccionRequest(Grado.PRIMARIA_5, "B")),
				estructura.crearSeccion(a2026, new CrearSeccionRequest(Grado.PRIMARIA_2, "B")),
				estructura.crearSeccion(a2026, new CrearSeccionRequest(Grado.SECUNDARIA_1, "A")),
				estructura.crearSeccion(a2027, new CrearSeccionRequest(Grado.PRIMARIA_6, "A")));
	}

	/** Mateo Quispe Huamán con su madre Rosa como apoderada nueva (con celular y correo). */
	public static RegistrarAlumnoRequest mateoConRosa(Long seccionId) {
		return conApoderadoNuevo(DNI_MATEO, "Quispe", "Huamán", "Mateo", NACIMIENTO_MATEO, DNI_ROSA, "Huamán", "Ccori",
				"Rosa", CELULAR_ROSA, CORREO_ROSA, seccionId);
	}

	/** Valeria, hermana de Mateo: su responsable es Rosa, ya registrada (por su DNI). */
	public static RegistrarAlumnoRequest valeriaConRosaRegistrada(Long seccionId) {
		return conApoderadoRegistrado(DNI_VALERIA, "Quispe", "Huamán", "Valeria", NACIMIENTO_VALERIA, DNI_ROSA, seccionId);
	}

	public static RegistrarAlumnoRequest conApoderadoNuevo(String dni, String paterno, String materno, String nombres,
			LocalDate nacimiento, String dniApoderado, String paternoApoderado, String maternoApoderado,
			String nombresApoderado, String celular, String correo, Long seccionId) {
		return new RegistrarAlumnoRequest(TipoDocumento.DNI, dni, paterno, materno, nombres, nacimiento, null, null,
				TipoDocumento.DNI, dniApoderado, paternoApoderado, maternoApoderado, nombresApoderado, Parentesco.MADRE,
				celular, correo, seccionId, null);
	}

	public static RegistrarAlumnoRequest conApoderadoRegistrado(String dni, String paterno, String materno,
			String nombres, LocalDate nacimiento, String dniApoderado, Long seccionId) {
		return new RegistrarAlumnoRequest(TipoDocumento.DNI, dni, paterno, materno, nombres, nacimiento, null,
				dniApoderado, null, null, null, null, null, null, null, null, seccionId, null);
	}

	public static ApoderadoRequest apoderado(String dni, String paterno, String nombres, Parentesco parentesco,
			String celular, String correo, String motivo) {
		return new ApoderadoRequest(TipoDocumento.DNI, dni, paterno, null, nombres, parentesco, celular, correo, motivo);
	}

	/** Toda la bitácora en un solo texto: para comprobar que no guarda datos personales completos. */
	public static String todaLaBitacora(JdbcTemplate jdbc) {
		List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM evento_auditoria");
		StringBuilder texto = new StringBuilder();
		filas.forEach(f -> f.values().forEach(v -> texto.append(v).append(" | ")));
		return texto.toString();
	}

	public static Long ultimoId(JdbcTemplate jdbc, String tabla) {
		return jdbc.queryForObject("SELECT MAX(id) FROM " + tabla, Long.class);
	}

	public static Map<String, Object> ultimoEvento(JdbcTemplate jdbc, String accion) {
		return jdbc.queryForMap("SELECT * FROM evento_auditoria WHERE accion = ? ORDER BY secuencia DESC LIMIT 1", accion);
	}

	public static long contar(JdbcTemplate jdbc, String tabla) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
	}
}
