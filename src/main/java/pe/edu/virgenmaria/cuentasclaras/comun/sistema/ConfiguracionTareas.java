package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Tareas programadas (una sola instancia de la aplicación): el outbox del OSE, la consulta de órdenes abiertas y la
 * reconsulta nocturna. Con {@code cuentasclaras.tareas.activas: false} (perfil de pruebas) no se programan: las pruebas
 * llaman a los métodos directamente. Dos hilos: una tarea lenta no frena a las demás.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "cuentasclaras.tareas.activas", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTareas {

	@Bean
	public ThreadPoolTaskScheduler programadorTareas() {
		ThreadPoolTaskScheduler programador = new ThreadPoolTaskScheduler();
		programador.setPoolSize(2);
		programador.setThreadNamePrefix("tareas-");
		programador.setDaemon(true);
		return programador;
	}
}
