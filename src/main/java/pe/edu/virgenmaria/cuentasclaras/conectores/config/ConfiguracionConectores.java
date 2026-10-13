package pe.edu.virgenmaria.cuentasclaras.conectores.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa {@link PropiedadesConectores} ({@code cuentasclaras.conectores.*}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesConectores.class)
public class ConfiguracionConectores {
}
