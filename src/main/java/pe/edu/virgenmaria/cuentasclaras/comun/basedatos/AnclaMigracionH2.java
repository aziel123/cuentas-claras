package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bug P0 del sprint 9 (doble ingreso con 500 y «La base de datos ha sido cerrada»): en H2, un CHECK con
 * {@code col IN ('A', 'B', ...)} se compila UNA vez, al crearlo, a un {@code ConditionInConstantSet} cuyo
 * {@code TreeSet} compara con la SESIÓN que ejecutó la migración. Si esa conexión se cierra (Hikari la retira a los 30
 * minutos, el reinicio de devtools cierra el pool o la prueba evicta las conexiones), toda fila que evalúa ese CHECK
 * falla con 23514 envuelto en 90098 y no se puede escribir en ninguna tabla con esos CHECK (por ejemplo
 * {@code ck_sesion_usuario_cierre} al cerrar la sesión anterior con OTRA_SESION). La base sigue viva, pero inservible.
 * <p>
 * Solo para H2 local (dev, demo y pruebas): Flyway migra por una conexión propia que nunca se cierra mientras viva la
 * JVM (la base en memoria con {@code DB_CLOSE_DELAY=-1} también vive hasta entonces, incluso entre reinicios de
 * devtools). Se guarda por URL en un estático y no es un bean: cerrar el contexto no la cierra. MySQL evalúa sus CHECK
 * sin estado de sesión: en producción no aplica y esta clase no cambia nada.
 * <p>
 * También para la demo persistente (H2 en archivo, perfil {@code dev,persistente}): en el arranque que migra (el primero,
 * o el primero después de traer una migración nueva) los CHECK nuevos quedan atados a la conexión de Flyway; si es del
 * pool, cuando Hikari la retira con la base abierta falla igual. Con el ancla, además, la base de archivo queda abierta
 * mientras viva la JVM. Al abrir una base de archivo ya migrada, H2 compila sus CHECK con su sesión de sistema, que vive
 * lo que la base: ese caso no necesitaba el ancla.
 */
@Component
public class AnclaMigracionH2 implements FlywayConfigurationCustomizer {

	private static final Map<String, SingleConnectionDataSource> ANCLAS = new ConcurrentHashMap<>();

	private final DataSourceProperties propiedades;

	public AnclaMigracionH2(DataSourceProperties propiedades) {
		this.propiedades = propiedades;
	}

	@Override
	public void customize(org.flywaydb.core.api.configuration.FluentConfiguration configuracion) {
		String url = propiedades.determineUrl();
		if (!anclada(url)) {
			return;
		}
		configuracion.dataSource(ANCLAS.computeIfAbsent(url, u -> new SingleConnectionDataSource(u,
				propiedades.determineUsername(), propiedades.determinePassword(), true)));
	}

	/** Si esa URL lleva el ancla: H2 en memoria o en archivo (nunca MySQL). */
	static boolean anclada(String url) {
		return BaseH2Local.es(url);
	}
}
