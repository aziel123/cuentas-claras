package pe.edu.virgenmaria.cuentasclaras.operacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code cuentasclaras.monitoreo} (sprint 7, tanda 1). Las horas de las alertas técnicas van como cron en
 * {@code cuentasclaras.monitoreo.cada} y {@code resumen-tecnico} (las lee {@code @Scheduled}).
 *
 * @param operadorCorreo          a quién llegan las alertas técnicas por correo (CC_OPERADOR_CORREO); vacío = apagadas
 * @param respaldoExigido         si la falta de respaldo es alerta (prod y piloto: sí; dev y pruebas: no)
 * @param aceptarRespaldoSimulado si un respaldo al destino «simulado» cuenta como respaldo (en prod: nunca)
 * @param respaldoMaxHoras        sin un respaldo más reciente, «Sin respaldo» (decisión 88: diario, RPO 24 h)
 * @param erroresUmbral           ERROR de una misma huella en la ventana que disparan la alerta
 * @param erroresVentana          ventana del umbral de errores
 * @param discoMinimoPorcentaje   espacio libre mínimo del disco del servidor
 * @param gracia                  tras un arranque, cuánto se espera antes de dar un proceso por atrasado
 * @param repetirCada             una misma alerta (tipo y huella) sale como máximo una vez en este tiempo
 */
@ConfigurationProperties("cuentasclaras.monitoreo")
public record PropiedadesMonitoreo(
		@DefaultValue("") String operadorCorreo,
		@DefaultValue("false") boolean respaldoExigido,
		@DefaultValue("true") boolean aceptarRespaldoSimulado,
		@DefaultValue("26") int respaldoMaxHoras,
		@DefaultValue("5") int erroresUmbral,
		@DefaultValue("15m") Duration erroresVentana,
		@DefaultValue("15") int discoMinimoPorcentaje,
		@DefaultValue("10m") Duration gracia,
		@DefaultValue("1h") Duration repetirCada) {

	public PropiedadesMonitoreo {
		operadorCorreo = operadorCorreo == null ? "" : operadorCorreo.strip();
		if (respaldoMaxHoras < 1) {
			throw new IllegalArgumentException("cuentasclaras.monitoreo.respaldo-max-horas: 1 o más");
		}
		if (erroresUmbral < 1) {
			throw new IllegalArgumentException("cuentasclaras.monitoreo.errores-umbral: 1 o más");
		}
		if (discoMinimoPorcentaje < 0 || discoMinimoPorcentaje > 100) {
			throw new IllegalArgumentException("cuentasclaras.monitoreo.disco-minimo-porcentaje: de 0 a 100");
		}
	}
}
