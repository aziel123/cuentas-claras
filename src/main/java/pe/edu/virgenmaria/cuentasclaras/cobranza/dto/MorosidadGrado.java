package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.math.BigDecimal;

/**
 * Morosidad de un grado en un año (decisión 74). <b>Nunca por sección</b>: una sección de 12 alumnos permite identificar
 * a un niño (Ley 29733 e INDECOPI). {@code grado == null} es la fila «Sin matrícula en ese año» (saldo inicial o alumnos
 * sin matrícula en el año de la cuota); {@code retirados} es la fila «Retirados o sin matrícula activa» (QA-S6-4: su deuda
 * anterior al retiro no se suma a un grado, que nunca tiene más alumnos con deuda que matriculados). Los tramos cuentan
 * alumnos según su cuota vencida más antigua.
 */
public record MorosidadGrado(Grado grado, boolean retirados, long matriculados, long conDeuda, BigDecimal monto,
		Tramos tramos) {

	public static final String SIN_MATRICULA = "Sin matrícula en ese año";

	public static final String RETIRADOS = "Retirados o sin matrícula activa";

	public String etiqueta() {
		return retirados ? RETIRADOS : grado == null ? SIN_MATRICULA : grado.etiqueta();
	}
}
