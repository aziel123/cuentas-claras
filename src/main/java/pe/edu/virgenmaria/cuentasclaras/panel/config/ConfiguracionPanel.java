package pe.edu.virgenmaria.cuentasclaras.panel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración del panel ({@link PropiedadesPanel}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesPanel.class)
public class ConfiguracionPanel {
}
