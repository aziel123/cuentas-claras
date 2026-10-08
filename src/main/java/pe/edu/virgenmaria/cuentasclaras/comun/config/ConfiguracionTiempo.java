package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj único de la aplicación, en la zona horaria del colegio (America/Lima).
 * Toda fecha de negocio o de auditoría se obtiene de este {@link Clock}, nunca de
 * {@code LocalDateTime.now()} sin argumentos, para que las pruebas puedan fijar la hora.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionTiempo {

	/** Nombre de la zona, para anotaciones como {@code @Scheduled(zone = ...)}. */
	public static final String ZONA = "America/Lima";

	public static final ZoneId ZONA_LIMA = ZoneId.of(ZONA);

	@Bean
	public Clock reloj() {
		return Clock.system(ZONA_LIMA);
	}
}
