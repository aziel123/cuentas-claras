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
	MATRICULA_SECCION_CAMBIADA("Cambió de sección a un alumno", false),

	// Importación desde Excel (sprint 2): con la huella SHA-256 del archivo y los conteos.
	IMPORTACION_CONFIRMADA("Importó alumnos desde Excel", false),

	// Planes de pensiones (sprint 2): montos y vencimientos completos en cada evento.
	PLAN_PENSION_CREADO("Propuso un plan de pensiones", false),
	PLAN_PENSION_EDITADO("Editó un plan de pensiones en borrador", false),
	PLAN_PENSION_ENVIADO("Envió un plan de pensiones para su aprobación", false),
	PLAN_PENSION_DEVUELTO("Devolvió un plan de pensiones sin aprobarlo", false),
	PLAN_PENSION_APROBADO("Aprobó un plan de pensiones", false),
	PLAN_PENSION_DESCARTADO("Descartó un plan de pensiones en borrador", false),

	// Cuotas (sprint 2)
	/** Uno por matrícula: plan, cuotas, vencimientos y total. */
	CRONOGRAMA_GENERADO("Generó el cronograma de cuotas de una matrícula", false),
	/** Se resalta: pedir que una deuda deje de cobrarse. La aprobación llega en el sprint 3. */
	CUOTA_ANULACION_SOLICITADA("Solicitó anular una cuota", true),
	CUOTA_ANULADA("Anuló una cuota", true),
	/** Se resalta: una cuota del plan no se generó porque esa deuda ya existía (por ejemplo, como saldo inicial). */
	CUOTA_OMITIDA_DEUDA_EXISTENTE("No generó una cuota porque la deuda ya existía", true),

	// Saldo inicial (sprint 2): doble control y total de control.
	SALDO_INICIAL_LOTE_CREADO("Creó un lote de saldo inicial", false),
	SALDO_INICIAL_LINEA_AGREGADA("Agregó una deuda a un lote de saldo inicial", false),
	SALDO_INICIAL_LINEA_QUITADA("Quitó una deuda de un lote de saldo inicial", true),
	SALDO_INICIAL_ENVIADO("Envió un lote de saldo inicial para confirmación", false),
	SALDO_INICIAL_CONFIRMADO("Confirmó un lote de saldo inicial", false),
	/** Se resalta: quien confirmaba escribió un total del informe del contador distinto del declarado. */
	SALDO_INICIAL_TOTAL_NO_COINCIDE("El total del informe no coincidió al confirmar", true),
	SALDO_INICIAL_DEVUELTO("Devolvió un lote de saldo inicial", false),
	SALDO_INICIAL_DESCARTADO("Descartó un lote de saldo inicial", false),

	// Solicitudes de cambio que aprueba otra persona (sprint 2, correcciones)
	/** Se resalta: retiros, contactos, responsables de pago, fechas de matrícula y anulaciones pedidas. */
	SOLICITUD_CREADA("Pidió un cambio que aprueba otra persona", true),
	SOLICITUD_APROBADA("Aprobó una solicitud de cambio", false),
	SOLICITUD_RECHAZADA("Rechazó una solicitud de cambio", false),
	/** Se resalta: un ingreso tardío aprobado recorta el cronograma. */
	MATRICULA_FECHA_CAMBIADA("Cambió la fecha de ingreso de una matrícula", true),

	// Control de segregación de funciones
	/** Se resalta: alguien intentó aprobar o confirmar lo que él mismo hizo. */
	AUTOAPROBACION_RECHAZADA("Intentó aprobar algo que él mismo hizo", true);

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
