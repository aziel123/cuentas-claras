package pe.edu.virgenmaria.cuentasclaras.familias.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.familias.web.ModoConsultaFamilias;

/** Sprint 11, tanda 1 (sección 3.8): el modo consulta de las familias en un colegio que dejó de operar. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionFamilias implements WebMvcConfigurer {

	private final ObjectProvider<ColegioRepository> colegios;

	public ConfiguracionFamilias(ObjectProvider<ColegioRepository> colegios) {
		this.colegios = colegios;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new ModoConsultaFamilias(colegios));
	}
}
