package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Conversión de celdas numéricas sin pasar por {@code double} (un DNI o un monto nunca se redondean).
 */
public final class ValoresXlsx {

	private ValoresXlsx() {
	}

	/** El número de la celda como entero exacto («1234567», nunca «1.234567E6»), o vacío si no es entero. */
	public static Optional<String> entero(CeldaXlsx celda) {
		if (celda == null || celda.tipo() != TipoCelda.NUMERO || celda.valorCrudo() == null) {
			return Optional.empty();
		}
		try {
			BigDecimal numero = new BigDecimal(celda.valorCrudo().strip());
			BigDecimal sinCeros = numero.stripTrailingZeros();
			if (sinCeros.scale() > 0) {
				return Optional.empty();
			}
			return Optional.of(sinCeros.toBigIntegerExact().toString());
		}
		catch (NumberFormatException | ArithmeticException e) {
			return Optional.empty();
		}
	}

	/**
	 * Fecha de una celda numérica (serial de Excel, entero) según el sistema de fechas del libro, o de una celda de
	 * fecha ISO. Vacío si no es una fecha válida.
	 */
	public static Optional<LocalDate> fecha(CeldaXlsx celda, boolean fecha1904) {
		if (celda == null || celda.valorCrudo() == null) {
			return Optional.empty();
		}
		if (celda.tipo() == TipoCelda.FECHA_ISO) {
			try {
				String texto = celda.valorCrudo().strip();
				return Optional.of(LocalDate.parse(texto.length() > 10 ? texto.substring(0, 10) : texto));
			}
			catch (DateTimeParseException e) {
				return Optional.empty();
			}
		}
		Optional<String> entero = entero(celda);
		if (entero.isEmpty() || entero.get().length() > 7) {
			return Optional.empty();
		}
		double serial = Integer.parseInt(entero.get());
		if (!DateUtil.isValidExcelDate(serial) || serial < 1) {
			return Optional.empty();
		}
		return Optional.of(DateUtil.getLocalDateTime(serial, fecha1904).toLocalDate());
	}
}
