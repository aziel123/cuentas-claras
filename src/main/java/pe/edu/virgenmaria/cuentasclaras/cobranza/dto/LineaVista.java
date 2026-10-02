package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una línea del lote. {@code cuotaId}: la cuota que creó al confirmarse. */
public record LineaVista(Long id, Long alumnoId, String alumno, String documento, String concepto,
		String descripcion, BigDecimal monto, LocalDate vencimiento, boolean quitada, String agregadaPor,
		Long cuotaId) {
}
