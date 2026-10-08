package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una cuota que el plan manda generar, antes de guardarse.
 *
 * @param numero      mes de la pensión (1 a 12); nulo en la matrícula
 * @param clave       idempotencia de la creación: «MAT:{matriculaId}» o «PEN:{matriculaId}:{mes}»
 * @param obligacion  la deuda: «MAT-2027» o «PEN-2027-09»
 */
public record CuotaPlanificada(TipoCuota tipo, Integer numero, String descripcion, BigDecimal monto,
		LocalDate vencimiento, String clave, String obligacion) {
}
