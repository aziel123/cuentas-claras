package pe.edu.virgenmaria.cuentasclaras.operacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContextoPeticionLog;

/** Sprint 7, tanda 1: propiedades del monitoreo y el contexto de cada petición en los logs (colegio, usuario y ruta). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesMonitoreo.class)
public class ConfiguracionOperacion implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new ContextoPeticionLog());
	}
}
