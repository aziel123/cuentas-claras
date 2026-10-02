package pe.edu.virgenmaria.cuentasclaras.comprobantes.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de comprobantes ({@link PropiedadesComprobantes}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesComprobantes.class)
public class ConfiguracionComprobantes {
}
