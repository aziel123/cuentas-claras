package pe.edu.virgenmaria.cuentasclaras.caja.model;

/**
 * CAJA: lo registró su cajero. REEMPLAZO: lo crea la aprobación de una corrección (sprint 3, tanda 2). PASARELA: lo
 * registró {@code sistema.pasarela} con la confirmación de la pasarela de SU orden de pago (sprint 4). RECAUDACION: lo
 * registró {@code sistema.recaudacion} con SU línea del archivo del banco, ya confirmado a ciegas por otra persona (sprint
 * 4, tanda 2).
 */
public enum OrigenPago {
	CAJA, REEMPLAZO, PASARELA, RECAUDACION
}
