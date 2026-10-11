package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que se pide a la pasarela: la referencia pública de la orden, el monto (y en céntimos), PEN, una descripción SIN
 * nombres de alumnos («Pago {nombre del colegio} · referencia»), el vencimiento y la URL de retorno.
 */
public record SolicitudOrden(String referencia, BigDecimal monto, long centimos, String moneda, String descripcion,
		LocalDateTime venceEn, String urlRetorno, Long colegioId) {
}
