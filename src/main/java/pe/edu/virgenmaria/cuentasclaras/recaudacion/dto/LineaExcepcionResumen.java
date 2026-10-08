package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una línea por revisar en la lista principal. */
public record LineaExcepcionResumen(Long id, Long loteId, int numero, LocalDate fecha, String codigo, String alumno,
		BigDecimal monto, String moneda, String motivo, boolean critica, String solicitudPendiente) {
}
