package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** El depósito del efectivo de una caja cerrada, con el número de operación del voucher del banco. */
public record DepositoRequest(
		@NotNull(message = "Elige la caja que depositas.") Long cajaId,
		@NotBlank(message = "Elige la cuenta del colegio.") @Size(max = 60) String cuenta,
		@NotBlank(message = "Escribe el número de operación del voucher.")
		@Size(max = 30, message = "El número de operación tiene hasta 30 caracteres.") String numeroOperacion,
		@NotNull(message = "Indica la fecha del depósito.")
		@PastOrPresent(message = "La fecha del depósito no puede ser futura.") LocalDate fecha,
		@NotNull(message = "Escribe el monto depositado.") @Positive(message = "El monto depositado debe ser mayor que 0.")
		@Digits(integer = 7, fraction = 2, message = "Escribe el monto con hasta 2 decimales.") BigDecimal monto,
		@Size(max = 500, message = "La explicación tiene hasta 500 caracteres.") String explicacion) {
}
