package pe.edu.virgenmaria.cuentasclaras.conciliacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * {@code cuentasclaras.conciliacion} (sprint 4, tanda 3). Un valor imposible impide arrancar.
 *
 * @param diasToleranciaFecha         días hábiles de diferencia que admite una pareja SUGERIDA
 * @param toleranciaMontoLiquidacion  diferencia de monto que admite una liquidación SUGERIDA (S/ 0.00 por defecto)
 * @param horaLimiteExtracto          hora del día hábil siguiente en que el extracto del día anterior ya debe estar
 *                                    subido (después, alerta)
 * @param intentosConfirmacion        saldos a ciegas distintos antes de RECHAZAR el extracto
 * @param muestreoDiario              movimientos al azar (semilla = fecha) que Promotoría compara con su app del banco
 * @param muestreoConfirmacion        movimientos al azar que ve quien confirma el extracto
 * @param patronAbonoRecaudacion      texto de la glosa con que el banco abona el lote de recaudación (vacío: no se usa la
 *                                    regla EXACTA por glosa y el lote se empareja como SUGERIDA)
 * @param abonoRecaudacion            cómo abona el banco la recaudación: el lote del día (POR_LOTE) o pago por pago
 * @param maximoLineas                líneas como máximo de un archivo de extracto
 */
@ConfigurationProperties("cuentasclaras.conciliacion")
public record PropiedadesConciliacion(
		@DefaultValue("2") int diasToleranciaFecha,
		@DefaultValue("0.00") BigDecimal toleranciaMontoLiquidacion,
		@DefaultValue("12:00") LocalTime horaLimiteExtracto,
		@DefaultValue("2") int intentosConfirmacion,
		@DefaultValue("3") int muestreoDiario,
		@DefaultValue("3") int muestreoConfirmacion,
		@DefaultValue("") String patronAbonoRecaudacion,
		@DefaultValue("POR_LOTE") AbonoRecaudacion abonoRecaudacion,
		@DefaultValue("3000") int maximoLineas) {

	/** Cómo abona el banco la recaudación por código de alumno (decisión 12). */
	public enum AbonoRecaudacion {
		POR_LOTE, POR_PAGO
	}

	public PropiedadesConciliacion {
		if (diasToleranciaFecha < 0 || diasToleranciaFecha > 10) {
			throw new IllegalArgumentException("cuentasclaras.conciliacion.dias-tolerancia-fecha debe estar entre 0 y 10");
		}
		if (toleranciaMontoLiquidacion == null) {
			toleranciaMontoLiquidacion = new BigDecimal("0.00");
		}
		if (toleranciaMontoLiquidacion.signum() < 0 || toleranciaMontoLiquidacion.compareTo(new BigDecimal("10.00")) > 0) {
			throw new IllegalArgumentException("cuentasclaras.conciliacion.tolerancia-monto-liquidacion debe estar entre "
					+ "0.00 y 10.00");
		}
		if (horaLimiteExtracto == null) {
			horaLimiteExtracto = LocalTime.NOON;
		}
		if (intentosConfirmacion < 1 || intentosConfirmacion > 5) {
			throw new IllegalArgumentException("cuentasclaras.conciliacion.intentos-confirmacion debe estar entre 1 y 5");
		}
		if (muestreoDiario < 0 || muestreoDiario > 10 || muestreoConfirmacion < 1 || muestreoConfirmacion > 10) {
			throw new IllegalArgumentException("cuentasclaras.conciliacion.muestreo-* debe estar entre 1 y 10");
		}
		patronAbonoRecaudacion = patronAbonoRecaudacion == null ? "" : patronAbonoRecaudacion.strip();
		if (abonoRecaudacion == null) {
			abonoRecaudacion = AbonoRecaudacion.POR_LOTE;
		}
		if (maximoLineas < 1 || maximoLineas > 20000) {
			throw new IllegalArgumentException("cuentasclaras.conciliacion.maximo-lineas debe estar entre 1 y 20000");
		}
	}

	public static PropiedadesConciliacion porDefecto() {
		return new PropiedadesConciliacion(2, new BigDecimal("0.00"), LocalTime.NOON, 2, 3, 3, "", AbonoRecaudacion.POR_LOTE,
				3000);
	}
}
