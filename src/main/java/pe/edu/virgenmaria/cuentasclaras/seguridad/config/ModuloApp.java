package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.security.core.GrantedAuthority;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.APODERADO;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.DIRECTOR;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.DOCENTE;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR;

/**
 * Matriz ÚNICA de permisos (diseño, sección 8). De aquí salen a la vez las reglas de URL de
 * {@link ConfiguracionSeguridad} y el menú de cada rol: no hay otra lista que mantener.
 * Toda ruta que no esté aquí (ni en las rutas públicas) se niega.
 * <p>
 * Agregar un módulo: una constante nueva. Habilitarlo en el menú: {@code disponible = true}
 * cuando exista su controlador.
 */
public enum ModuloApp {

	INICIO("Inicio", "Tu resumen del día.", "Sprint 1", true, "/inicio",
			new String[] { "/", "/inicio" }, EnumSet.allOf(Rol.class)),

	// TODO(sprint1-tanda3): disponible = true cuando existan UsuarioController (paso 8) y AuditoriaController (paso 9).
	USUARIOS("Usuarios y roles", "Crea cuentas, asigna roles y desactiva accesos.", "Sprint 1", false,
			"/usuarios", new String[] { "/usuarios", "/usuarios/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	AUDITORIA("Bitácora de auditoría", "Revisa quién hizo qué y cuándo, y comprueba que nadie alteró el registro.",
			"Sprint 1", false, "/auditoria", new String[] { "/auditoria", "/auditoria/**" },
			EnumSet.of(PROMOTOR, DIRECTOR)),

	COLEGIO("Colegio", "Año escolar, niveles, grados y secciones.", "Sprint 2", false, "/colegio",
			new String[] { "/colegio", "/colegio/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	ALUMNOS("Alumnos y apoderados", "Fichas de alumnos y familias, e importación desde Excel.", "Sprint 2", false,
			"/alumnos", new String[] { "/alumnos", "/alumnos/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	PENSIONES("Pensiones", "Configura las pensiones y genera el cronograma de cuotas de cada alumno.", "Sprint 2",
			false, "/pensiones", new String[] { "/pensiones", "/pensiones/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	CAJA_COBRO("Caja", "Cobra en segundos, entrega la boleta al momento y cierra tu caja cada día.", "Sprint 3",
			false, "/caja", new String[] { "/caja", "/caja/**" }, EnumSet.of(CAJA)),
	DESCUENTOS("Descuentos y becas", "Solicita descuentos y becas para que Dirección los apruebe.", "Sprint 3", false,
			"/descuentos", new String[] { "/descuentos", "/descuentos/**" }, EnumSet.of(ADMINISTRACION)),
	APROBACIONES("Aprobaciones", "Aprueba o rechaza anulaciones, descuentos y cierres de caja.", "Sprint 3", false,
			"/aprobaciones", new String[] { "/aprobaciones", "/aprobaciones/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),

	FAMILIA("Mi familia", "Revisa lo que debes, paga desde el celular y descarga tus boletas.", "Sprint 4", false,
			"/familia", new String[] { "/familia", "/familia/**" }, EnumSet.of(APODERADO)),

	PANEL("Panel del colegio", "Cuánto entró hoy, la morosidad y las alertas de caja, desde tu celular.", "Sprint 5",
			false, "/panel", new String[] { "/panel", "/panel/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	REPORTES("Reportes", "Reportes de cobranza y exportación a Excel.", "Sprint 5", false, "/reportes",
			new String[] { "/reportes", "/reportes/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	CONCILIACION("Conciliación bancaria", "Cruza los pagos registrados con los movimientos del banco.", "Sprint 6",
			false, "/conciliacion", new String[] { "/conciliacion", "/conciliacion/**" },
			EnumSet.of(PROMOTOR, ADMINISTRACION)),

	ACADEMICO("Académico", "Asistencia, notas por competencias y exportación al SIAGIE.", "Más adelante", false,
			"/academico", new String[] { "/academico", "/academico/**" }, EnumSet.of(DIRECTOR, DOCENTE)),
	COMUNICADOS("Comunicados", "Envía comunicados a las familias con confirmación de lectura.", "Más adelante", false,
			"/comunicados", new String[] { "/comunicados", "/comunicados/**" }, EnumSet.of(PROMOTOR, DIRECTOR, DOCENTE));

	/** Rutas públicas: no exigen sesión. */
	public static final String[] RUTAS_PUBLICAS = { "/login", "/error", "/actuator/health" };

	/** Única ruta para quien inició sesión con una clave temporal (autoridad {@code CLAVE_PENDIENTE}). */
	public static final String RUTA_CAMBIAR_CLAVE = "/cuenta/cambiar-clave";

	private static final String PREFIJO_ROL = "ROLE_";

	private final String etiqueta;

	private final String descripcion;

	private final String etapa;

	private final boolean disponible;

	private final String ruta;

	private final String[] patrones;

	private final Set<Rol> roles;

	ModuloApp(String etiqueta, String descripcion, String etapa, boolean disponible, String ruta, String[] patrones,
			Set<Rol> roles) {
		this.etiqueta = etiqueta;
		this.descripcion = descripcion;
		this.etapa = etapa;
		this.disponible = disponible;
		this.ruta = ruta;
		this.patrones = patrones;
		this.roles = roles;
	}

	/** Módulos que ve quien tiene estas autoridades, en el orden de la matriz. Ignora las que no son roles. */
	public static List<ModuloApp> para(Collection<? extends GrantedAuthority> autoridades) {
		Set<Rol> rolesDelUsuario = rolesDe(autoridades);
		return Arrays.stream(values()).filter(m -> m.roles.stream().anyMatch(rolesDelUsuario::contains)).toList();
	}

	/**
	 * Roles a partir de las autoridades. Solo cuentan las {@code ROLE_*}: Spring Security agrega
	 * otras (por ejemplo {@code FACTOR_PASSWORD}) y la clave pendiente es {@code CLAVE_PENDIENTE}.
	 */
	public static Set<Rol> rolesDe(Collection<? extends GrantedAuthority> autoridades) {
		Set<Rol> roles = EnumSet.noneOf(Rol.class);
		for (GrantedAuthority autoridad : autoridades) {
			String nombre = autoridad.getAuthority();
			if (nombre != null && nombre.startsWith(PREFIJO_ROL)) {
				Arrays.stream(Rol.values()).filter(r -> r.autoridad().equals(nombre)).forEach(roles::add);
			}
		}
		return roles;
	}

	public boolean permite(Rol rol) {
		return roles.contains(rol);
	}

	public String etiqueta() {
		return etiqueta;
	}

	public String descripcion() {
		return descripcion;
	}

	/** "Sprint 2", "Más adelante"... para el aviso de "Próximamente". */
	public String etapa() {
		return etapa;
	}

	public boolean disponible() {
		return disponible;
	}

	public String ruta() {
		return ruta;
	}

	public String[] patrones() {
		return patrones.clone();
	}

	/** Roles sin el prefijo {@code ROLE_}, como los espera {@code hasAnyRole(...)}. */
	public String[] nombresDeRoles() {
		return roles.stream().map(Rol::name).toArray(String[]::new);
	}

	public Set<Rol> roles() {
		return EnumSet.copyOf(roles);
	}
}
