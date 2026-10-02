package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;

import java.time.Instant;

/**
 * Reemplaza el reloj de la aplicación por uno ajustable: {@code @Import(ConfiguracionRelojAjustable.class)}.
 * Todas las pruebas que lo importan comparten el mismo contexto de Spring.
 */
@TestConfiguration
public class ConfiguracionRelojAjustable {

	/** 2 de octubre de 2026, 08:00 en Lima. */
	public static final Instant INICIO = Instant.parse("2026-10-02T13:00:00Z");

	@Bean
	@Primary
	RelojAjustable relojAjustable() {
		return new RelojAjustable(INICIO, ConfiguracionTiempo.ZONA_LIMA);
	}
}
