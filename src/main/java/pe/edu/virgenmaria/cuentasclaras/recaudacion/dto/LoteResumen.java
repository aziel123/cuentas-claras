package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un lote en la lista. {@code total} es {@code null} mientras nadie lo confirmó a ciegas (no se muestra). */
public record LoteResumen(Long id, LocalDateTime cargadoEn, String cargadoPor, String banco, LocalDate desde,
		LocalDate hasta, int lineas, BigDecimal total, String estado, String etiqueta, String variante, int aplicadas,
		int excepciones) {
}
