package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de generar cronogramas.
 *
 * @param omitidas cuotas que no se generaron porque esa deuda ya existía (por ejemplo, cargada como saldo inicial)
 */
public record ResultadoGeneracion(int matriculas, int cuotas, BigDecimal total, List<String> omitidas) {

	public ResultadoGeneracion sumar(ResultadoGeneracion otro) {
		List<String> todas = new java.util.ArrayList<>(omitidas);
		todas.addAll(otro.omitidas());
		return new ResultadoGeneracion(matriculas + otro.matriculas(), cuotas + otro.cuotas(),
				total.add(otro.total()), List.copyOf(todas));
	}
}
