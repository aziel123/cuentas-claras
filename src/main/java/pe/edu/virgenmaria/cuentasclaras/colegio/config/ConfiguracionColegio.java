package pe.edu.virgenmaria.cuentasclaras.colegio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa {@link PropiedadesColegio} ({@code cuentasclaras.colegio.*}). */
@Configuration
@EnableConfigurationProperties(PropiedadesColegio.class)
public class ConfiguracionColegio {
}
