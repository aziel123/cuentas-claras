package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.math.BigDecimal;

/**
 * Morosidad de un grado en un año (decisión 74). <b>Nunca por sección</b>: una sección de 12 alumnos permite identificar
 * a un niño (Ley 29733 e INDECOPI). {@code grado == null} es la fila «Sin matrícula en ese año» (saldo inicial o alumnos
 * sin matrícula en el año de la cuota). Los tramos cuentan alumnos según su cuota vencida más antigua.
 */
public record MorosidadGrado(Grado grado, long matriculados, long conDeuda, BigDecimal monto, Tramos tramos) {

	public static final String SIN_MATRICULA = "Sin matrícula en ese año";

	public String etiqueta() {
		return grado == null ? SIN_MATRICULA : grado.etiqueta();
	}
}
