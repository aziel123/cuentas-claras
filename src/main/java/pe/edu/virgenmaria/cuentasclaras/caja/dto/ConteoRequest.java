package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Denominacion;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Primer conteo a ciegas: el total contado (incluye el fondo fijo) o las denominaciones (el servidor suma). Nunca lleva
 * el esperado: lo calcula el sistema con el libro.
 */
public record ConteoRequest(
		@Digits(integer = 7, fraction = 2, message = "Escribe un monto de hasta S/ 9,999,999.99, con 2 decimales como máximo.")
		@DecimalMin(value = "0.00", message = "El conteo no puede ser negativo.") BigDecimal contado,
		Map<Denominacion, Integer> denominaciones) {
}
