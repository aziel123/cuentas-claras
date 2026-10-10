package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pe.edu.virgenmaria.cuentasclaras.comun.marca.Marca;

/**
 * Registra {@link PropiedadesEntorno} (la franja global del piloto), {@link Marca} (sprint 8: el nombre comercial en un
 * solo parámetro) y {@link PropiedadesPlataforma} (sprint 8: instalación y operadores).
 */
@Configuration
@EnableConfigurationProperties({ PropiedadesEntorno.class, Marca.class, PropiedadesPlataforma.class })
public class ConfiguracionEntorno {
}
