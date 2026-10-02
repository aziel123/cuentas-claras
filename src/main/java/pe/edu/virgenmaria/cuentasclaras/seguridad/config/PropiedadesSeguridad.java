package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Bloqueo de cuentas por intentos fallidos ({@code cuentasclaras.seguridad.*}).
 *
 * @param intentosMaximos intentos fallidos seguidos que bloquean la cuenta
 * @param duracionBloqueo cuánto dura el bloqueo; luego la cuenta se desbloquea sola
 */
@ConfigurationProperties("cuentasclaras.seguridad")
public record PropiedadesSeguridad(@DefaultValue("5") int intentosMaximos,
		@DefaultValue("15m") Duration duracionBloqueo) {

	public PropiedadesSeguridad {
		if (intentosMaximos < 1) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.intentos-maximos debe ser al menos 1");
		}
		if (duracionBloqueo == null || duracionBloqueo.isNegative() || duracionBloqueo.isZero()) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.duracion-bloqueo debe ser positiva");
		}
	}
}
