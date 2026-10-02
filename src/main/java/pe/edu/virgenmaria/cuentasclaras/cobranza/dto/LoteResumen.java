package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un lote en la lista de saldo inicial. */
public record LoteResumen(Long id, int anio, LocalDate fechaCorte, String documentoReferencia,
		BigDecimal totalDeclarado, BigDecimal totalLineas, boolean cuadra, int lineas, String estado,
		String estadoEtiqueta, String estadoVariante, String creadoPor, LocalDateTime creadoEn) {
}
