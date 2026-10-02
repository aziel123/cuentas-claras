package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una deuda previa. El alumno se busca por su número de documento. PENSION y MATRICULA llevan el año real de la deuda
 * (y la pensión su mes); su descripción la pone el sistema. En una PENSION sin vencimiento, vence el último día del mes
 * (o el día del corte). En OTRO la descripción es uno de los conceptos del reglamento.
 */
public record LineaSaldoRequest(
		@NotBlank(message = "Escribe el DNI o documento del alumno.")
		@Size(max = 20, message = "El documento admite hasta 20 caracteres.")
		String documentoAlumno,
		@NotNull(message = "Elige el concepto.")
		ConceptoSaldo concepto,
		@Min(value = 2000, message = "Revisa el año de la deuda.") @Max(value = 2100, message = "Revisa el año de la deuda.")
		Integer anio,
		@Min(value = 1, message = "El mes va de 1 a 12.") @Max(value = 12, message = "El mes va de 1 a 12.")
		Integer mes,
		@Size(max = 80, message = "La descripción admite hasta 80 caracteres.")
		String descripcion,
		@NotNull(message = "Indica el monto.")
		@Digits(integer = 5, fraction = 2, message = "El monto debe tener como máximo 2 decimales.")
		@DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero.")
		@DecimalMax(value = "99999.99", message = "El monto no puede superar S/ 99,999.99.")
		BigDecimal monto,
		LocalDate vencimiento) {
}
