package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.OptionalLong;

/**
 * De lo que vence en el mes (cuotas no anuladas, descontados sus descuentos), cuánto ya se pagó.
 */
public record AvanceMes(YearMonth mes, BigDecimal vence, BigDecimal pagado) {

	/** Vacío si en el mes no vence nada. */
	public OptionalLong porcentaje() {
		return pe.edu.virgenmaria.cuentasclaras.comun.dinero.Porcentaje.de(pagado, vence);
	}
}
