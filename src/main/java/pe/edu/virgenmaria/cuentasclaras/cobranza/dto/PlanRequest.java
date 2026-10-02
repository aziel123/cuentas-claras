package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formulario del plan. Los montos llegan como {@link BigDecimal} (punto decimal): nunca pasan por double. Las
 * fechas de las pensiones vienen en {@code vencimientos[0..11]}; las casillas vacías se ignoran.
 */
public record PlanRequest(
		@NotNull(message = "Indica el monto de la matrícula (0 si no se cobra).")
		@Digits(integer = 5, fraction = 2, message = "El monto debe tener como máximo 2 decimales y 5 enteros.")
		@DecimalMin(value = "0.00", message = "La matrícula no puede ser negativa.")
		BigDecimal montoMatricula,
		@NotNull(message = "Indica el vencimiento de la matrícula.")
		LocalDate vencimientoMatricula,
		@NotNull(message = "Indica el monto de la pensión.")
		@Digits(integer = 5, fraction = 2, message = "El monto debe tener como máximo 2 decimales y 5 enteros.")
		@DecimalMin(value = "0.01", message = "La pensión debe ser mayor que cero.")
		@DecimalMax(value = "99999.99", message = "La pensión no puede superar S/ 99,999.99.")
		BigDecimal montoPension,
		@Size(max = ConfiguracionPlan.MAX_PENSIONES, message = "Hasta 12 pensiones.")
		List<LocalDate> vencimientos,
		LocalDate cobroDesde) {

	/** Casillas para el formulario: las fechas del plan y, hasta completar 12, vacías. */
	public List<LocalDate> casillas() {
		List<LocalDate> casillas = new ArrayList<>(fechas());
		while (casillas.size() < ConfiguracionPlan.MAX_PENSIONES) {
			casillas.add(null);
		}
		return casillas;
	}

	/** La fecha de la casilla {@code i} (0 a 11) o {@code null} si está vacía. */
	public LocalDate casilla(int i) {
		List<LocalDate> casillas = casillas();
		return i >= 0 && i < casillas.size() ? casillas.get(i) : null;
	}

	public List<LocalDate> fechas() {
		return vencimientos == null ? List.of() : vencimientos.stream().filter(Objects::nonNull).toList();
	}

	public ConfiguracionPlan configuracion() {
		return new ConfiguracionPlan(montoMatricula, vencimientoMatricula, montoPension, fechas(), cobroDesde);
	}

	public static PlanRequest de(ConfiguracionPlan c) {
		return new PlanRequest(c.montoMatricula(), c.vencimientoMatricula(), c.montoPension(), c.vencimientos(),
				c.cobroDesde());
	}
}
