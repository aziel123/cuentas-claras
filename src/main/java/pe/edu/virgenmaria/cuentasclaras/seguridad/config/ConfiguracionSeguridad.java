package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.CredentialsExpiredException;
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
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioCierreSesion;

import java.util.Map;

/**
 * Seguridad web (diseño, sección 7).
 * <ul>
 *   <li>Permisos: solo los de {@link ModuloApp}; toda otra ruta se niega ({@code denyAll}).</li>
 *   <li>Login propio en {@code /login} con BCrypt; autentica {@code ProveedorAutenticacion}, que serializa los
 *       intentos por usuario (al existir ese bean, Boot ya no genera el usuario {@code user}).</li>
 *   <li>Una sesión por usuario: un segundo ingreso expira el primero. Cambio de id de sesión al ingresar.</li>
 *   <li>Cierre de sesión solo por POST (con CSRF) en {@code /salir}.</li>
 *   <li>Cabeceras: CSP sin estilos ni scripts en línea, sin iframes, Referrer-Policy y Permissions-Policy.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({ PropiedadesSeguridad.class, PropiedadesSesion.class })
public class ConfiguracionSeguridad {

	static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
			+ "font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	/**
	 * Sprint 4: cadena aparte para los avisos de la pasarela ({@link ModuloApp#RUTAS_WEBHOOK}). Sin sesión
	 * ({@code STATELESS}), sin formulario de ingreso y sin CSRF SOLO aquí; solo {@code POST} a la ruta del aviso y todo lo
	 * demás se niega. Las mismas cabeceras. La autenticación del aviso (firma) la hace la aplicación.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain cadenaDeWebhooks(HttpSecurity http) throws Exception {
		http
			.securityMatcher(ModuloApp.RUTAS_WEBHOOK)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, ModuloApp.RUTA_WEBHOOK_PASARELA).permitAll()
				.requestMatchers(HttpMethod.POST, ModuloApp.RUTA_WEBHOOK_WHATSAPP).permitAll()
				.requestMatchers(HttpMethod.GET, ModuloApp.RUTA_WEBHOOK_WHATSAPP).permitAll()
				.anyRequest().denyAll())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(c -> c.disable())
			.requestCache(c -> c.disable())
			.securityContext(c -> c.requireExplicitSave(true))
			.headers(h -> h
				.contentSecurityPolicy(c -> c.policyDirectives(CSP))
				.frameOptions(fo -> fo.deny())
				.referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")));
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ManejadorIngresoExitoso manejadorIngresoExitoso,
			ManejadorAccesoDenegado manejadorAccesoDenegado, SessionRegistry registroSesiones,
			ServicioCierreSesion cierreSesion) throws Exception {
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
			// Sprint 7, tanda 2: antes de invalidar la sesión HTTP, se cierra la sesión de la base (su secreto ya no firma).
			.logout(l -> l.logoutUrl("/salir").logoutSuccessUrl("/login?salio").addLogoutHandler(cierreSesion)
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

	/**
	 * Clave incorrecta, usuario inexistente, cuenta desactivada o BLOQUEADA: el mismo mensaje genérico, para no
	 * revelar qué usuarios existen ni cuáles están bloqueados. La clave temporal vencida tiene su aviso: solo se
	 * llega ahí con la clave correcta.
	 */
	private static ExceptionMappingAuthenticationFailureHandler manejadorFallo() {
		ExceptionMappingAuthenticationFailureHandler fallo = new ExceptionMappingAuthenticationFailureHandler();
		fallo.setDefaultFailureUrl("/login?error");
		fallo.setExceptionMappings(Map.of(CredentialsExpiredException.class.getName(), "/login?vencida"));
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
