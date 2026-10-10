package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Las dos conexiones de la aplicación (sprint 7, tanda 2; sección 3.2). Sin AOP ni dependencias nuevas:
 * <ul>
 *   <li>{@code APP}: {@code spring.datasource.*} ({@code DB_USUARIO}, en prod {@code cc_app}) con su
 *       {@code spring.datasource.hikari.*}, como hasta ahora;</li>
 *   <li>{@code SISTEMA}: la misma URL y la misma configuración de Hikari con {@code cuentasclaras.basedatos.sistema.*}
 *       ({@code DB_SISTEMA_USUARIO}, en prod {@code cc_sistema}) y como máximo {@code pool-maximo} conexiones.</li>
 * </ul>
 * Sin {@code cuentasclaras.basedatos.sistema.usuario} (dev y test con H2) las dos rutas son el mismo pool. En prod y
 * piloto {@code VerificadorConfiguracion} exige el segundo usuario y que no sea el mismo de la aplicación.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesBaseDatos.class)
public class ConfiguracionFuentesDatos {

	@Bean
	@Primary
	public FuenteDatosEnrutada dataSource(DataSourceProperties propiedades, Environment entorno,
			PropiedadesBaseDatos baseDatos) {
		HikariDataSource app = propiedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		Binder.get(entorno).bind("spring.datasource.hikari", Bindable.ofInstance(app));
		app.setPoolName("cc-app");
		PropiedadesBaseDatos.Sistema sistema = baseDatos.sistema();
		if (sistema == null || !sistema.configurado()) {
			return new FuenteDatosEnrutada(app, app);
		}
		// Sin arrancar el pool (igual que el de la aplicación): se conecta en la primera transacción.
		HikariDataSource deSistema = new HikariDataSource();
		app.copyStateTo(deSistema);
		deSistema.setUsername(sistema.usuario());
		deSistema.setPassword(sistema.clave());
		deSistema.setPoolName("cc-sistema");
		deSistema.setMaximumPoolSize(Math.max(1, sistema.poolMaximo()));
		if (deSistema.getMinimumIdle() > deSistema.getMaximumPoolSize()) {
			deSistema.setMinimumIdle(deSistema.getMaximumPoolSize());
		}
		return new FuenteDatosEnrutada(app, deSistema);
	}
}
