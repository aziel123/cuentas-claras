package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResumenCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;

import java.math.BigDecimal;

/**
 * Lo que dice el libro de una caja: el efectivo VIGENTE (incluye los reemplazos de una corrección) y lo digital, que
 * nunca entra al esperado. Es el único cálculo del esperado: el formulario del cierre nunca lo trae. Sin rol: lo usan
 * servicios de caja que ya lo exigieron.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class LibroCaja {

	private final PagoRepository pagos;

	public LibroCaja(PagoRepository pagos) {
		this.pagos = pagos;
	}

	public ResumenCaja resumen(CajaDiaria caja) {
		BigDecimal efectivo = Dinero.CERO;
		BigDecimal digital = Dinero.CERO;
		int pagosEfectivo = 0;
		int pagosDigitales = 0;
		for (Object[] fila : pagos.vigentesPorMedio(caja.getId())) {
			MedioPago medio = (MedioPago) fila[0];
			int cantidad = ((Number) fila[1]).intValue();
			BigDecimal total = Dinero.normalizar((BigDecimal) fila[2]);
			if (medio == MedioPago.EFECTIVO) {
				efectivo = efectivo.add(total);
				pagosEfectivo += cantidad;
			}
			else {
				digital = digital.add(total);
				pagosDigitales += cantidad;
			}
		}
		return new ResumenCaja(caja.getFondoFijo(), efectivo, pagosEfectivo, pagosDigitales, digital);
	}
}
