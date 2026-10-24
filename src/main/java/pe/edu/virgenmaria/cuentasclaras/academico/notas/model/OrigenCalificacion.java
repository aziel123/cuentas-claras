package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

/**
 * Sprint 10, tanda 2 (sección 3.5): de dónde sale una nota. Con el periodo ABIERTO, {@code REGISTRO} (la primera) y
 * {@code CAMBIO_ABIERTO} (reemplaza la vigente, también a «sin nota»); con el periodo CERRADO, solo {@code REGISTRO_TARDIO}
 * (con su CALIFICACION_TARDIA aprobada) y {@code CORRECCION_APROBADA} (al aprobar la CORRECCION_CALIFICACION de otra
 * persona). Lo exige trg_calificacion_nace.
 */
public enum OrigenCalificacion {

	REGISTRO, CAMBIO_ABIERTO, REGISTRO_TARDIO, CORRECCION_APROBADA
}
