package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una línea en el detalle del lote. {@code monto} es {@code null} mientras el lote no esté confirmado. */
public record LineaVista(Long id, int numero, LocalDate fecha, String codigo, String alumno, BigDecimal monto,
		String moneda, String operacion, String estado, String etiqueta, String variante, String motivo, String detalle,
		boolean critica, String comprobante) {

	public boolean enExcepcion() {
		return "EXCEPCION".equals(estado);
	}
}
