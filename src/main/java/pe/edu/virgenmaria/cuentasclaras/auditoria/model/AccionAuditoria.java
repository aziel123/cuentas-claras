package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
	INGRESO_RECHAZADO_CLAVE_VENCIDA("Intentó ingresar con una clave temporal vencida", true),
	CUENTA_DESBLOQUEADA("Desbloqueó una cuenta", false),

	// Sesión y acceso
	SESION_CERRADA("Cerró sesión", false),
	ACCESO_DENEGADO("Intentó entrar a una página sin permiso", true),

	// Usuarios y claves
	USUARIO_CREADO("Creó un usuario", false),
	USUARIO_DESACTIVADO("Desactivó un usuario", false),
	USUARIO_REACTIVADO("Reactivó un usuario", false),
	CLAVE_CAMBIADA("Cambió su clave", false),
	CLAVE_RESTABLECIDA("Restableció la clave de un usuario", true),
	ROLES_CAMBIADOS("Cambió los roles de un usuario", false),

	// Integridad
	INTEGRIDAD_VERIFICADA("Verificó la integridad de la bitácora", false);

	/** Altas y cambios de roles: se revisan si dan un rol que maneja dinero o permisos. */
	public static final Set<AccionAuditoria> CON_ROLES = EnumSet.of(USUARIO_CREADO, ROLES_CAMBIADOS);

	/** Roles que, al darse, piden revisión (por su nombre guardado en el valor nuevo). */
	public static final List<String> ROLES_SENSIBLES = List.of("PROMOTOR", "DIRECTOR", "ADMINISTRACION", "CAJA");

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

	/** {@code true} para intentos fallidos o rechazados y restablecimientos de clave: siempre se revisan. */
	public boolean requiereAtencion() {
		return requiereAtencion;
	}

	/** Acciones que siempre se marcan "Revisar". */
	public static Set<AccionAuditoria> siempreRevisar() {
		Set<AccionAuditoria> acciones = EnumSet.noneOf(AccionAuditoria.class);
		for (AccionAuditoria accion : values()) {
			if (accion.requiereAtencion) {
				acciones.add(accion);
			}
		}
		return acciones;
	}

	/**
	 * Si un evento se marca "Revisar": intentos fallidos o rechazados, restablecimientos de clave, y altas o
	 * cambios de roles que dan Promotoría, Dirección, Administración o Caja (riesgo de cuentas fantasma).
	 */
	public boolean requiereRevision(String valorNuevo) {
		return requiereAtencion || CON_ROLES.contains(this) && valorNuevo != null
				&& ROLES_SENSIBLES.stream().anyMatch(valorNuevo::contains);
	}
}
