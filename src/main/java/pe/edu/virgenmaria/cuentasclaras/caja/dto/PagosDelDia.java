package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pagos del día de la caja de quien la consulta. A propósito NO lleva totales: mientras la caja está abierta la cajera
 * no ve el efectivo esperado (así el cierre ciego tiene sentido).
 */
public record PagosDelDia(LocalDate fecha, List<PagoDelDia> pagos) {

	/** Una fila de la tabla de pagos del día. */
	public record PagoDelDia(Long id, LocalTime hora, String comprobante, String alumnos, String conceptos, String medio,
			BigDecimal total, String estado, String estadoVariante, boolean anulado) {
	}
}
