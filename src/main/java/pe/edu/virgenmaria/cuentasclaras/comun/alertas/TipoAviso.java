package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

/**
 * Qué alerta sale al celular de Promotoría (sprint 6, tanda 2, sección 12.2). El texto del mensaje es FIJO por tipo: nunca
 * el texto de la alerta, que puede llevar la explicación escrita por la cajera o el nombre de una familia (hallazgo 2).
 */
public enum TipoAviso {

	CIERRE_CON_DIFERENCIA("Cierre de caja con diferencia"),
	CAJA_SIN_CERRAR("Una caja de un día anterior sigue abierta"),
	CIERRE_NO_REALIZADO("Una caja no se cerró a la hora límite"),
	ANULACION_PAGO_PENDIENTE("Anulación de pago por aprobar"),
	DEPOSITO_DISTINTO("Depósito distinto de lo contado"),
	NO_APARECE_EN_BANCO("Un pago o depósito no aparece en el banco"),
	AVISO_FAMILIA_GRAVE("Una familia avisa de un pago que no reconoce o que no aparece"),
	HUELLA("La bitácora no coincide con su huella"),
	RESUMEN_NO_SALIO("El resumen diario no salió"),
	CIFRAS_CAMBIARON("Las cifras de un día ya informado cambiaron"),
	LLAMADA_NO_CONFIRMA("Una familia no confirma lo registrado"),
	/** Correcciones del sprint 6 (S6-M1): hay una foto del resumen que no guardó sistema.panel. */
	RESUMEN_SUPLANTADO("Hay un resumen diario que no generó el sistema"),
	/** S6-M2 (ATENCIÓN): una familia de la llamada de control no contestó dos veces y se eligió otra. */
	LLAMADA_REEMPLAZADA("Una familia de la llamada de control no contestó dos veces"),
	/** S6-M2 (ATENCIÓN): Dirección registró una llamada de control con la semana delegada. */
	LLAMADA_POR_DIRECCION("Dirección registró una llamada de control"),
	/** Sprint 8, tanda 2 (sección 3.6, E12): un conector entró en vigencia (CRÍTICA durante unos días). */
	CONECTOR_CAMBIADO("Cambió adónde entra el dinero del colegio"),
	/** Sprint 9, tanda 2 (decisión 149, A4): secciones sin asistencia (o incompleta) a la hora de alerta. A Dirección. */
	SECCION_SIN_ASISTENCIA("Hay secciones sin asistencia registrada"),
	/** Sprint 9, tanda 2 (A8): solicitudes de la bandeja pendientes más de 2 días hábiles. A Promotoría. */
	SOLICITUDES_PENDIENTES("Hay solicitudes por aprobar hace más de 2 días"),
	/** Correcciones del sprint 9 (auditoría, 6): más de 30 consentimientos desde la ficha por una persona en un día. */
	CONSENTIMIENTOS_DESDE_FICHA("Muchos consentimientos registrados hoy desde la ficha"),
	/**
	 * Sprint 10, tanda 1 (M10, CRÍTICA): la revisión diaria del catálogo nacional del currículo encontró que la base no
	 * coincide con el jar (alguien cambió una competencia o metió una versión). A Promotoría.
	 */
	INTEGRIDAD_CATALOGO("El catálogo del currículo no coincide con el del sistema"),
	/** Sprint 10, tanda 2 (A5, decisión 186): una corrección de nota subió el nivel o dejó sin nota. A Promotoría. */
	CORRECCIONES_NOTAS("Se corrigió una nota después del cierre"),
	/** Sprint 10, tanda 2: 3 días antes del fin, secciones y áreas con competencias sin nota (sin alumnos). A Dirección. */
	NOTAS_PENDIENTES("Hay competencias sin nota a pocos días del cierre"),
	/** Sprint 10, tanda 2 (sección 4 del brief): asignaciones sin áreas del catálogo (no califican). A Dirección. */
	ASIGNACIONES_SIN_AREAS("Hay docentes que todavía no pueden poner notas"),
	/**
	 * Sprint 10, tanda 3 (E29, A3, decisión 188): faltan libretas de matrículas activas o reservadas, o una libreta no
	 * coincide con su huella (alguien tocó la base) y no se ha reemitido. A Promotoría; vencido el plazo, se repite cada día.
	 */
	INTEGRIDAD_LIBRETAS("Hay una libreta por revisar: falta o no coincide con su huella"),
	OTRA_CRITICA("Alerta crítica");

	private final String texto;

	TipoAviso(String texto) {
		this.texto = texto;
	}

	/** El texto fijo que sale por WhatsApp o correo ({{1}} de cc_alerta_promotoria). */
	public String texto() {
		return texto;
	}

	/** Las ATENCIÓN que también salen al celular (decisión 70); de las demás, solo las CRÍTICAS. */
	public boolean saleAunqueNoSeaCritica() {
		return this == CIERRE_NO_REALIZADO || this == ANULACION_PAGO_PENDIENTE || this == LLAMADA_REEMPLAZADA
				|| this == LLAMADA_POR_DIRECCION || this == SECCION_SIN_ASISTENCIA || this == SOLICITUDES_PENDIENTES
				|| this == CONSENTIMIENTOS_DESDE_FICHA || this == CORRECCIONES_NOTAS || this == NOTAS_PENDIENTES
				|| this == ASIGNACIONES_SIN_AREAS;
	}

	/** Sprint 9, tanda 2 (H10): las que van SOLO a Dirección (no a Promotoría, que no ve la asistencia: decisión 143). */
	public boolean soloDireccion() {
		return this == SECCION_SIN_ASISTENCIA || this == CONSENTIMIENTOS_DESDE_FICHA || this == NOTAS_PENDIENTES
				|| this == ASIGNACIONES_SIN_AREAS;
	}

	/**
	 * S6-B2 (QA-S6-7): los tipos que solo salen como CRÍTICA. No cuentan para el tope diario: la cajera no puede agotarlo
	 * con anulaciones por aprobar para que su faltante de caja no llegue ese día.
	 */
	public boolean esCritico() {
		return !saleAunqueNoSeaCritica();
	}
}
