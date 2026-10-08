package pe.edu.virgenmaria.cuentasclaras.caja.model;

import java.math.BigDecimal;

/**
 * Lo que dice el libro de una caja: fondo fijo, efectivo de los pagos VIGENTES y el total digital (que nunca entra al
 * esperado). Lo calcula el sistema; nunca llega del formulario.
 */
public record ResumenCaja(BigDecimal fondo, BigDecimal efectivo, int pagosEfectivo, int pagosDigitales,
		BigDecimal digital) {

	public BigDecimal esperado() {
		return fondo.add(efectivo);
	}
}
