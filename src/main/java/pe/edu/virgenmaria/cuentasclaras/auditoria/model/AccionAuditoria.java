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
	INTEGRIDAD_VERIFICADA("Verificó la integridad de la bitácora", false),

	// Estructura del colegio (sprint 2)
	ANIO_ESCOLAR_CREADO("Creó un año escolar", false),
	SECCION_CREADA("Creó una sección", false),
	SECCION_DESACTIVADA("Desactivó una sección", false),

	// Familias y apoderados (sprint 2). Los datos personales van enmascarados.
	FAMILIA_CREADA("Creó una familia", false),
	FAMILIA_ACTUALIZADA("Cambió el nombre de una familia", false),
	APODERADO_REGISTRADO("Registró un apoderado", false),
	APODERADO_ACTUALIZADO("Corrigió los datos de un apoderado", false),
	/** Se resalta: cambiar el celular o el correo es la vía para desviar los avisos de pago lejos del padre real. */
	APODERADO_CONTACTO_CAMBIADO("Cambió el celular o el correo de un apoderado", true),
	APODERADO_DESACTIVADO("Desactivó un apoderado", false),

	// Alumnos y matrículas (sprint 2)
	ALUMNO_REGISTRADO("Registró un alumno", false),
	ALUMNO_ACTUALIZADO("Corrigió los datos de un alumno", false),
	/** Se resalta: un alumno retirado deja de recibir cuotas. */
	ALUMNO_RETIRADO("Retiró a un alumno", true),
	/** Se resalta: decide a quién se le cobra y quién recibe los avisos de pago. */
	RESPONSABLE_PAGO_CAMBIADO("Cambió el responsable de pago de un alumno", true),
	MATRICULA_REGISTRADA("Matriculó a un alumno", false),
	MATRICULA_SECCION_CAMBIADA("Cambió de sección a un alumno", false);

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
