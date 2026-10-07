package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El estado de cuenta COMPLETO de la familia (pantalla 2; regla 10 de la skill: transparencia). Todas las cuotas por hijo
 * y por año (también las anuladas, con su motivo y el rol de quien aprobó), los pagos con su comprobante y quién los
 * registró, y los descuentos con quién los aprobó. Cualquier deuda falsa o pago faltante sale a la luz.
 */
public record EstadoCuentaFamilia(String familia, BigDecimal totalPendiente, BigDecimal totalVencido, List<Hijo> hijos,
		List<PagoFila> pagos, List<DescuentoFila> descuentos) {

	public record Hijo(Long alumnoId, String nombre, List<Anio> anios) {
	}

	public record Anio(int anio, List<CuotaFila> cuotas, BigDecimal saldo) {
	}

	/**
	 * {@code pagadaCon}: «B001-00000231 · 05/10/2026 · Yape · en caja» de cada pago vigente que la tocó.
	 * {@code anulacion}: motivo y «aprobó: Dirección» si la cuota se anuló.
	 */
	public record CuotaFila(Long id, String descripcion, LocalDate vencimiento, BigDecimal monto, BigDecimal descuento,
			BigDecimal pagado, BigDecimal saldo, String estado, String variante, List<String> pagadaCon,
			String anulacion) {
	}

	/** {@code anulacion}: motivo y rol de quien aprobó si el pago se anuló. */
	public record PagoFila(Long comprobanteId, String comprobante, LocalDate fecha, String medio, String registradoPor,
			BigDecimal total, boolean vigente, String anulacion) {
	}

	public record DescuentoFila(String alumno, String tipo, String valor, BigDecimal total, String estado,
			String variante, String aprobo) {
	}
}
