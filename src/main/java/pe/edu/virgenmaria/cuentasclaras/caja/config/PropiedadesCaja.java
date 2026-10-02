package pe.edu.virgenmaria.cuentasclaras.caja.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * {@code cuentasclaras.caja}: decisiones del colegio para la caja (sección 16 del diseño). Por ahora es global; con un
 * segundo colegio pasa a una tabla de configuración por colegio. Un valor inválido impide arrancar.
 *
 * @param fondoFijo sencillo que Administración entrega a cada cajero (entra al esperado del cierre)
 * @param horaLimiteCierre después de esta hora, «Caja sin cerrar» en Para revisar (tanda 3)
 * @param permitirPagoACuenta pagos parciales: desactivados en el piloto
 * @param pagoACuentaMinimo mínimo de un pago a cuenta, si se habilita
 * @param cuentasDeposito cuenta donde se deposita el efectivo (tanda 3)
 * @param diasSinVerificar pagos digitales o depósitos sin verificar: alerta (tanda 3)
 */
@ConfigurationProperties("cuentasclaras.caja")
public record PropiedadesCaja(
		@DefaultValue("0.00") BigDecimal fondoFijo,
		@DefaultValue("19:00") LocalTime horaLimiteCierre,
		@DefaultValue("false") boolean permitirPagoACuenta,
		@DefaultValue("50.00") BigDecimal pagoACuentaMinimo,
		@DefaultValue("") String cuentasDeposito,
		@DefaultValue("1") int diasSinVerificar) {

	public PropiedadesCaja {
		if (fondoFijo == null || fondoFijo.signum() < 0 || !Dinero.enDecimos(fondoFijo)) {
			throw new IllegalArgumentException("cuentasclaras.caja.fondo-fijo: 0 o más, múltiplo de S/ 0.10");
		}
		fondoFijo = Dinero.normalizar(fondoFijo);
		if (pagoACuentaMinimo == null || pagoACuentaMinimo.signum() <= 0 || !Dinero.enDecimos(pagoACuentaMinimo)) {
			throw new IllegalArgumentException("cuentasclaras.caja.pago-a-cuenta-minimo: mayor que 0, múltiplo de S/ 0.10");
		}
		pagoACuentaMinimo = Dinero.normalizar(pagoACuentaMinimo);
		if (diasSinVerificar < 0) {
			throw new IllegalArgumentException("cuentasclaras.caja.dias-sin-verificar: 0 o más");
		}
	}
}
