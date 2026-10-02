package pe.edu.virgenmaria.cuentasclaras.comun.dinero;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Montos en soles (PEN). Reglas del colegio:
 * <ul>
 *   <li>Siempre {@link BigDecimal} con escala 2; nada pasa por {@code double}.</li>
 *   <li>Al entrar, más de 2 decimales se <b>rechaza</b>: nunca se redondea en silencio.</li>
 *   <li>Se compara con {@code compareTo} o {@code signum}, nunca con {@code equals} (2.0 ≠ 2.00 para equals).</li>
 *   <li>Cuando haya prorrateos (sprint 3) se usará {@link #REDONDEO} por cuota.</li>
 * </ul>
 */
public final class Dinero {

	public static final String MONEDA = "PEN";

	public static final int ESCALA = 2;

	/** Para los prorrateos y descuentos que vendrán; hoy no se divide nada. */
	public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

	public static final BigDecimal CERO = new BigDecimal("0.00");

	/** Tope de una cuota, una pensión o una línea de saldo: 99,999.99 (DECIMAL(10,2) con margen). */
	public static final BigDecimal MAXIMO = new BigDecimal("99999.99");

	/** Tope del total declarado de un lote de saldo inicial. */
	public static final BigDecimal MAXIMO_TOTAL = new BigDecimal("99999999.99");

	/** Separador de miles con coma y decimal con punto, como se escribe en el Perú: S/ 1,250.00. */
	private static final DecimalFormatSymbols SIMBOLOS = DecimalFormatSymbols.getInstance(Locale.US);

	private Dinero() {
	}

	/**
	 * Lleva el monto a escala 2 sin redondear.
	 *
	 * @throws ReglaNegocioException si es nulo o tiene más de 2 decimales significativos (1.005)
	 */
	public static BigDecimal normalizar(BigDecimal monto) {
		if (monto == null) {
			throw new ReglaNegocioException("Indica el monto.");
		}
		try {
			return monto.setScale(ESCALA, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException e) {
			throw new ReglaNegocioException("El monto debe tener como máximo 2 decimales.");
		}
	}

	/** Normaliza y exige un monto entre 0.01 y {@link #MAXIMO}. */
	public static BigDecimal positivo(BigDecimal monto, String nombre) {
		return enRango(monto, new BigDecimal("0.01"), MAXIMO, nombre);
	}

	/** Normaliza y exige un monto entre {@code minimo} y {@code maximo} (ambos incluidos). */
	public static BigDecimal enRango(BigDecimal monto, BigDecimal minimo, BigDecimal maximo, String nombre) {
		BigDecimal normalizado = normalizar(monto);
		if (normalizado.compareTo(minimo) < 0 || normalizado.compareTo(maximo) > 0) {
			throw new ReglaNegocioException(capitalizar(nombre) + " debe estar entre " + formatear(minimo) + " y "
					+ formatear(maximo) + ".");
		}
		return normalizado;
	}

	public static BigDecimal sumar(Collection<BigDecimal> montos) {
		BigDecimal total = CERO;
		for (BigDecimal monto : montos) {
			total = total.add(normalizar(monto));
		}
		return total;
	}

	public static boolean iguales(BigDecimal a, BigDecimal b) {
		return Objects.requireNonNull(a).compareTo(Objects.requireNonNull(b)) == 0;
	}

	/** 1250 → «S/ 1,250.00»; -5 → «-S/ 5.00». */
	public static String formatear(BigDecimal monto) {
		if (monto == null) {
			return "";
		}
		BigDecimal escalado = monto.setScale(ESCALA, REDONDEO);
		DecimalFormat formato = new DecimalFormat("#,##0.00", SIMBOLOS);
		String numero = formato.format(escalado.abs());
		return (escalado.signum() < 0 ? "-" : "") + "S/ " + numero;
	}

	private static String capitalizar(String texto) {
		return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}
}
