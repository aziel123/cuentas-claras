package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo cobrado (pagos VIGENTES) en un día de caja: total, cantidad y la parte en efectivo. Montos con escala 2. */
public record CobradoDia(LocalDate fecha, BigDecimal total, long cantidad, BigDecimal efectivo, long pagosEfectivo) {
}
