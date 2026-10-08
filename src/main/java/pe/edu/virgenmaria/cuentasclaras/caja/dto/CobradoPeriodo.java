package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

/**
 * Lo cobrado en un periodo (sprint 6, decisión 67): pagos VIGENTES por su día de caja (Lima). Lo anulado se informa
 * aparte ({@link AnuladoPeriodo}). Montos con escala 2.
 */
public record CobradoPeriodo(LocalDate desde, LocalDate hasta, BigDecimal total, long cantidad, BigDecimal efectivo,
		long pagosEfectivo, List<PorMedio> porMedio, List<PorCanal> porCanal) {

	public record PorMedio(MedioPago medio, long cantidad, BigDecimal total) {

		public String etiqueta() {
			return medio.etiqueta();
		}
	}

	public record PorCanal(CanalCaja canal, long cantidad, BigDecimal total) {

		public String etiqueta() {
			return canal.etiqueta();
		}
	}

	public CobradoPeriodo {
		porMedio = List.copyOf(porMedio);
		porCanal = List.copyOf(porCanal);
	}

	public BigDecimal digital() {
		return total.subtract(efectivo);
	}

	public long pagosDigitales() {
		return cantidad - pagosEfectivo;
	}

	/** % de pagos digitales por NÚMERO de pagos (decisión 65). Vacío si no hubo pagos: no es lo mismo que 0 %. */
	public OptionalLong porcentajeDigital() {
		return cantidad == 0 ? OptionalLong.empty()
				: OptionalLong.of(BigDecimal.valueOf(pagosDigitales() * 100L)
						.divide(BigDecimal.valueOf(cantidad), 0, RoundingMode.HALF_UP).longValue());
	}

	/** % digital por monto (se muestra debajo). Vacío si no hubo pagos. */
	public OptionalLong porcentajeDigitalPorMonto() {
		return total.signum() == 0 ? OptionalLong.empty()
				: OptionalLong.of(digital().multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP)
						.longValue());
	}
}
