package pe.edu.virgenmaria.cuentasclaras.comunicacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link PropiedadesMensajeria}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class ConfiguracionMensajeria {
}
