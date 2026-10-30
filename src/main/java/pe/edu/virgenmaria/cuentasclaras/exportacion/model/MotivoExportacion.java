package pe.edu.virgenmaria.cuentasclaras.exportacion.model;

/** Sprint 11, tanda 2 (sección 3.9): por qué se generó la exportación. */
public enum MotivoExportacion {

	/** Al aplicarse la suspensión (el colegio recibe sus datos el mismo día). */
	SUSPENSION,
	/** Antes del cierre, si no hay una de menos de 30 días. */
	CIERRE,
	/** A pedido escrito del colegio, con la firma de un operador. */
	PEDIDO
}
