package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

/**
 * Acciones que quedan en la bitácora. Se guardan por nombre ({@code VARCHAR(40)}):
 * nunca renombres un valor ya usado, agrega uno nuevo.
 */
public enum AccionAuditoria {

	// Ingresos y bloqueos
	INGRESO_EXITOSO,
	INGRESO_FALLIDO,
	CUENTA_BLOQUEADA,
	INGRESO_RECHAZADO_BLOQUEADA,
	INGRESO_RECHAZADO_INACTIVA,
	CUENTA_DESBLOQUEADA,

	// Sesión y acceso
	SESION_CERRADA,
	ACCESO_DENEGADO,

	// Usuarios y claves
	USUARIO_CREADO,
	USUARIO_DESACTIVADO,
	USUARIO_REACTIVADO,
	CLAVE_CAMBIADA,
	CLAVE_RESTABLECIDA,
	ROLES_CAMBIADOS,

	// Integridad
	INTEGRIDAD_VERIFICADA
}
