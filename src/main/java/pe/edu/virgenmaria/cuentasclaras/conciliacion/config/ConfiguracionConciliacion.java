package pe.edu.virgenmaria.cuentasclaras.conciliacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de la conciliación automática ({@link PropiedadesConciliacion}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesConciliacion.class)
public class ConfiguracionConciliacion {
}
