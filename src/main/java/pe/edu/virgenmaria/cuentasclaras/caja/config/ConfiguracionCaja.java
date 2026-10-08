package pe.edu.virgenmaria.cuentasclaras.caja.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de caja ({@link PropiedadesCaja}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesCaja.class)
public class ConfiguracionCaja {
}
