package pe.edu.virgenmaria.cuentasclaras.comun.migracion;

import ch.qos.logback.classic.Level;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modo de migración: {@code java -jar cuentas-claras.jar migrar}. Aplica las migraciones Flyway con el
 * usuario {@code cc_migrador} y TERMINA, sin levantar Spring ni el servidor web.
 * <p>
 * Así la aplicación nunca recibe las credenciales del migrador (que puede cambiar o borrar tablas, incluida
 * la bitácora): en producción la aplicación corre con {@code cc_app} y Flyway desactivado.
 * Variables: {@code DB_URL}, {@code DB_MIGRADOR_USUARIO}, {@code DB_MIGRADOR_CLAVE} y, opcional,
 * {@code DB_MIGRAR_HASTA} (sprint 7: la restauración de un respaldo migra solo hasta la versión del esquema que dice
 * su manifiesto, aunque el jar traiga migraciones más nuevas).
 */
public final class MigradorBaseDatos {

	public static final String ARGUMENTO = "migrar";

	static final String UBICACION = "classpath:db/migration";

	static final String HASTA = "DB_MIGRAR_HASTA";

	private static final Logger LOG = LoggerFactory.getLogger(MigradorBaseDatos.class);

	private MigradorBaseDatos() {
	}

	/** @return el código de salida del proceso: 0 si todo salió bien */
	public static int ejecutarDesdeEntorno(Map<String, String> entorno) {
		silenciarDepuracion();
		List<String> faltantes = new ArrayList<>();
		for (String variable : List.of("DB_URL", "DB_MIGRADOR_USUARIO", "DB_MIGRADOR_CLAVE")) {
			if (entorno.get(variable) == null || entorno.get(variable).isBlank()) {
				faltantes.add(variable);
			}
		}
		if (!faltantes.isEmpty()) {
			LOG.error("No se puede migrar: faltan las variables {}", String.join(", ", faltantes));
			return 2;
		}
		String hasta = entorno.get(HASTA);
		if (hasta != null && !hasta.isBlank() && !hasta.strip().matches("[0-9]+")) {
			LOG.error("No se puede migrar: {} debe ser el número de una versión (por ejemplo 24) y es «{}».", HASTA,
					hasta);
			return 2;
		}
		try {
			MigrateResult resultado = migrar(entorno.get("DB_URL"), entorno.get("DB_MIGRADOR_USUARIO"),
					entorno.get("DB_MIGRADOR_CLAVE"), hasta == null || hasta.isBlank() ? null : hasta.strip());
			LOG.info("Migración terminada: {} migraciones aplicadas; versión actual {}.", resultado.migrationsExecuted,
					resultado.targetSchemaVersion == null ? "sin cambios" : resultado.targetSchemaVersion);
			return 0;
		}
		catch (RuntimeException e) {
			LOG.error("La migración falló: {}", e.getMessage());
			return 1;
		}
	}

	public static MigrateResult migrar(String url, String usuario, String clave) {
		return migrar(url, usuario, clave, null);
	}

	/** @param hasta última versión a aplicar; {@code null} para todas */
	public static MigrateResult migrar(String url, String usuario, String clave, String hasta) {
		var configuracion = Flyway.configure()
				.dataSource(url, usuario, clave)
				.locations(UBICACION)
				.validateOnMigrate(true);
		if (hasta != null) {
			configuracion.target(hasta);
		}
		return configuracion.load().migrate();
	}

	/** Sin la configuración de Spring Boot, Logback registra en modo depuración: lo dejamos en INFO. */
	static void silenciarDepuracion() {
		if (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger raiz) {
			raiz.setLevel(Level.INFO);
		}
	}
}
