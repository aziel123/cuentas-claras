package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 2 (sección 5): de dónde sale una marca. No hay estados que cambien: cada corrección es una fila NUEVA
 * que reemplaza a la vigente (la vigente es la que ninguna otra reemplaza).
 */
public enum OrigenMarca {

	/** La del registro del día (o del registro tardío aprobado), por el docente que registra. */
	REGISTRO("Registro"),
	/** El mismo día, por un docente de la sección, antes de la hora del segundo aviso (A2), con su firma (A5). */
	CORRECCION_DEL_DIA("Corrección del día"),
	/** Otro día (o después del cierre), con SU solicitud CORRECCION_ASISTENCIA aprobada y firmada por Dirección. */
	CORRECCION_APROBADA("Corrección aprobada"),
	/** La familia justifica una FALTA desde el portal, con su sesión y su firma (A5). */
	JUSTIFICACION("Justificación de la familia"),
	/** Dirección revierte una justificación, con motivo y su firma. */
	REVERSION_JUSTIFICACION("Justificación revertida");

	private final String etiqueta;

	OrigenMarca(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
