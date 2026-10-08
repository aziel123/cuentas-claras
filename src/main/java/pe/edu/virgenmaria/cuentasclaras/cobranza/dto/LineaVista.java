package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una línea del lote. {@code cuotaId}: la cuota que creó al confirmarse. {@code cronograma}: lo que el alumno ya debe
 * ese año, para compararlo; {@code alerta}: duplicado en el lote o deuda que ya está en su cronograma.
 */
public record LineaVista(Long id, Long alumnoId, String alumno, String documento, String concepto,
		String descripcion, BigDecimal monto, LocalDate vencimiento, boolean quitada, String agregadaPor,
		Long cuotaId, String cronograma, String alerta) {
}
