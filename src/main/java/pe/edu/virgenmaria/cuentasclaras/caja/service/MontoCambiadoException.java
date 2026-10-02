package pe.edu.virgenmaria.cuentasclaras.caja.service;

import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;

/** El total recalculado al cobrar no es el que la cajera vio en la revisión (otro pago o un descuento entre medio). */
public class MontoCambiadoException extends ReglaNegocioException {

	public MontoCambiadoException(BigDecimal totalActual) {
		super("El monto cambió desde que lo revisaste (ahora es " + Dinero.formatear(totalActual)
				+ "). Revisa de nuevo antes de cobrar.");
	}
}
