package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Auditoría JPA de {@code BaseEntity}: quién creó el registro y cuándo se creó o modificó.
 * Va aparte de la clase principal para no interferir con las pruebas de capa web.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorActual", dateTimeProviderRef = "fechaHoraActual")
public class ConfiguracionJpa {

	/** Autor de los procesos sin usuario (arranque, tareas programadas). */
	public static final String AUTOR_SISTEMA = "sistema";

	/** Autor de las acciones de un visitante no identificado. */
	public static final String AUTOR_ANONIMO = "anonimo";

	@Bean
	public AuditorAware<String> auditorActual() {
		return () -> Optional.of(nombreDelAutorActual());
	}

	/** Hora del reloj de la aplicación, truncada a microsegundos (precisión de {@code DATETIME(6)}). */
	@Bean
	public DateTimeProvider fechaHoraActual(Clock reloj) {
		return () -> Optional.of(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
	}

	static String nombreDelAutorActual() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()) {
			return AUTOR_SISTEMA;
		}
		if (autenticacion instanceof AnonymousAuthenticationToken) {
			return AUTOR_ANONIMO;
		}
		return autenticacion.getName();
	}
}
