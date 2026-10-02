package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra los límites de los archivos Excel ({@link PropiedadesExcel}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesExcel.class)
public class ConfiguracionExcel {
}
