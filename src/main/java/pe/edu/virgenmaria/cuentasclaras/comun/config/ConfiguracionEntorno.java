package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link PropiedadesEntorno} (la franja global del piloto). */
@Configuration
@EnableConfigurationProperties(PropiedadesEntorno.class)
public class ConfiguracionEntorno {
}
