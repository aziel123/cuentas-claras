package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.util.List;

/** El antes y el después de cada cuota, para revisar el pedido antes de enviarlo a aprobación. */
public record RevisionDescuento(DescuentoRequest pedido, String alumno, String tipo, String valor, List<Linea> lineas,
		BigDecimal total) {

	/** Una cuota: cuánto se debía y cuánto se deberá. {@code exonera}: el descuento cubre toda la cuota. */
	public record Linea(String descripcion, BigDecimal monto, BigDecimal saldoAntes, BigDecimal ajuste,
			BigDecimal saldoDespues, boolean exonera) {
	}
}
