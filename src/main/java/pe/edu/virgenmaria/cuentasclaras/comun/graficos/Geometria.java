package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cuentas de la geometría de los gráficos. El dinero sigue en {@link BigDecimal}; aquí solo se calcula qué parte del
 * ancho o del alto ocupa cada barra, con dos decimales y punto (lo que entiende un atributo SVG).
 */
public final class Geometria {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	private Geometria() {
	}

	/** Qué % de {@code maximo} es {@code valor}, entre 0 y 100, como «42.50». Con máximo 0, «0.00». */
	public static String porcentaje(BigDecimal valor, BigDecimal maximo) {
		if (maximo == null || maximo.signum() <= 0 || valor == null || valor.signum() <= 0) {
			return "0.00";
		}
		BigDecimal p = valor.multiply(CIEN).divide(maximo, 2, RoundingMode.HALF_UP);
		return (p.compareTo(CIEN) > 0 ? CIEN.setScale(2) : p).toPlainString();
	}

	/** Un número decimal para un atributo SVG («12.34»). */
	public static String numero(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	/**
	 * El tope «redondo» del eje: el primer 1, 2, 2.5 o 5 por una potencia de 10 que no es menor que {@code maximo} (1 830
	 * → 2 000; 4 100 → 5 000). Con 0 o menos, 100: un eje sin datos igual tiene escala.
	 */
	public static BigDecimal topeRedondo(BigDecimal maximo) {
		if (maximo == null || maximo.signum() <= 0) {
			return CIEN;
		}
		BigDecimal potencia = BigDecimal.ONE.movePointRight(maximo.setScale(0, RoundingMode.CEILING).precision() - 1);
		for (BigDecimal paso : new BigDecimal[] { BigDecimal.ONE, BigDecimal.valueOf(2), new BigDecimal("2.5"),
				BigDecimal.valueOf(5), BigDecimal.TEN }) {
			BigDecimal tope = potencia.multiply(paso);
			if (tope.compareTo(maximo) >= 0) {
				return tope.stripTrailingZeros().scale() < 0 ? tope.setScale(0) : tope;
			}
		}
		return potencia.multiply(BigDecimal.TEN);
	}
}
