package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Bloqueo de cuentas y claves temporales ({@code cuentasclaras.seguridad.*}).
 *
 * @param intentosMaximos       intentos fallidos seguidos que bloquean la cuenta
 * @param duracionBloqueo       cuánto dura el bloqueo; luego la cuenta se desbloquea sola
 * @param vigenciaClaveTemporal cuánto sirve una clave temporal generada por el sistema
 * @param costoBcrypt           costo de BCrypt de las claves nuevas (sprint 7, tanda 3; A02): de 10 a 14
 */
@ConfigurationProperties("cuentasclaras.seguridad")
public record PropiedadesSeguridad(@DefaultValue("5") int intentosMaximos,
		@DefaultValue("15m") Duration duracionBloqueo, @DefaultValue("48h") Duration vigenciaClaveTemporal,
		@DefaultValue("12") int costoBcrypt) {

	/** Sin el costo de BCrypt (pruebas de servicios): 12. */
	public PropiedadesSeguridad(int intentosMaximos, Duration duracionBloqueo, Duration vigenciaClaveTemporal) {
		this(intentosMaximos, duracionBloqueo, vigenciaClaveTemporal, 12);
	}

	@ConstructorBinding
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
		if (costoBcrypt < 10 || costoBcrypt > 14) {
			throw new IllegalArgumentException("cuentasclaras.seguridad.costo-bcrypt: de 10 a 14");
		}
	}
}
