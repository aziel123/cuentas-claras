package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model;

import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.DIRECTOR;
import static pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR;

/**
 * Sprint 11, tanda 1b (sección 3.14): el catálogo de los pasos de la puesta en marcha, sin datos en la base. Cada paso
 * dice su título, el verbo de la acción, quién lo hace, la ruta y de qué pasos depende. {@code EstadoPuestaEnMarcha} sigue
 * calculando si está hecho; este enum solo agrega lo que la interfaz necesita. Es la única fuente de «Quién lo hace»
 * (antes, {@code PuntoLista#responsable}).
 * <p>
 * Con la tanda 1c (sección 3.16), lo que hace la Promotoría se aplica directo: los textos ya no dicen «lo aprueba
 * Dirección» para la Promotoría. Lo que propone Administración sigue con dos personas.
 * <p>
 * Sprint 11, tanda 2 (AP2-9): el 16.º, {@link #CONSERVACION} (la autorización de conservación del titular), es
 * obligatorio: 16 pasos, 14 obligatorios. El 17.º ({@link #AREAS_DOCENTES}, sprint 10) es un aviso: no cuenta entre los 16
 * ni bloquea el paso a producción.
 */
public enum PasoPuestaEnMarcha {

	DATOS(1, "datos", "Datos y logo del colegio", "Completar los datos del colegio", EnumSet.of(PROMOTOR), "/colegio/marca",
			List.of(), Tipo.OBLIGATORIO, Quien.COLEGIO, "Promotoría", null),
	EQUIPO(2, "equipo", "Equipo del colegio", "Crear las cuentas de Dirección, Administración y Caja", EnumSet.of(PROMOTOR),
			"/usuarios", List.of(), Tipo.OBLIGATORIO, Quien.COLEGIO, "Promotoría",
			"Separar quién cobra, quién registra y quién revisa es lo que evita otro faltante."),
	ANIO(3, "anio", "Año escolar y fechas de clases", "Configurar el año escolar", EnumSet.of(PROMOTOR, DIRECTOR), "/colegio",
			List.of(), Tipo.OBLIGATORIO, Quien.COLEGIO, "Promotoría o Dirección", null),
	SECCIONES(4, "secciones", "Secciones", "Crear las secciones del año {año}", EnumSet.of(ADMINISTRACION, DIRECTOR),
			"/colegio", List.of("anio"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Administración o Dirección", null),
	PENSIONES(5, "pensiones", "Pensiones", "Proponer las pensiones del año {año}", EnumSet.of(ADMINISTRACION, PROMOTOR),
			"/pensiones", List.of("anio"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Administración o Promotoría",
			"Lo que propone Administración lo aprueba Promotoría o Dirección. Lo de la Promotoría se aplica directo."),
	ALUMNOS(6, "alumnos", "Alumnos", "Importar los alumnos del año {año}", EnumSet.of(ADMINISTRACION), "/alumnos",
			List.of("secciones"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Administración", null),
	SALDO_INICIAL(7, "saldo-inicial", "Saldo inicial", "Cargar el saldo inicial", EnumSet.of(ADMINISTRACION, PROMOTOR),
			"/pensiones/saldo-inicial", List.of("anio"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Administración o Promotoría",
			"Si el colegio no trae deudas anteriores, basta la firma de la Promotoría. El lote de Administración lo "
					+ "confirma Promotoría o Dirección."),
	OSE(8, "ose", "Comprobantes (OSE)", "Configurar los comprobantes", EnumSet.of(ADMINISTRACION, PROMOTOR), "/conectores",
			List.of("datos"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Administración o Promotoría",
			"Con el comprobante simulado, la Promotoría firma que emite el legal aparte."),
	CUENTA_BANCARIA(9, "cuenta-bancaria", "Cuenta bancaria", "Registrar la cuenta del banco", EnumSet.of(PROMOTOR),
			"/conectores", List.of("datos"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Promotoría",
			"Se aplica 24 horas después de aprobarla, o al instante si la aprueba otra Promotoría."),
	MENSAJERIA(10, "mensajeria", "WhatsApp y correo", null, EnumSet.noneOf(Rol.class), null, List.of(), Tipo.OBLIGATORIO,
			Quien.PLATAFORMA, "La plataforma", null),
	CORREO_CONTROL(11, "correo-control", "Correo externo de control", null, EnumSet.noneOf(Rol.class), null, List.of(),
			Tipo.OBLIGATORIO, Quien.OPERADOR, "El operador de la plataforma", null),
	PRIVACIDAD(12, "privacidad", "Aviso de privacidad", "Aprobar el aviso de privacidad", EnumSet.of(DIRECTOR),
			"/colegio/privacidad", List.of("datos"), Tipo.OBLIGATORIO, Quien.COLEGIO, "Dirección", null),
	RESPALDO(13, "respaldo", "Respaldo", null, EnumSet.noneOf(Rol.class), null, List.of(), Tipo.OBLIGATORIO,
			Quien.PLATAFORMA, "La plataforma", null),
	PASARELA(14, "pasarela", "Pago en línea", "Configurar el pago en línea", EnumSet.of(PROMOTOR), "/conectores",
			List.of("cuenta-bancaria"), Tipo.OPCIONAL, Quien.COLEGIO, "Promotoría", null),
	RECAUDACION(15, "recaudacion", "Recaudación bancaria", "Configurar la recaudación del banco", EnumSet.of(PROMOTOR),
			"/conectores", List.of("cuenta-bancaria"), Tipo.OPCIONAL, Quien.COLEGIO, "Promotoría", null),
	CONSERVACION(16, "conservacion", "Autorización de conservación", "Decidir la conservación de los datos",
			EnumSet.of(PROMOTOR), "/colegio/conservacion", List.of(), Tipo.OBLIGATORIO, Quien.COLEGIO,
			"El titular (primera Promotoría)",
			"Con tu autorización, si el colegio deja la plataforma las familias consultan 12 meses desde la suspensión; sin "
					+ "ella, los datos se suprimen 30 días después del cierre."),
	AREAS_DOCENTES(17, "areas-docentes", "Áreas de cada docente", "Completar las áreas de cada docente",
			EnumSet.of(DIRECTOR, ADMINISTRACION), "/academico/asignaciones", List.of(), Tipo.AVISO, Quien.COLEGIO,
			"Dirección o Administración", null);

	/** Los pasos que cuentan en «faltan N de 16». */
	public static final int TOTAL = 16;

	/** Los obligatorios: la barra del avance va sobre ellos (tanda 2: la conservación es el 14.º). */
	public static final int OBLIGATORIOS = 14;

	/** Obligatorio para pasar a producción, opcional (pago en línea y recaudación) o un aviso que no bloquea. */
	public enum Tipo {
		OBLIGATORIO, OPCIONAL, AVISO
	}

	/** Quién lo completa: el colegio, la plataforma de forma automática o el operador de la plataforma. */
	public enum Quien {
		COLEGIO, PLATAFORMA, OPERADOR
	}

	private final int numero;

	private final String clave;

	private final String titulo;

	private final String accion;

	private final Set<Rol> roles;

	private final String ruta;

	private final List<String> dependeDe;

	private final Tipo tipo;

	private final Quien quien;

	private final String responsable;

	private final String ayuda;

	PasoPuestaEnMarcha(int numero, String clave, String titulo, String accion, Set<Rol> roles, String ruta,
			List<String> dependeDe, Tipo tipo, Quien quien, String responsable, String ayuda) {
		this.numero = numero;
		this.clave = clave;
		this.titulo = titulo;
		this.accion = accion;
		this.roles = roles;
		this.ruta = ruta;
		this.dependeDe = dependeDe;
		this.tipo = tipo;
		this.quien = quien;
		this.responsable = responsable;
		this.ayuda = ayuda;
	}

	public static Optional<PasoPuestaEnMarcha> deClave(String clave) {
		return Arrays.stream(values()).filter(p -> p.clave.equals(clave)).findFirst();
	}

	public int numero() {
		return numero;
	}

	public String clave() {
		return clave;
	}

	public String titulo() {
		return titulo;
	}

	/** El verbo del botón; {@code {año}} se reemplaza con el año escolar (o se quita si todavía no hay). */
	public String accion(Integer anio) {
		if (accion == null) {
			return null;
		}
		return anio == null ? accion.replace(" del año {año}", "") : accion.replace("{año}", anio.toString());
	}

	public Set<Rol> roles() {
		return roles.isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles);
	}

	public boolean loHace(Set<Rol> rolesDelUsuario) {
		return roles.stream().anyMatch(rolesDelUsuario::contains);
	}

	public String ruta() {
		return ruta;
	}

	public List<PasoPuestaEnMarcha> dependeDe() {
		return dependeDe.stream().map(c -> deClave(c).orElseThrow()).toList();
	}

	public Tipo tipo() {
		return tipo;
	}

	public boolean obligatorio() {
		return tipo == Tipo.OBLIGATORIO;
	}

	public boolean opcional() {
		return tipo == Tipo.OPCIONAL;
	}

	public Quien quien() {
		return quien;
	}

	public boolean loHaceElColegio() {
		return quien == Quien.COLEGIO;
	}

	/** «Quién lo hace», en palabras («Administración o Dirección», «La plataforma»). */
	public String responsable() {
		return responsable;
	}

	/** Lo crucial que no cabe en el botón: va en un «i» (F-D7). Puede ser nulo. */
	public String ayuda() {
		return ayuda;
	}
}
