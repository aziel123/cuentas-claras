package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un movimiento del extracto ya validado: su línea en el archivo, la fecha contable, la glosa del banco (hasta 200
 * caracteres), la operación en forma canónica (o {@code null}), la referencia, si es abono o cargo, el monto (positivo)
 * y el saldo después del movimiento.
 */
public record FilaExtracto(int linea, LocalDate fecha, String descripcion, String operacion, String referencia,
		TipoMovimiento tipo, BigDecimal monto, BigDecimal saldo) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** Cuánto mueve el saldo: + abono, − cargo. */
	public BigDecimal efecto() {
		return tipo == TipoMovimiento.ABONO ? monto : monto.negate();
	}

	/** Para comparar un día ya cargado con lo guardado: tipo, monto, operación y saldo. */
	public String huella() {
		return tipo + "|" + monto.toPlainString() + "|" + (operacion == null ? "" : operacion) + "|"
				+ (saldo == null ? "" : saldo.toPlainString());
	}
}
