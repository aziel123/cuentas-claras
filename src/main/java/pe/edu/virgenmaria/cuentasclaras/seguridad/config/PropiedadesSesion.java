package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Sesiones e ingresos ({@code cuentasclaras.sesion.*}; sprint 7, tandas 2 y 3).
 *
 * @param vigenciaMaxima        cuánto dura una sesión aunque haya actividad (decisión 83: 10 h). Pasado ese tiempo la
 *                              sesión HTTP se cierra y el secreto con el que se firman las aprobaciones deja de servir. En
 *                              MySQL, trg_sesion_usuario_nace acepta 12 horas como máximo.
 * @param intentosPorIp         intentos fallidos de ingreso desde una misma conexión (cualquier usuario) tras los que esa
 *                              conexión espera (decisión 104: 20)
 * @param intentosPorCuentaEIp  intentos fallidos con un mismo usuario desde una misma conexión tras los que esa conexión
 *                              espera para ese usuario (H9: 5). Así un tercero no bloquea la cuenta de otra persona: sus
 *                              intentos no llegan al contador de la cuenta.
 * @param ventanaIntentosIp     ventana en la que se cuentan esos intentos y cuánto espera la conexión (15 min)
 */
@ConfigurationProperties("cuentasclaras.sesion")
public record PropiedadesSesion(@DefaultValue("10h") Duration vigenciaMaxima, @DefaultValue("20") int intentosPorIp,
		@DefaultValue("5") int intentosPorCuentaEIp, @DefaultValue("15m") Duration ventanaIntentosIp) {

	@ConstructorBinding
	public PropiedadesSesion {
		if (vigenciaMaxima == null || vigenciaMaxima.isNegative() || vigenciaMaxima.isZero()
				|| vigenciaMaxima.compareTo(Duration.ofHours(12)) > 0) {
			throw new IllegalArgumentException("cuentasclaras.sesion.vigencia-maxima debe ser positiva y de 12 h como máximo");
		}
		if (intentosPorIp < 1 || intentosPorCuentaEIp < 1) {
			throw new IllegalArgumentException("cuentasclaras.sesion.intentos-por-ip e intentos-por-cuenta-e-ip: al menos 1");
		}
		if (ventanaIntentosIp == null || ventanaIntentosIp.isNegative() || ventanaIntentosIp.isZero()) {
			throw new IllegalArgumentException("cuentasclaras.sesion.ventana-intentos-ip debe ser positiva");
		}
	}

	/** Solo la vigencia (las pruebas de sesiones): los límites de ingreso con sus valores por defecto. */
	public PropiedadesSesion(Duration vigenciaMaxima) {
		this(vigenciaMaxima, 20, 5, Duration.ofMinutes(15));
	}
}
