package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.Cell;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * El ÚNICO lugar del código que escribe un {@code double} (ArchUnit): Excel guarda los números en doble precisión. El
 * monto debe tener como máximo 2 decimales y ser menor que mil millones en valor absoluto; hasta ahí la conversión es
 * exacta a dos decimales y se comprueba de ida y vuelta. Los totales de control de cada reporte se repiten como texto
 * calculado con {@code BigDecimal}.
 */
final class CeldaDinero {

	private static final BigDecimal LIMITE = new BigDecimal("1000000000");

	/** 2^53: hasta aquí un entero cabe exacto en un double. */
	private static final long ENTERO_MAXIMO = 9_007_199_254_740_992L;

	private CeldaDinero() {
	}

	static void escribir(Cell celda, BigDecimal monto) {
		Objects.requireNonNull(monto, "monto");
		BigDecimal exacto;
		try {
			exacto = monto.setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException e) {
			throw new IllegalArgumentException("Un monto del reporte tiene más de 2 decimales", e);
		}
		if (exacto.abs().compareTo(LIMITE) >= 0) {
			throw new IllegalArgumentException("Monto fuera del rango del reporte");
		}
		double valor = exacto.doubleValue();
		if (BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).compareTo(exacto) != 0) {
			throw new IllegalStateException("El monto no se puede escribir exacto en Excel");
		}
		celda.setCellValue(valor);
	}

	static void escribirEntero(Cell celda, long numero) {
		if (Math.abs(numero) > ENTERO_MAXIMO) {
			throw new IllegalArgumentException("Número fuera del rango del reporte");
		}
		celda.setCellValue((double) numero);
	}
}
