package pe.edu.virgenmaria.cuentasclaras.colegio.model;

/**
 * Sprint 11, tanda 1 (AP-2): separa los cambios pendientes de un colegio. Uno pendiente por ámbito
 * ({@code UNIQUE (colegio_id, ambito, pendiente)}): un bloqueo de la facultad no espera a una suspensión.
 */
public enum AmbitoCambioEstado {
	ESTADO, FACULTAD
}
