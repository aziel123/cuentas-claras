package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

/**
 * Acciones que quedan en la bitácora. Se guardan por nombre ({@code VARCHAR(40)}):
 * nunca renombres un valor ya usado, agrega uno nuevo.
 */
public enum AccionAuditoria {

	// Ingresos y bloqueos
	INGRESO_EXITOSO("Ingresó al sistema", false),
	INGRESO_FALLIDO("Intento de ingreso fallido", true),
	CUENTA_BLOQUEADA("Cuenta bloqueada por intentos fallidos", true),
	INGRESO_RECHAZADO_BLOQUEADA("Intentó ingresar con la cuenta bloqueada", true),
	INGRESO_RECHAZADO_INACTIVA("Intentó ingresar con una cuenta desactivada", true),
	CUENTA_DESBLOQUEADA("Desbloqueó una cuenta", false),

	// Sesión y acceso
	SESION_CERRADA("Cerró sesión", false),
	ACCESO_DENEGADO("Intentó entrar a una página sin permiso", true),

	// Usuarios y claves
	USUARIO_CREADO("Creó un usuario", false),
	USUARIO_DESACTIVADO("Desactivó un usuario", false),
	USUARIO_REACTIVADO("Reactivó un usuario", false),
	CLAVE_CAMBIADA("Cambió su clave", false),
	CLAVE_RESTABLECIDA("Restableció la clave de un usuario", false),
	ROLES_CAMBIADOS("Cambió los roles de un usuario", false),

	// Integridad
	INTEGRIDAD_VERIFICADA("Verificó la integridad de la bitácora", false);

	private final String descripcion;

	private final boolean requiereAtencion;

	AccionAuditoria(String descripcion, boolean requiereAtencion) {
		this.descripcion = descripcion;
		this.requiereAtencion = requiereAtencion;
	}

	/** Texto en lenguaje claro para la bitácora. */
	public String descripcion() {
		return descripcion;
	}

	/** {@code true} para los intentos fallidos o rechazados: se resaltan en la bitácora. */
	public boolean requiereAtencion() {
		return requiereAtencion;
	}
}
