package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import java.util.Collection;

/**
 * Puerto (sprint 4): si alguna de estas cuotas tiene un pago en línea en curso (una orden abierta y no vencida). Mientras
 * lo tenga, no se aprueba un descuento ni una anulación de esa cuota: cambiaría el saldo de una orden ya enviada a la
 * pasarela. Lo implementa el módulo de pagos en línea; cobranza no depende de él.
 */
public interface CuotasEnPagoEnLinea {

	boolean algunaEnCurso(Collection<Long> cuotaIds);

	/** El mensaje para quien aprueba. */
	String MENSAJE = "Hay un pago en línea en curso para esta cuota; vuelve a intentarlo en 30 minutos.";
}
