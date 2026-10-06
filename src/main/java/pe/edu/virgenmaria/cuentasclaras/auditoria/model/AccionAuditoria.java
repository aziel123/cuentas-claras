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

	// Caja y comprobantes (sprint 3, tanda 1). Con comprobante, montos, medio, familia, alumnos y cuotas; los documentos
	// van enmascarados.
	PAGO_REGISTRADO("Registró un pago", false),
	COMPROBANTE_EMITIDO("Emitió un comprobante", false),
	/** Se resalta: un pago parcial deja deuda viva y es la vía para cobrar completo y registrar menos. */
	PAGO_A_CUENTA("Registró un pago a cuenta (parcial)", true),
	CAJA_ABIERTA("Abrió su caja del día", false),
	/** Se resalta: intentó cobrar en efectivo con la caja ya cerrada. */
	CAJA_EFECTIVO_RECHAZADO_CERRADA("Intentó cobrar en efectivo con la caja cerrada", true),

	// Anulaciones de pago y descuentos (sprint 3, tanda 2). Todos resaltados salvo el rechazo de un descuento.
	PAGO_ANULACION_SOLICITADA("Pidió anular un pago", true),
	PAGO_ANULADO("Anuló un pago (aprobado por otra persona)", true),
	NOTA_CREDITO_EMITIDA("Emitió una nota de crédito", true),
	/** Se resalta: el dinero de un pago anulado se aplica a otras cuotas (quizás de otra familia). */
	PAGO_REEMPLAZO_REGISTRADO("Registró el pago de reemplazo de una corrección", true),
	DESCUENTO_SOLICITADO("Pidió un descuento o beca", true),
	DESCUENTO_APROBADO("Aprobó un descuento o beca en una cuota", true),
	DESCUENTO_RECHAZADO("Rechazó un descuento o beca", false),

	// Cierre, depósito y verificación bancaria (sprint 3, tanda 3). Se resalta todo lo que muestra una diferencia.
	/** Se resalta: el primer conteo a ciegas no coincidió con lo registrado (no se muestran montos a la cajera). */
	CAJA_CONTEO_NO_COINCIDE("Su primer conteo de caja no coincidió", true),
	CAJA_CERRADA("Cerró su caja (cuadró)", false),
	/** Se resalta: faltante o sobrante al cierre (un sobrante puede ser un cobro sin registrar). */
	CAJA_CERRADA_CON_DIFERENCIA("Cerró su caja con diferencia", true),
	CAJA_CIERRE_APROBADO("Aprobó un cierre de caja", false),
	CAJA_CIERRE_OBSERVADO("Observó un cierre de caja", true),
	CAJA_REAPERTURA_SOLICITADA("Pidió reabrir su caja", true),
	CAJA_REABIERTA("Reabrió una caja (aprobado por otra persona)", true),
	DEPOSITO_REGISTRADO("Registró el depósito de una caja", false),
	/** Se resalta: depositó un monto distinto de lo contado en el cierre. */
	DEPOSITO_DIFERENTE("Depositó un monto distinto de lo contado", true),
	PAGO_VERIFICADO("Verificó un pago digital en el banco", false),
	DEPOSITO_VERIFICADO("Verificó un depósito en el banco", false),
	/** Se resalta: un Yape, Plin o transferencia registrado no aparece en el banco (¿número inventado?). */
	PAGO_NO_ENCONTRADO_BANCO("Un pago digital no aparece en el banco", true),
	DEPOSITO_NO_ENCONTRADO("Un depósito no aparece en el banco", true),

	// Correcciones del sprint 3 (auditoría antifraude).
	/** Se resalta: lo escrito a ciegas del banco no coincide con lo registrado (¿número o monto inventado?). */
	VERIFICACION_NO_COINCIDE("Lo verificado en el banco no coincide", true),
	/** Se resalta: sale dinero (devolución al apoderado). */
	REEMBOLSO_REGISTRADO("Registró el reembolso de una devolución", true),
	DATOS_FACTURACION_CAMBIADOS("Cambió el RUC para factura de un apoderado", true),

	// Sprint 4 · tanda 1: pagos en línea y envío de comprobantes al OSE. El actor de lo automático es sistema.*.
	ORDEN_PAGO_CREADA("Inició un pago en línea", false),
	ORDEN_PAGO_VENCIDA("Venció un pago en línea sin pagar", false),
	ORDEN_PAGO_RECHAZADA("La pasarela rechazó un pago en línea", false),
	/** Se resalta: hubo dinero pero no se pudo aplicar (cuota ya pagada, monto o moneda distintos, operación usada). */
	ORDEN_PAGO_POR_REVISAR("Un pago en línea quedó por revisar", true),
	/** Se resalta: un pago con la pasarela SIMULADA (no es dinero real). */
	PASARELA_SIMULADA_USADA("Pagó con la pasarela SIMULADA (no es dinero real)", true),
	/** Se resalta: la pasarela confirmó el pago de una orden ya vencida. */
	PAGO_EN_LINEA_TARDIO("Se aplicó un pago en línea tardío", true),
	INGRESO_APLICACION_SOLICITADA("Pidió aplicar un ingreso por revisar", true),
	INGRESO_APLICADO("Aplicó un ingreso por revisar (aprobado)", true),
	INGRESO_DEVOLUCION_SOLICITADA("Pidió devolver un ingreso por revisar", true),
	INGRESO_DEVUELTO("Devolvió un ingreso por revisar", true),
	CONTRACARGO_RECIBIDO("Recibió un contracargo de la pasarela", true),
	COMPROBANTE_ACEPTADO("El OSE aceptó un comprobante", false),
	COMPROBANTE_OBSERVADO("El OSE aceptó un comprobante con observaciones", true),
	COMPROBANTE_RECHAZADO("El OSE rechazó un comprobante", true),
	COMPROBANTE_REEMITIDO("Reemitió un comprobante rechazado", true),
	COMPROBANTE_NO_COINCIDE_OSE("Un comprobante aceptado no coincide en el OSE", true),
	ACCESO_APODERADO_CREADO("Dio acceso en línea a un apoderado", false),
	ACCESO_APODERADO_QUITADO("Quitó el acceso en línea de un apoderado", true),

	// Sprint 4 · tanda 2: recaudación bancaria. Los pagos los registra sistema.recaudacion (PAGO_REGISTRADO de siempre).
	RECAUDACION_CARGADA("Cargó un archivo de recaudación del banco", false),
	/** Se resalta: quien subió el archivo lo descartó antes de que otra persona lo confirme. */
	RECAUDACION_DESCARTADA("Descartó un archivo de recaudación", true),
	RECAUDACION_CONFIRMADA("Confirmó a ciegas el total de una recaudación", false),
	/** Se resalta: el total escrito a ciegas no coincide con el del archivo (¿archivo fabricado o editado?). */
	RECAUDACION_TOTAL_NO_COINCIDE("El total a ciegas de una recaudación no coincide", true),
	/** Se resalta: dos totales a ciegas distintos; el lote no se aplica. */
	RECAUDACION_RECHAZADA("Se rechazó un archivo de recaudación", true),
	RECAUDACION_APLICADA("Se aplicaron los pagos de una recaudación", false),
	/** Se resalta: una línea del banco no se pudo aplicar sola (código errado, sin deuda, exceso...). */
	RECAUDACION_LINEA_EXCEPCION("Una línea de recaudación quedó por revisar", true),
	/** Se resalta: el archivo del banco lleva datos de familias (Ley 29733). */
	ARCHIVO_BANCO_DESCARGADO("Descargó un archivo original del banco", true),
	BASE_DEUDAS_EXPORTADA("Exportó la base de deudas para el banco", false),

	// Sprint 4 · tanda 3: extracto bancario, conciliación automática y liquidaciones de la pasarela.
	/** Se resalta: la cuenta cuyo extracto se concilia (una cuenta falsa escondería los abonos reales). */
	CUENTA_BANCARIA_REGISTRADA("Registró una cuenta bancaria para conciliar", true),
	CUENTA_BANCARIA_DESACTIVADA("Desactivó una cuenta bancaria", true),
	EXTRACTO_CARGADO("Cargó un extracto bancario", false),
	/** Se resalta: el saldo final escrito a ciegas no coincide con el del extracto (¿extracto editado?). */
	EXTRACTO_SALDO_NO_COINCIDE("El saldo a ciegas de un extracto no coincide", true),
	EXTRACTO_CONFIRMADO("Confirmó a ciegas el saldo de un extracto", false),
	/** Se resalta: dos saldos a ciegas distintos; el extracto no se concilia. */
	EXTRACTO_RECHAZADO("Se rechazó un extracto bancario", true),
	/** Se resalta: el archivo trae días ya cargados con otros movimientos (el banco no cambia el pasado). */
	EXTRACTO_DISCONTINUO("Un extracto no coincide con los días ya cargados", true),
	EXTRACTO_DESCARTADO("Descartó un extracto bancario", true),
	CONCILIACION_AUTOMATICA("Conciliación automática con el extracto", false),
	/** Se resalta: una persona dio por buena una pareja sugerida (no tenía la misma operación). */
	PARTIDA_SUGERIDA_CONFIRMADA("Confirmó una pareja sugerida del extracto", true),
	PARTIDA_MANUAL_REGISTRADA("Emparejó a mano un movimiento del extracto", true),
	/** Correcciones del sprint 4 (S4-C1): la pareja manual quedó confirmada al aprobarla otra persona en la bandeja. */
	PARTIDA_MANUAL_APROBADA("Se aprobó una pareja manual del extracto", true),
	PARTIDA_DESCARTADA("Descartó una pareja del extracto", true),
	MOVIMIENTO_EXPLICADO("Explicó un movimiento del extracto", true),
	LIQUIDACION_REGISTRADA("Registró una liquidación de la pasarela", false),
	/** Se resalta: la pasarela liquidó un cargo que no corresponde a ningún pago registrado. */
	LIQUIDACION_SIN_PAGO("Una liquidación trae un cargo sin pago registrado", true),

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
