package pe.edu.virgenmaria.cuentasclaras.privacidad.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AccesosDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.privacidad.web.RegistroAccesosInterceptor;

/** Sprint 7, tanda 3: el registro de accesos a datos personales en toda pantalla marcada con {@code @RegistraAcceso}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesPrivacidad.class)
public class ConfiguracionPrivacidad implements WebMvcConfigurer {

	private final AccesosDatosPersonales accesos;

	public ConfiguracionPrivacidad(AccesosDatosPersonales accesos) {
		this.accesos = accesos;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new RegistroAccesosInterceptor(accesos));
	}
}
