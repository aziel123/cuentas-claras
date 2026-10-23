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
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.OPERADOR_PLATAFORMA;
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

	// Sprint 8, tanda 1: el operador de la plataforma entra a /inicio (y a /) solo para que lo reenvíen a /plataforma.
	INICIO("Inicio", "Tu resumen del día.", "Sprint 1", true, "/inicio",
			new String[] { "/", "/inicio" }, EnumSet.allOf(Rol.class)),

	// Sprint 8, tanda 1 (sección 3.2): la plataforma es SOLO del operador, y el operador no tiene ninguna otra ruta (salvo
	// cambiar su clave y salir): nunca ve datos de las familias. Va primero: ninguna otra regla cubre /plataforma/**.
	PLATAFORMA("Colegios de la plataforma", "Da de alta un colegio con su primera Promotoría, corrige su ficha y registra "
			+ "su correo externo de control. Nunca muestra datos de las familias.", "Sprint 8", true, "/plataforma",
			new String[] { "/plataforma", "/plataforma/**" }, EnumSet.of(OPERADOR_PLATAFORMA)),

	USUARIOS("Usuarios y roles", "Crea cuentas, asigna roles y desactiva accesos.", "Sprint 1", true,
			"/usuarios", new String[] { "/usuarios", "/usuarios/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	// Sprint 7, tanda 3 (Ley 29733, sección 8.2): quién vio datos personales y los datos con plazo vencido, SOLO Promotoría.
	// Va ANTES que AUDITORIA (la primera regla que coincide gana: /auditoria/** también es de Dirección).
	ACCESOS_DATOS("Quién vio datos personales", "Quién del colegio abrió la ficha de una familia o un alumno, buscó alumnos o "
			+ "vio a los morosos, y los contactos con el plazo de conservación vencido.", "Sprint 7", true,
			"/auditoria/accesos", new String[] { "/auditoria/accesos", "/auditoria/datos-vencidos" },
			EnumSet.of(PROMOTOR)),
	AUDITORIA("Bitácora de auditoría", "Revisa quién hizo qué y cuándo, y comprueba que nadie alteró el registro.",
			"Sprint 1", true, "/auditoria", new String[] { "/auditoria", "/auditoria/**" },
			EnumSet.of(PROMOTOR, DIRECTOR)),

	// Sprint 8, tanda 1 (sección 3.7, decisión 123): la marca del colegio (razón social, dirección, correo y logo), solo
	// Promotoría. Va ANTES que COLEGIO (/colegio/** es también de Dirección y Administración: la primera regla gana).
	MARCA_COLEGIO("Marca del colegio", "Razón social, RUC, dirección y logo: lo que ven las familias en su portal y en "
			+ "sus boletas.", "Sprint 8", true, "/colegio/marca", new String[] { "/colegio/marca", "/colegio/marca/**" },
			EnumSet.of(PROMOTOR)),
	// Sprint 8, tanda 1 (sección 3.4): el estado del colegio y el paso a producción (con la firma de Promotoría). La lista
	// de verificación completa llega en la tanda 2.
	PUESTA_EN_MARCHA("Puesta en marcha", "Lo que falta para que el colegio empiece a cobrar, y el paso a producción.",
			"Sprint 8", true, "/puesta-en-marcha", new String[] { "/puesta-en-marcha", "/puesta-en-marcha/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	// Sprint 8, tanda 2 (sección 3.6): los conectores del colegio (OSE, pasarela, cuenta bancaria y recaudación). Proponer
	// es de Promotoría o Administración y aprobar, de otra persona de Promotoría o Dirección (en la bandeja); Dirección
	// los ve. Los servicios lo exigen también con @PreAuthorize.
	CONECTORES("Conectores", "Adónde entra el dinero del colegio y quién emite sus boletas: cada cambio lo aprueba otra "
			+ "persona.", "Sprint 8", true, "/conectores", new String[] { "/conectores", "/conectores/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	COLEGIO("Colegio", "Años escolares, grados y secciones.", "Sprint 2", true, "/colegio",
			new String[] { "/colegio", "/colegio/**" }, EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	// Sprint 9, tanda 2 (E38): el consentimiento de la ficha de matrícula firmada lo registran Administración o Dirección
	// (lo exige el servicio). Va ANTES que ALUMNOS (que también es de Promotoría).
	CONSENTIMIENTOS_FICHA("Consentimientos de la ficha", "Registra el «sí» a los avisos de asistencia que la familia firmó "
			+ "en la ficha de matrícula.", "Sprint 9", true, "/alumnos/consentimientos",
			new String[] { "/alumnos/consentimientos" }, EnumSet.of(DIRECTOR, ADMINISTRACION)),
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

	// Sprint 9, tanda 1 (sección 3.2 y 10): las asignaciones de docentes, tutores y auxiliares a las secciones. Ver y
	// proponer: Administración y Dirección (los servicios lo exigen también); terminar, Dirección y Promotoría; aprobar, otra
	// persona en la bandeja. Va ANTES que ACADEMICO (la primera regla que coincide gana: /academico/** es de Dirección y
	// Docente, y el docente no ve las asignaciones de los demás).
	ASIGNACIONES("Docentes y secciones", "Quién es tutor, docente o auxiliar de cada sección: lo propone Administración o "
			+ "Dirección y lo aprueba otra persona.", "Sprint 9", true, "/academico/asignaciones",
			new String[] { "/academico/asignaciones", "/academico/asignaciones/**" },
			EnumSet.of(PROMOTOR, DIRECTOR, ADMINISTRACION)),
	// Sprint 9, tanda 3 (sección 3.11, E34, B14): la nómina para el SIAGIE (DNI y fechas de nacimiento de menores) la saca
	// SOLO Dirección, con su sesión; los datos que faltan y los códigos modulares, también. Va ANTES que ACADEMICO.
	SIAGIE("Nómina para el SIAGIE", "Los datos que faltan para la matrícula en el SIAGIE, los códigos modulares y la nómina "
			+ "provisional de cada sección para copiar en la plantilla del SIAGIE.", "Sprint 9", true, "/academico/siagie",
			new String[] { "/academico/siagie", "/academico/siagie/**" }, EnumSet.of(DIRECTOR)),
	// Sprint 9, tanda 2 (sección 10, A6): las horas y plazos de la asistencia los cambia SOLO Dirección, con su firma. Va
	// ANTES que ACADEMICO (la primera regla que coincide gana: /academico/** es también del docente).
	CONFIGURACION_ACADEMICA("Horas de la asistencia", "A qué hora sale la alerta de secciones sin asistencia y el aviso a "
			+ "las familias, y los plazos para justificar y registrar tarde. Solo Dirección, con su firma.", "Sprint 9",
			true, "/academico/configuracion", new String[] { "/academico/configuracion" }, EnumSet.of(DIRECTOR)),
	// Sprint 9, tanda 2 (E5, E13): Dirección no registra ni corrige marcas (decisión 144): ve los cambios del día y revierte
	// las justificaciones con su firma. Va ANTES que ASISTENCIA (que es solo del docente).
	ASISTENCIA_DIRECCION("Cambios de la asistencia", "Las correcciones y justificaciones del día, resaltando las de después "
			+ "del aviso a las familias; revierte una justificación con tu firma.", "Sprint 9", true,
			"/academico/asistencia/cambios", new String[] { "/academico/asistencia/cambios",
					"/academico/asistencia/marcas/*/revertir-justificacion" }, EnumSet.of(DIRECTOR)),
	// Sprint 9, tanda 2: el reporte mensual lo ven el tutor (lo exige el servicio) y Dirección.
	REPORTE_ASISTENCIA("Reporte de asistencia", "Por sección y mes: presentes, tardanzas, faltas y faltas justificadas de "
			+ "cada alumno.", "Sprint 9", true, "/academico/asistencia/reporte",
			new String[] { "/academico/asistencia/reporte" }, EnumSet.of(DIRECTOR, DOCENTE)),
	// Sprint 9, tanda 2 (decisión 144): la hoja del día, la corrección y las solicitudes son SOLO del docente (E49: Dirección,
	// 403). Va ANTES que ACADEMICO.
	ASISTENCIA("Asistencia de hoy", "Marca la asistencia de tu sección en un minuto: todos en «Presente» y un toque por "
			+ "alumno.", "Sprint 9", true, "/academico/asistencia",
			new String[] { "/academico/asistencia", "/academico/asistencia/**" }, EnumSet.of(DOCENTE)),
	// Sprint 10, tanda 1 (sección 8.3): cómo se evalúa el año. La leen Dirección, Administración y Promotoría; la guarda SOLO
	// Dirección con su firma (lo exige el servicio: 403 para los demás). Va ANTES que ACADEMICO.
	EVALUACION("Evaluación del año", "Con qué currículo se califica, cómo se divide el año y desde cuándo se cierra cada "
			+ "periodo. Solo Dirección la guarda, con su firma.", "Sprint 10", true, "/academico/evaluacion/configuracion",
			new String[] { "/academico/evaluacion", "/academico/evaluacion/**" }, EnumSet.of(PROMOTOR, DIRECTOR,
					ADMINISTRACION)),
	// Sprint 10, tanda 1: los periodos de evaluación (programar, mover fechas, abrir antes y cerrar), SOLO Dirección con su
	// firma; abren solos en su fecha (sistema.academico). Va ANTES que ACADEMICO.
	PERIODOS("Periodos de evaluación", "Programa los bimestres del año, ábrelos antes de tiempo si hace falta y ciérralos "
			+ "con tu firma. Un periodo cerrado no se reabre.", "Sprint 10", true, "/academico/periodos",
			new String[] { "/academico/periodos", "/academico/periodos/**" }, EnumSet.of(DIRECTOR)),
	// Sprint 10, tanda 1: el catálogo nacional del currículo, de solo lectura, para el personal académico y de gobierno (sin
	// datos personales). Caja no: solo entra a caja (mínimo acceso). Va ANTES que ACADEMICO.
	CURRICULO("Currículo nacional", "Las áreas, competencias y la escala de calificación del Currículo Nacional, de solo "
			+ "lectura.", "Sprint 10", true, "/academico/curriculo",
			new String[] { "/academico/curriculo", "/academico/curriculo/**" }, EnumSet.of(PROMOTOR, DIRECTOR,
					ADMINISTRACION, DOCENTE)),
	// Sprint 10, tanda 2 (sección 8.3): el avance del registro de notas, SIN alumnos, para Dirección y Promotoría (las
	// correcciones del periodo, solo Dirección: lo exige el servicio). Va ANTES que NOTAS (que es solo del docente).
	NOTAS_AVANCE("Avance de notas", "Por sección y área, cuántas notas faltan en el periodo, y las asignaciones que todavía "
			+ "no pueden calificar.", "Sprint 10", true, "/academico/notas/avance",
			new String[] { "/academico/notas/avance", "/academico/notas/avance/**" }, EnumSet.of(PROMOTOR, DIRECTOR)),
	// Sprint 10, tanda 2 (E21): las exoneraciones de Religión o Educación Física, SOLO Dirección con su firma.
	EXONERACIONES("Exoneraciones", "Exonera a un alumno de Religión o Educación Física con el escrito del padre; rige desde "
			+ "el periodo abierto.", "Sprint 10", true, "/academico/notas/exoneraciones",
			new String[] { "/academico/notas/exoneraciones" }, EnumSet.of(DIRECTOR)),
	// Sprint 10, tanda 2 (secciones 3.5 y 3.6): las notas de toda la sección, de solo lectura, para su tutor y Dirección (lo
	// exige el servicio: 404 a otro docente), y la solicitud de corrección de una nota de un periodo cerrado (el docente del
	// área o Dirección). Va ANTES que NOTAS.
	NOTAS_SECCION("Notas de la sección", "Las notas de toda la sección, de solo lectura (tutor y Dirección), y la corrección "
			+ "de una nota después del cierre (la aprueba otra persona).", "Sprint 10", true, "/academico/notas/secciones",
			new String[] { "/academico/notas/secciones", "/academico/notas/secciones/*", "/academico/notas/correcciones/*" },
			EnumSet.of(DIRECTOR, DOCENTE)),
	// Sprint 10, tanda 2 (decisión 173 y sección 6 del brief): la hoja de notas es SOLO del docente del área (el tutor, en las
	// transversales). Dirección y Promotoría no ponen notas (403). Va ANTES que ACADEMICO.
	NOTAS("Notas", "Registra las notas de tu área por competencias, desde el celular: un toque por nivel y un solo guardado "
			+ "con tu firma.", "Sprint 10", true, "/academico/notas",
			new String[] { "/academico/notas", "/academico/notas/**" }, EnumSet.of(DOCENTE)),
	// Sprint 9, tanda 1: el docente ve sus secciones y los nombres de sus alumnos (nunca deudas, pagos, documentos ni
	// contactos); Dirección, todas las secciones. Promotoría no ve las listas por alumno (decisión 143).
	ACADEMICO("Académico", "Tus secciones y la lista de tus alumnos. Nunca muestra deudas, pagos, documentos ni contactos.",
			"Sprint 9", true, "/academico", new String[] { "/academico", "/academico/**" }, EnumSet.of(DIRECTOR, DOCENTE)),
	// Sprint 9, tanda 3 (sección 3.8): Promotoría y Dirección escriben a todo el colegio, un nivel, un grado o una sección;
	// el docente, solo a sus secciones (lo exige el servicio: 404). Retirar, solo Dirección y Promotoría (@PreAuthorize).
	COMUNICADOS("Comunicados", "Envía comunicados a las familias con confirmación de lectura.", "Sprint 9", true,
			"/comunicados", new String[] { "/comunicados", "/comunicados/**" }, EnumSet.of(PROMOTOR, DIRECTOR, DOCENTE));

	/** Rutas públicas: no exigen sesión. */
	public static final String[] RUTAS_PUBLICAS = { "/login", "/error", "/actuator/health", "/activar/*/*", "/verificar/*/*",
			// Sprint 7, tanda 1: sondas de vida y de disponibilidad (solo el estado) y el vigilante externo del respaldo.
			"/actuator/health/liveness", "/actuator/health/readiness", "/salud/respaldo",
			// Sprint 7, tanda 3 (Ley 29733): el aviso de privacidad vigente, enlazado desde el ingreso y el portal.
			"/privacidad",
			// Sprint 7, tanda 3 (H9): la página de ingreso con el aviso «Demasiados intentos» (429), a donde reenvía
			// FiltroLimiteIngresos. Sin datos: solo el formulario y un mensaje genérico.
			"/login/demasiados-intentos" };

	/**
	 * Sprint 8, tanda 3 (sección 8.2): las páginas públicas de la demo («Crear mi demo», el recorrido guiado, los archivos de
	 * ejemplo y robots.txt). {@code ConfiguracionSeguridad} las abre SOLO en el perfil {@code demo}; en los demás perfiles
	 * no existen (se niegan como cualquier ruta fuera de la matriz).
	 */
	public static final String[] RUTAS_DEMO = { "/demo", "/demo/guia", "/demo/ejemplos/*", "/robots.txt" };

	/**
	 * Sprint 4: avisos (webhooks) de la pasarela. Sin sesión ni CSRF, SOLO por POST y en una cadena de seguridad aparte:
	 * cada aviso se autentica con su firma y nunca registra dinero por sí mismo (se consulta a la pasarela).
	 */
	public static final String RUTAS_WEBHOOK = "/webhooks/**";

	public static final String RUTA_WEBHOOK_PASARELA = "/webhooks/pasarela/*/*";

	/**
	 * Sprint 8, tanda 2 (sección 3.6, E15): el aviso de la pasarela de UN conector del colegio, por su clave pública opaca
	 * (un UUID, no el id del colegio). La firma se valida con el secreto de ESE conector.
	 */
	public static final String RUTA_WEBHOOK_PASARELA_CONECTOR = "/webhooks/pasarela/*";

	/** Sprint 5: avisos de estado de WhatsApp (POST firmado) y su verificación (GET con hub.challenge). */
	public static final String RUTA_WEBHOOK_WHATSAPP = "/webhooks/whatsapp/*";

	/** Sprint 6, tanda 2: pedir el cambio del propio celular o correo (personal). */
	public static final String RUTA_MI_CONTACTO = "/cuenta/contacto";

	/** Única ruta para quien inició sesión con una clave temporal (autoridad {@code CLAVE_PENDIENTE}). */
	public static final String RUTA_CAMBIAR_CLAVE = "/cuenta/cambiar-clave";

	/**
	 * Sprint 8, tanda 1 (sección 3.7): el logo del colegio de la sesión (sin id: sin IDOR). Para cualquier persona con
	 * sesión; no es un módulo del menú.
	 */
	public static final String RUTA_LOGO = "/marca/logo";

	/** Correcciones del sprint 8 (S8-B4): el logo con que se emitió un comprobante ({@code /marca/logo/{id}}). */
	public static final String RUTA_LOGO_EMITIDO = "/marca/logo/*";

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
