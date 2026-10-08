package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.OptionalLong;

/**
 * De lo que vence en el mes (cuotas no anuladas, descontados sus descuentos), cuánto ya se pagó.
 */
public record AvanceMes(YearMonth mes, BigDecimal vence, BigDecimal pagado) {

	/** Vacío si en el mes no vence nada. */
	public OptionalLong porcentaje() {
		return vence.signum() == 0 ? OptionalLong.empty()
				: OptionalLong.of(pagado.multiply(BigDecimal.valueOf(100)).divide(vence, 0, RoundingMode.HALF_UP)
						.longValue());
	}
}
