package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve el apoderado en su celular: las cuotas por pagar de cada hijo (de SU familia), el código de pago para el
 * banco, sus pagos en línea recientes y sus comprobantes.
 */
public record CuentaEnLinea(String familia, String apoderado, List<Hijo> hijos, BigDecimal totalPorPagar,
		List<OrdenReciente> ordenes, List<PagoReciente> pagos, boolean pagoEnLineaDisponible, boolean simulada,
		BigDecimal montoMaximo, LocalDate hoy) {

	public record Hijo(Long alumnoId, String nombre, String codigoPago, List<CuotaPorPagar> cuotas) {
	}

	public record CuotaPorPagar(Long id, String descripcion, LocalDate vencimiento, BigDecimal saldo, String estado,
			String variante, boolean pagable, String aviso) {
	}

	public record OrdenReciente(String referencia, LocalDateTime creadaEn, BigDecimal monto, String estado,
			String variante) {
	}

	public record PagoReciente(Long comprobanteId, String comprobante, LocalDate fecha, BigDecimal total, String medio,
			String estado) {
	}

	public boolean sinDeuda() {
		return hijos.stream().allMatch(h -> h.cuotas().isEmpty());
	}
}
