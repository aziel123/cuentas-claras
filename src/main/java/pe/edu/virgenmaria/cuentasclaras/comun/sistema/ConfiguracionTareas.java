package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Tareas programadas (una sola instancia de la aplicación): el outbox del OSE, la consulta de órdenes abiertas y la
 * reconsulta nocturna. Con {@code cuentasclaras.tareas.activas: false} (perfil de pruebas) no se programan: las pruebas
 * llaman a los métodos directamente. Dos hilos: una tarea lenta no frena a las demás.
 * <p>
 * Sprint 7 (monitoreo): cada ejecución se observa y, si termina sin error, deja su latido en {@link Latidos} (lo lee el
 * estado técnico para dar un proceso por atrasado). El registro de observaciones es propio: no publica métricas.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "cuentasclaras.tareas.activas", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTareas implements SchedulingConfigurer {

	private final Latidos latidos;

	public ConfiguracionTareas(Latidos latidos) {
		this.latidos = latidos;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registro) {
		ObservationRegistry observaciones = ObservationRegistry.create();
		observaciones.observationConfig().observationHandler(new ObservadorLatidos(latidos));
		registro.setObservationRegistry(observaciones);
	}

	@Bean
	public ThreadPoolTaskScheduler programadorTareas() {
		ThreadPoolTaskScheduler programador = new ThreadPoolTaskScheduler();
		programador.setPoolSize(2);
		programador.setThreadNamePrefix("tareas-");
		programador.setDaemon(true);
		return programador;
	}
}
