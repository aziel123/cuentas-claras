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

	USUARIOS("Usuarios y roles", "Crea cuentas, asigna roles y desactiva accesos.", "Sprint 1", true,
			"/usuarios", new String[] { "/usuarios", "/usuarios/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	AUDITORIA("Bitácora de auditoría", "Revisa quién hizo qué y cuándo, y comprueba que nadie alteró el registro.",
			"Sprint 1", true, "/auditoria", new String[] { "/auditoria", "/auditoria/**" },
			EnumSet.of(PROMOTOR, DIRECTOR)),

	COLEGIO("Colegio", "Años escolares, grados y secciones.", "Sprint 2", true, "/colegio",
			new String[] { "/colegio", "/colegio/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	ALUMNOS("Alumnos y apoderados", "Fichas de alumnos y familias, y matrícula en cada año.", "Sprint 2", true,
			"/alumnos", new String[] { "/alumnos", "/alumnos/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	PENSIONES("Pensiones", "Configura las pensiones y genera el cronograma de cuotas de cada alumno.", "Sprint 2",
			true, "/pensiones", new String[] { "/pensiones", "/pensiones/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	CAJA_COBRO("Caja", "Cobra en segundos, entrega la boleta al momento y cierra tu caja cada día.", "Sprint 3",
			true, "/caja", new String[] { "/caja", "/caja/**" }, EnumSet.of(CAJA)),
	DESCUENTOS("Descuentos y becas", "Solicita descuentos y becas para que Dirección los apruebe.", "Sprint 3", true,
			"/descuentos", new String[] { "/descuentos", "/descuentos/**" }, EnumSet.of(ADMINISTRACION)),
	APROBACIONES("Aprobaciones", "Aprueba cierres de caja, anulaciones de pagos, descuentos y becas, retiros, "
			+ "cambios de contacto e ingresos tardíos; mira las cajas del día.",
			"Sprint 2", true,
			"/aprobaciones", new String[] { "/aprobaciones", "/aprobaciones/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),

	FAMILIA("Mi familia", "Revisa lo que debes, paga desde el celular y descarga tus boletas.", "Sprint 4 (pago en línea)",
			true, "/familia", new String[] { "/familia", "/familia/**" }, EnumSet.of(APODERADO)),
	PAGOS_EN_LINEA("Pagos en línea", "Pagos que entran solos por la pasarela: los que quedaron por revisar, los de hoy y "
			+ "los vencidos.", "Sprint 4", true, "/pagos-en-linea", new String[] { "/pagos-en-linea", "/pagos-en-linea/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	RECAUDACION("Recaudación bancaria", "Pagos que las familias hacen en el banco con el código del alumno: sube el archivo "
			+ "del banco, otra persona lo confirma a ciegas y el sistema registra los pagos.", "Sprint 4", true,
			"/recaudacion", new String[] { "/recaudacion", "/recaudacion/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	COMPROBANTES("Comprobantes electrónicos", "Envíos al OSE: rechazados, por vencer el plazo legal, pendientes y aceptados.",
			"Sprint 4", true, "/comprobantes", new String[] { "/comprobantes", "/comprobantes/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	MENSAJES("Mensajes a las familias", "Avisos de pago, anulación y descuento que salen solos por WhatsApp o correo: los "
			+ "que fallaron, los pendientes y los de hoy.", "Sprint 5", true, "/mensajes",
			new String[] { "/mensajes", "/mensajes/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	AVISOS_FAMILIAS("Avisos de las familias", "Lo que las familias reportan desde el portal: pagos que no aparecen o "
			+ "cobros que no reconocen. Solo Promotoría y Dirección.", "Sprint 5", true, "/avisos-familias",
			new String[] { "/avisos-familias", "/avisos-familias/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	MATRICULA_2027("Renovación de matrícula", "Cada familia confirma si su hijo continúa el próximo año; al pagar la "
			+ "matrícula se generan sus pensiones.", "Sprint 5", true, "/matricula-2027",
			new String[] { "/matricula-2027", "/matricula-2027/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	FERIADOS("Feriados", "Feriados nacionales y días no laborables del colegio: no cuentan como hábiles en las alertas ni "
			+ "salen recordatorios. Solo Promotoría y Dirección registran días nuevos.", "Sprint 5", true, "/feriados",
			new String[] { "/feriados", "/feriados/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION, CAJA)),

	// Sprint 6 (decisión 64). REPORTES va ANTES que PANEL: las reglas de URL se aplican en el orden de la matriz y la
	// primera que coincide gana. Morosidad e ingresos en pantalla: Promotoría, Dirección y Administración (el Excel, solo
	// Promotoría y Administración: lo exige ExportacionContador). El panel /panel: solo Promotoría.
	REPORTES("Reportes", "Morosidad por grado, ingresos por medio de pago y familias morosas; Excel para el contador.",
			"Sprint 6", true, "/panel/reportes",
			new String[] { "/panel/morosos", "/panel/reportes", "/panel/reportes/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	// Sprint 6, tanda 3 (decisión 77): la llamada de control la hacen Promotoría o Dirección. Va ANTES que PANEL (la
	// primera regla que coincide gana: /panel/** es solo de Promotoría).
	LLAMADAS_CONTROL("Llamadas de control", "Cada semana, llama a las familias que el sistema eligió al azar entre las que "
			+ "pagaron en efectivo o tienen deuda vencida, y compara lo que te dicen con lo registrado.", "Sprint 6", true, "/panel/llamadas",
			new String[] { "/panel/llamadas", "/panel/llamadas/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	PANEL("Panel del colegio", "Cuánto entró hoy y en el mes, la deuda vencida y las alertas, desde tu celular.",
			"Sprint 6", true, "/panel", new String[] { "/panel", "/panel/**" }, EnumSet.of(PROMOTOR)),

	// Sprint 6, tanda 2 (P6, decisión 78): cada persona del personal pide el cambio de SU celular o correo (lo aprueba otra
	// persona). Promotoría lo pide para otra desde /usuarios/{id}/contacto (cubierto por USUARIOS).
	MI_CONTACTO("Mi celular y correo", "Pide cambiar el celular o el correo donde te llegan los avisos de tu cuenta: lo "
			+ "aprueba otra persona.", "Sprint 6", true, ModuloApp.RUTA_MI_CONTACTO,
			new String[] { ModuloApp.RUTA_MI_CONTACTO }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION, CAJA, DOCENTE)),

	CONCILIACION("Conciliación bancaria", "Sube el extracto del banco cada día: el sistema empareja solo los Yape, "
			+ "depósitos, pagos en línea y recaudación, y te muestra solo las diferencias.",
			"Sprint 4 (automática)", true, "/conciliacion", new String[] { "/conciliacion", "/conciliacion/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),

	ACADEMICO("Académico", "Asistencia, notas por competencias y exportación al SIAGIE.", "Más adelante", false,
			"/academico", new String[] { "/academico", "/academico/**" }, EnumSet.of(DIRECTOR, DOCENTE)),
	COMUNICADOS("Comunicados", "Envía comunicados a las familias con confirmación de lectura.", "Más adelante", false,
			"/comunicados", new String[] { "/comunicados", "/comunicados/**" }, EnumSet.of(PROMOTOR, DIRECTOR, DOCENTE));

	/** Rutas públicas: no exigen sesión. */
	public static final String[] RUTAS_PUBLICAS = { "/login", "/error", "/actuator/health", "/activar/*/*", "/verificar/*/*",
			// Sprint 7, tanda 1: sondas de vida y de disponibilidad (solo el estado) y el vigilante externo del respaldo.
			"/actuator/health/liveness", "/actuator/health/readiness", "/salud/respaldo" };

	/**
	 * Sprint 4: avisos (webhooks) de la pasarela. Sin sesión ni CSRF, SOLO por POST y en una cadena de seguridad aparte:
	 * cada aviso se autentica con su firma y nunca registra dinero por sí mismo (se consulta a la pasarela).
	 */
	public static final String RUTAS_WEBHOOK = "/webhooks/**";

	public static final String RUTA_WEBHOOK_PASARELA = "/webhooks/pasarela/*/*";

	/** Sprint 5: avisos de estado de WhatsApp (POST firmado) y su verificación (GET con hub.challenge). */
	public static final String RUTA_WEBHOOK_WHATSAPP = "/webhooks/whatsapp/*";

	/** Sprint 6, tanda 2: pedir el cambio del propio celular o correo (personal). */
	public static final String RUTA_MI_CONTACTO = "/cuenta/contacto";

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
