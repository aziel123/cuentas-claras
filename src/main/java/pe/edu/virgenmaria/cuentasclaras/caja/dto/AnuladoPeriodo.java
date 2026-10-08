package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Anulaciones de pagos APROBADAS en un periodo (por fecha de aprobación), para que nada desaparezca sin verse. */
public record AnuladoPeriodo(LocalDate desde, LocalDate hasta, long cantidad, BigDecimal total) {
}
