package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Una liquidación tal como la entrega la pasarela (API o archivo): su referencia, cuándo se liquidó, cuándo abona al
 * banco y sus líneas con la comisión y el IGV de la comisión.
 */
public record LiquidacionLeida(String referencia, LocalDate fechaLiquidacion, LocalDate fechaAbono, List<Linea> lineas) {

	public LiquidacionLeida {
		Objects.requireNonNull(referencia, "referencia");
		Objects.requireNonNull(fechaLiquidacion, "fechaLiquidacion");
		Objects.requireNonNull(fechaAbono, "fechaAbono");
		lineas = List.copyOf(lineas);
	}

	/** Una línea: el cargo (por su operación canónica), su bruto, la comisión y el IGV de la comisión. */
	public record Linea(TipoLineaLiquidacion tipo, String operacion, BigDecimal bruto, BigDecimal comision,
			BigDecimal igv) {
	}
}
