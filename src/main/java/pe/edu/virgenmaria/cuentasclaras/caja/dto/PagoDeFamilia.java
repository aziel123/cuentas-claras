package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un pago registrado de una familia para la llamada de control (sprint 6, tanda 3): fecha de caja, medio, monto (escala 2),
 * estado, comprobante («B001-00000012») y quién lo registró. Sin datos de la familia: quien llama ya sabe a quién llama.
 */
public record PagoDeFamilia(LocalDate fecha, MedioPago medio, BigDecimal total, EstadoPago estado, String comprobante,
		String registradoPor) {
}
