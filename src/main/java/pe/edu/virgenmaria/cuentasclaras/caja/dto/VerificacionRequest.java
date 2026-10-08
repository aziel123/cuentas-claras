package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que Administración vio en el estado de cuenta del banco, escrito A CIEGAS (no ve lo registrado): para
 * «Encontrado», la operación, la fecha y el monto que el sistema compara con lo registrado; «No aparece» exige una
 * nota.
 */
public record VerificacionRequest(
		@NotNull(message = "Indica si lo encontraste en el banco.") ResultadoVerificacion resultado,
		@Size(max = 40, message = "El número de operación tiene hasta 30 caracteres.") String operacion,
		LocalDate fecha,
		@Digits(integer = 7, fraction = 2, message = "Escribe un monto de hasta S/ 9,999,999.99, con 2 decimales como máximo.") BigDecimal monto,
		@Size(max = 500, message = "La nota tiene hasta 500 caracteres.") String nota) {

	/** «No aparece en el banco», con su nota. */
	public static VerificacionRequest noAparece(String nota) {
		return new VerificacionRequest(ResultadoVerificacion.NO_ENCONTRADO, null, null, null, nota);
	}

	/** Lo que se vio en el banco, para comparar. */
	public static VerificacionRequest delBanco(String operacion, LocalDate fecha, BigDecimal monto) {
		return new VerificacionRequest(ResultadoVerificacion.ENCONTRADO, operacion, fecha, monto, null);
	}
}
