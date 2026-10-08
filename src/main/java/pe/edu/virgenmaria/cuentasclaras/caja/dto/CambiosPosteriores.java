package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que cambió en el libro de pagos de un rango de días DESPUÉS de un momento (sprint 6, tanda 2; P4): los pagos
 * registrados después (un pago en línea o del banco que llegó tarde) y los anulados después (anulación aprobada). Con
 * esto se explica la diferencia entre la foto de un día ya informado y lo que dicen hoy los libros.
 */
public record CambiosPosteriores(List<Movimiento> registrados, List<Movimiento> anulados) {

	/**
	 * Un pago del rango: su día de caja, medio y total, cuándo se registró y (si se anuló) cuándo se aprobó la anulación.
	 */
	public record Movimiento(LocalDate fecha, MedioPago medio, BigDecimal total, LocalDateTime registradoEn,
			LocalDateTime anuladoEn) {
	}

	public CambiosPosteriores {
		registrados = List.copyOf(registrados);
		anulados = List.copyOf(anulados);
	}
}
