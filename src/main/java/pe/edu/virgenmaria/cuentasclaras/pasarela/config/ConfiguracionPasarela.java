package pe.edu.virgenmaria.cuentasclaras.pasarela.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de pagos en línea ({@link PropiedadesPasarela}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesPasarela.class)
public class ConfiguracionPasarela {
}
