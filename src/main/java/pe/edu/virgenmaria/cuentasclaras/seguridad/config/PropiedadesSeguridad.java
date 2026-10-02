package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Bloqueo de cuentas y claves temporales ({@code cuentasclaras.seguridad.*}).
 *
 * @param intentosMaximos       intentos fallidos seguidos que bloquean la cuenta
 * @param duracionBloqueo       cuánto dura el bloqueo; luego la cuenta se desbloquea sola
 * @param vigenciaClaveTemporal cuánto sirve una clave temporal generada por el sistema
 */
@ConfigurationProperties("cuentasclaras.seguridad")
public record PropiedadesSeguridad(@DefaultValue("5") int intentosMaximos,
		@DefaultValue("15m") Duration duracionBloqueo, @DefaultValue("48h") Duration vigenciaClaveTemporal) {

	public PropiedadesSeguridad {
		if (intentosMaximos < 1) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.intentos-maximos debe ser al menos 1");
		}
		if (duracionBloqueo == null || duracionBloqueo.isNegative() || duracionBloqueo.isZero()) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.duracion-bloqueo debe ser positiva");
		}
		if (vigenciaClaveTemporal == null || vigenciaClaveTemporal.isNegative() || vigenciaClaveTemporal.isZero()) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.vigencia-clave-temporal debe ser positiva");
		}
	}
}
