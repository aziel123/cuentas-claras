package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Denominacion;

import java.math.BigDecimal;
import java.util.Map;

/** El reconteo (solo uno): con explicación obligatoria de por qué no coincidió. Siempre cierra la caja. */
public record ReconteoRequest(
		@Digits(integer = 7, fraction = 2, message = "Escribe el monto con hasta 2 decimales.")
		@DecimalMin(value = "0.00", message = "El conteo no puede ser negativo.") BigDecimal contado,
		Map<Denominacion, Integer> denominaciones,
		@NotBlank(message = "Explica qué pasó con el primer conteo.")
		@Size(min = 10, max = 500, message = "La explicación debe tener entre 10 y 500 caracteres.") String explicacion) {
}
