package pe.edu.virgenmaria.cuentasclaras.cobranza.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de cobranza ({@link PropiedadesSaldoInicial}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesSaldoInicial.class)
public class ConfiguracionCobranza {
}
