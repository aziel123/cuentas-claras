package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.LimiteIngresos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorAccesoDenegado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorFalloIngreso;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ManejadorIngresoExitoso;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.FiltroLimiteIngresos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.FiltroSesionMaxima;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Seguridad web (diseño, sección 7; sprint 7, tanda 3: OWASP A01, A02, A05 y A07).
 * <ul>
 *   <li>Permisos: solo los de {@link ModuloApp}; toda otra ruta se niega ({@code denyAll}).</li>
 *   <li>Login propio en {@code /login} con BCrypt (costo configurable); autentica {@code ProveedorAutenticacion}, que
 *       serializa los intentos por usuario (al existir ese bean, Boot ya no genera el usuario {@code user}).</li>
 *   <li>Límite de intentos fallidos por conexión ({@link FiltroLimiteIngresos}, 429) antes del formulario: un tercero no
 *       bloquea la cuenta de otra persona (H9).</li>
 *   <li>Una sesión por usuario: un segundo ingreso expira el primero. Cambio de id de sesión al ingresar. Como máximo
 *       10 horas aunque haya actividad ({@link FiltroSesionMaxima}, decisión 83).</li>
 *   <li>Cierre de sesión solo por POST (con CSRF) en {@code /salir}; borra la cookie con su nombre configurado
 *       ({@code __Host-CCSESION} en producción, decisión 103).</li>
 *   <li>Cabeceras en TODA respuesta (también 302, 403, 404, 429 y 500): CSP sin estilos ni scripts en línea ni iframes,
 *       HSTS de 1 año con subdominios y sin {@code preload} (explícito: no depende de que el proxy marque la petición como
 *       segura; el navegador lo ignora por http), Cross-Origin-Opener-Policy y Cross-Origin-Resource-Policy
 *       {@code same-origin}, X-Frame-Options, nosniff, Referrer-Policy y Permissions-Policy.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({ PropiedadesSeguridad.class, PropiedadesSesion.class })
public class ConfiguracionSeguridad {

	static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
			+ "font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	/** HSTS: 1 año (decisión 103). */
	static final Duration HSTS = Duration.ofDays(365);

	/** Las mismas cabeceras en las dos cadenas (la de los avisos de la pasarela también). */
	static Customizer<HeadersConfigurer<HttpSecurity>> cabeceras() {
		return h -> h
			.contentSecurityPolicy(c -> c.policyDirectives(CSP))
			.frameOptions(fo -> fo.deny())
			.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(HSTS.toSeconds()).includeSubDomains(true)
				.preload(false).requestMatcher(AnyRequestMatcher.INSTANCE))
			.crossOriginOpenerPolicy(c -> c.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
			.crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
			.referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
			.permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()"));
	}

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
			.headers(cabeceras());
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ManejadorIngresoExitoso manejadorIngresoExitoso,
			ManejadorFalloIngreso manejadorFalloIngreso, ManejadorAccesoDenegado manejadorAccesoDenegado,
			SessionRegistry registroSesiones, ServicioCierreSesion cierreSesion, LimiteIngresos limiteIngresos,
			PropiedadesSesion propiedadesSesion, Clock reloj,
			@Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieSesion) throws Exception {
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
				.failureHandler(manejadorFalloIngreso)
				.permitAll())
			// Sprint 7, tanda 3 (H9): antes del formulario, la conexión que superó el límite espera (429) sin autenticar.
			.addFilterBefore(new FiltroLimiteIngresos(limiteIngresos), UsernamePasswordAuthenticationFilter.class)
			// Sprint 7, tanda 3 (decisión 83): 10 horas como máximo desde el ingreso, aunque haya actividad.
			.addFilterAfter(new FiltroSesionMaxima(propiedadesSesion.vigenciaMaxima(), reloj),
				SecurityContextHolderFilter.class)
			// Sprint 7, tanda 2: antes de invalidar la sesión HTTP, se cierra la sesión de la base (su secreto ya no firma).
			.logout(l -> l.logoutUrl("/salir").logoutSuccessUrl("/login?salio").addLogoutHandler(cierreSesion)
				.invalidateHttpSession(true).deleteCookies(cookieSesion))
			.sessionManagement(s -> s
				.sessionFixation(fx -> fx.changeSessionId())
				.sessionConcurrency(c -> c.maximumSessions(1).sessionRegistry(registroSesiones)
					.expiredUrl("/login?expirada")))
			.exceptionHandling(e -> e.accessDeniedHandler(manejadorAccesoDenegado))
			.headers(cabeceras());
		return http.build();
	}

	/**
	 * BCrypt con costo configurable (sprint 7, tanda 3; A02): 12 por defecto (el ingreso tarda unos 250 ms en un servidor
	 * actual; si en el del colegio pasa de 500 ms, se baja a 11). Las claves guardadas con otro costo siguen sirviendo:
	 * el costo va dentro de cada hash.
	 */
	@Bean
	public PasswordEncoder codificadorClaves(PropiedadesSeguridad propiedades) {
		return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(propiedades.costoBcrypt())));
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
