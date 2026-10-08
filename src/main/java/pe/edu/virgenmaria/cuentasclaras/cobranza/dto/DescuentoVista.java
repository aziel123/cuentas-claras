package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un descuento en la lista. */
public record DescuentoVista(Long id, Long alumnoId, String alumno, String tipo, String valor, int cuotas,
		BigDecimal total, String estado, String estadoVariante, String solicitadoPor, LocalDateTime solicitadoEn,
		String resueltoPor) {
}
