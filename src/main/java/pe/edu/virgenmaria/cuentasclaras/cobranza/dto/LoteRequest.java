package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cabecera de un lote de saldo inicial: año, corte, referencia al informe del contador y total de control. */
public record LoteRequest(
		@NotNull(message = "Elige el año escolar.")
		Long anioId,
		@NotNull(message = "Indica la fecha de corte.")
		LocalDate fechaCorte,
		@NotBlank(message = "Escribe la referencia al informe del contador.")
		@Size(max = 150, message = "La referencia admite hasta 150 caracteres.")
		String documentoReferencia,
		@NotNull(message = "Indica el total declarado por el contador.")
		@Digits(integer = 8, fraction = 2, message = "El monto debe tener como máximo 2 decimales.")
		@DecimalMin(value = "0.01", message = "El total debe ser mayor que cero.")
		@DecimalMax(value = "99999999.99", message = "El total no puede superar S/ 99,999,999.99.")
		BigDecimal totalDeclarado) {
}
