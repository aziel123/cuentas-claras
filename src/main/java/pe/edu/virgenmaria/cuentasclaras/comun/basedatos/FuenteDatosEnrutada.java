package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

/**
 * La fuente de datos de la aplicación (sprint 7, tanda 2; sección 3.2): dos pools, {@link RutaConexion#APP}
 * ({@code cc_app}) y {@link RutaConexion#SISTEMA} ({@code cc_sistema}), elegidos con {@link RutaConexion#actual()} al
 * pedir la conexión. En dev y test (H2) las dos rutas son el MISMO pool: {@link #separadas()} es {@code false}.
 */
public class FuenteDatosEnrutada extends AbstractRoutingDataSource implements DisposableBean {

	private final DataSource app;

	private final DataSource sistema;

	public FuenteDatosEnrutada(DataSource app, DataSource sistema) {
		this.app = Objects.requireNonNull(app, "app");
		this.sistema = Objects.requireNonNull(sistema, "sistema");
		setTargetDataSources(Map.of(RutaConexion.APP, app, RutaConexion.SISTEMA, sistema));
		setDefaultTargetDataSource(app);
		setLenientFallback(false);
		afterPropertiesSet();
	}

	@Override
	protected Object determineCurrentLookupKey() {
		return RutaConexion.actual();
	}

	/** El pool de las personas ({@code cc_app}). */
	public DataSource app() {
		return app;
	}

	/** El pool de los procesos y la identidad ({@code cc_sistema}); en dev y test, el mismo que {@link #app()}. */
	public DataSource sistema() {
		return sistema;
	}

	/** Si las dos rutas usan usuarios de base distintos (MySQL de prod, piloto y las pruebas de MySQL real). */
	public boolean separadas() {
		return app != sistema;
	}

	@Override
	public void destroy() throws Exception {
		cerrar(sistema);
		if (separadas()) {
			cerrar(app);
		}
	}

	private static void cerrar(DataSource fuente) throws Exception {
		if (fuente instanceof AutoCloseable cerrable) {
			cerrable.close();
		}
	}
}
