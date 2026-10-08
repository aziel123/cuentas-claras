package pe.edu.virgenmaria.cuentasclaras.recaudacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de la recaudación bancaria ({@link PropiedadesRecaudacion}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesRecaudacion.class)
public class ConfiguracionRecaudacion {
}
