package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

/**
 * Actores de sistema: lo que entra solo (pagos en línea, recaudación, conciliación, envío al OSE) lo registra uno de
 * ellos, nunca una persona. No son valores de {@code Rol}: ninguna persona puede tenerlos (el CHECK de
 * {@code usuario_rol} lo impide) y ningún usuario puede llamarse «sistema...» ({@code ck_usuario_nombre_reservado}).
 */
public enum ActorSistema {

	PASARELA("sistema.pasarela"),
	RECAUDACION("sistema.recaudacion"),
	CONCILIACION("sistema.conciliacion"),
	OSE("sistema.ose"),
	/** Sprint 5: envía los mensajes (WhatsApp y correo), reintenta y registra las respuestas del proveedor. */
	MENSAJERIA("sistema.mensajeria"),
	/** Sprint 5: guarda y envía la huella diaria de la bitácora y la vuelve a verificar cada mañana. */
	AUDITORIA("sistema.auditoria"),
	/** Sprint 5, tanda 2: reserva la matrícula del año siguiente al confirmarse la renovación y la activa al pagarse. */
	MATRICULA("sistema.matricula"),
	/**
	 * Sprint 6, tanda 2: guarda la foto del resumen diario (y la envía a Promotoría), la recalcula cada mañana y difunde
	 * las alertas al celular. Solo lee los libros por los puertos de cifras y alertas; nunca registra dinero.
	 */
	PANEL("sistema.panel"),
	/**
	 * Sprint 7, tanda 2 (H4): crea y lee la semilla secreta del muestreo (solo él la inserta, con la conexión de
	 * {@code cc_sistema}, y solo para hoy o el lunes en curso). La semilla efectiva se deriva con la clave del servidor.
	 */
	MUESTREO("sistema.muestreo");

	/** Prefijo reservado: ninguna persona puede tener un nombre de usuario que empiece así. */
	public static final String PREFIJO_RESERVADO = "sistema";

	private final String usuario;

	ActorSistema(String usuario) {
		this.usuario = usuario;
	}

	/** El nombre con el que firma ({@code creado_por}, {@code cajero} de su caja de canal y la bitácora). */
	public String usuario() {
		return usuario;
	}

	/** {@code ROLE_SISTEMA_PASARELA}...: lo exigen los servicios con {@code hasRole('SISTEMA_PASARELA')}. */
	public String autoridad() {
		return "ROLE_" + rol();
	}

	/** {@code SISTEMA_PASARELA}...: como lo guarda la bitácora en los roles del actor. */
	public String rol() {
		return "SISTEMA_" + name();
	}

	/** Si el nombre de usuario (ya normalizado) es de los reservados para los actores de sistema. */
	public static boolean esReservado(String nombreUsuario) {
		return nombreUsuario != null && nombreUsuario.startsWith(PREFIJO_RESERVADO);
	}
}
