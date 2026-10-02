package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorAccesoDenegado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorIngresoExitoso;

import java.util.Map;

/**
 * Seguridad web (diseño, sección 7).
 * <ul>
 *   <li>Permisos: solo los de {@link ModuloApp}; toda otra ruta se niega ({@code denyAll}).</li>
 *   <li>Login propio en {@code /login} con BCrypt; los usuarios los carga {@code ServicioDetallesUsuario}
 *       (al existir ese bean, Boot ya no genera el usuario {@code user}).</li>
 *   <li>Una sesión por usuario: un segundo ingreso expira el primero. Cambio de id de sesión al ingresar.</li>
 *   <li>Cierre de sesión solo por POST (con CSRF) en {@code /salir}.</li>
 *   <li>Cabeceras: CSP sin estilos ni scripts en línea, sin iframes, Referrer-Policy y Permissions-Policy.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class ConfiguracionSeguridad {

	static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
			+ "font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	@Bean
	public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ManejadorIngresoExitoso manejadorIngresoExitoso,
			ManejadorAccesoDenegado manejadorAccesoDenegado, SessionRegistry registroSesiones) throws Exception {
		http
			.authorizeHttpRequests(auth -> {
				auth.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
					.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
					.requestMatchers(ModuloApp.RUTAS_PUBLICAS).permitAll()
					.requestMatchers(ModuloApp.RUTA_CAMBIAR_CLAVE).authenticated();
				for (ModuloApp modulo : ModuloApp.values()) {
					auth.requestMatchers(modulo.patrones()).hasAnyRole(modulo.nombresDeRoles());
				}
				auth.anyRequest().denyAll();
			})
			.formLogin(f -> f.loginPage("/login").loginProcessingUrl("/login")
				.usernameParameter("usuario").passwordParameter("clave")
				.successHandler(manejadorIngresoExitoso)
				.failureHandler(manejadorFallo())
				.permitAll())
			.logout(l -> l.logoutUrl("/salir").logoutSuccessUrl("/login?salio")
				.invalidateHttpSession(true).deleteCookies("CCSESION"))
			.sessionManagement(s -> s
				.sessionFixation(fx -> fx.changeSessionId())
				.sessionConcurrency(c -> c.maximumSessions(1).sessionRegistry(registroSesiones)
					.expiredUrl("/login?expirada")))
			.exceptionHandling(e -> e.accessDeniedHandler(manejadorAccesoDenegado))
			.headers(h -> h
				.contentSecurityPolicy(c -> c.policyDirectives(CSP))
				.frameOptions(fo -> fo.deny())
				.referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")));
		return http.build();
	}

	/** Clave incorrecta, usuario inexistente o desactivado: el mismo mensaje. Cuenta bloqueada: aviso propio. */
	private static ExceptionMappingAuthenticationFailureHandler manejadorFallo() {
		ExceptionMappingAuthenticationFailureHandler fallo = new ExceptionMappingAuthenticationFailureHandler();
		fallo.setDefaultFailureUrl("/login?error");
		fallo.setExceptionMappings(Map.of(LockedException.class.getName(), "/login?bloqueada"));
		return fallo;
	}

	@Bean
	public PasswordEncoder codificadorClaves() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	public SessionRegistry registroSesiones() {
		return new SessionRegistryImpl();
	}

	/** Mantiene al día el registro de sesiones cuando una sesión expira o cambia de id. */
	@Bean
	public HttpSessionEventPublisher publicadorEventosSesion() {
		return new HttpSessionEventPublisher();
	}
}
