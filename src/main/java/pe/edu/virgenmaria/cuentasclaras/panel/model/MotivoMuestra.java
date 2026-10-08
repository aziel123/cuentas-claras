package pe.edu.virgenmaria.cuentasclaras.panel.model;

/** Por qué una familia está en la muestra de la llamada de control (correcciones del sprint 6, S6-A2 y S6-M2). */
public enum MotivoMuestra {

	/** Pagó en efectivo en las 5 semanas anteriores al lunes. */
	EFECTIVO,
	/** Tiene deuda vencida al lunes: el efectivo que la cajera no registró no deja pago, pero sí deuda (S6-A2). */
	DEUDA,
	/** Reemplaza a una familia que no contestó en dos intentos (S6-M2). */
	REEMPLAZO
}
