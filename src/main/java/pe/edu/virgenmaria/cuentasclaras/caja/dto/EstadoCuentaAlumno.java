package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de cuenta de un alumno: sus cuotas (monto, descuento, pagado y saldo), los pagos que las tocaron (con su
 * comprobante, cajera, estado y nota de crédito) y sus descuentos. {@code puedeSolicitar}: quien mira es de
 * Administración y puede pedir anulaciones.
 */
public record EstadoCuentaAlumno(Long alumnoId, String alumno, String documento, Long familiaId, String familia, String grado,
		LocalDate hoy, List<Cuota> cuotas, BigDecimal totalMonto, BigDecimal totalDescuento, BigDecimal totalPagado,
		BigDecimal totalSaldo, List<PagoAlumno> pagos, List<DescuentoAlumno> descuentos, boolean puedeSolicitar) {

	public record Cuota(Long id, String descripcion, LocalDate vencimiento, BigDecimal monto, BigDecimal descuento,
			BigDecimal pagado, BigDecimal saldo, String estadoEtiqueta, String estadoVariante) {
	}

	public record PagoAlumno(Long id, LocalDate fecha, Long comprobanteId, String comprobante, String medio,
			BigDecimal total, String cajera, String estado, String estadoVariante, boolean anulado, boolean puedeSolicitar,
			Long notaCreditoId, String notaCredito, String reemplazaA) {
	}

	public record DescuentoAlumno(String tipo, String valor, BigDecimal total, String estado, String estadoVariante,
			String pedidoPor, String resueltoPor) {
	}
}
