package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad MÍNIMA Y TEMPORAL del sprint 1, tanda 1.
 * <p>
 * Todo exige autenticación salvo los recursos estáticos y {@code /actuator/health}. Usa el
 * formulario de login por defecto de Spring Security y el usuario que Boot genera al arrancar.
 * <p>
 * TODO(sprint1-tanda2): reemplazar por la configuración del diseño (sección 7): matriz
 * {@code ModuloApp} con {@code anyRequest().denyAll()}, formulario propio en {@code /login},
 * {@code ServicioDetallesUsuario} con BCrypt (y eliminar el usuario generado por Boot),
 * bloqueo de cuenta, una sesión por usuario, cookie {@code CCSESION} y cabeceras CSP.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class ConfiguracionSeguridad {

	@Bean
	public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
				.requestMatchers("/actuator/health").permitAll()
				.anyRequest().authenticated())
			.formLogin(Customizer.withDefaults());
		return http.build();
	}
}
