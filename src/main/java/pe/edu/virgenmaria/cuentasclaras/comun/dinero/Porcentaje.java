package pe.edu.virgenmaria.cuentasclaras.comun.dinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * Porcentajes enteros para el panel (correcciones del sprint 6, observación de QA): redondeo HALF_UP, pero un valor que no
 * es exactamente 100 nunca se muestra como «100 %» (con 999 de 1000 pagos digitales, «99 %») ni uno que no es
 * exactamente 0 como «0 %» («1 %»). Así «100 %» y «0 %» siempre significan todo y nada.
 */
public final class Porcentaje {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	private Porcentaje() {
	}

	/** {@code parte} de {@code total}; vacío si el total es 0 (no es lo mismo que 0 %). */
	public static OptionalLong de(BigDecimal parte, BigDecimal total) {
		if (total == null || total.signum() == 0) {
			return OptionalLong.empty();
		}
		long redondeado = parte.multiply(CIEN).divide(total, 0, RoundingMode.HALF_UP).longValue();
		int comparado = parte.compareTo(total);
		if (redondeado >= 100 && comparado < 0) {
			return OptionalLong.of(99);
		}
		if (redondeado <= 0 && parte.signum() > 0) {
			return OptionalLong.of(1);
		}
		return OptionalLong.of(redondeado);
	}

	public static OptionalLong de(long parte, long total) {
		return de(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
	}
}
