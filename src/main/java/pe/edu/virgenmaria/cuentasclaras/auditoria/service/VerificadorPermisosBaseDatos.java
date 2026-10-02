package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * En producción, al crearse (antes de que el servidor web acepte peticiones), comprueba:
 * <ul>
 *   <li>que el usuario de la aplicación NO puede editar ni borrar la bitácora: MySQL debe rechazar
 *       {@code UPDATE} y {@code DELETE} sobre {@code evento_auditoria} con el error 1142;</li>
 *   <li>que no faltan migraciones (en producción la aplicación no migra: se corre {@code migrar} antes).</li>
 * </ul>
 * Si algo falla, la aplicación NO arranca. No hay interruptor para saltarse esta comprobación.
 * Las sentencias usan {@code WHERE 1 = 0}: aunque MySQL las permitiera, no tocan ninguna fila.
 * Es la ÚNICA clase autorizada a usar {@link JdbcTemplate} (regla ArchUnit).
 */
@Component
@Profile("prod")
public class VerificadorPermisosBaseDatos implements InitializingBean {

	static final int MYSQL_COMANDO_DENEGADO = 1142;

	static final String[] SENTENCIAS_PROHIBIDAS = {
			"UPDATE evento_auditoria SET ip = ip WHERE 1 = 0",
			"DELETE FROM evento_auditoria WHERE 1 = 0" };

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorPermisosBaseDatos.class);

	private final JdbcTemplate jdbc;

	private final DataSource fuenteDatos;

	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos) {
		this.jdbc = jdbc;
		this.fuenteDatos = fuenteDatos;
	}

	@Override
	public void afterPropertiesSet() {
		verificarMigraciones();
		verificarPermisos();
	}

	public void verificarPermisos() {
		for (String sentencia : SENTENCIAS_PROHIBIDAS) {
			String problema = comprobarDenegada(sentencia);
			if (problema != null) {
				throw new IllegalStateException(problema + " Revisa docs/operacion/mysql-usuarios.md.");
			}
		}
		LOG.info("Permisos de la bitácora verificados: la aplicación no puede editar ni borrar eventos.");
	}

	public void verificarMigraciones() {
		MigrationInfo[] pendientes = Flyway.configure().dataSource(fuenteDatos).locations("classpath:db/migration")
				.load().info().pending();
		if (pendientes.length > 0) {
			throw new IllegalStateException("Faltan migraciones de la base: "
					+ Arrays.stream(pendientes).map(m -> "V" + m.getVersion()).collect(Collectors.joining(", "))
					+ ". Antes de arrancar ejecuta «java -jar cuentas-claras.jar migrar» con el usuario cc_migrador.");
		}
	}

	/** @return {@code null} si la base la rechazó con 1142; si no, la descripción del problema */
	private String comprobarDenegada(String sentencia) {
		try {
			jdbc.update(sentencia);
			return "El usuario de la aplicación PUEDE ejecutar «" + sentencia + "»: la bitácora no está protegida en la base.";
		}
		catch (DataAccessException e) {
			Integer codigo = codigoMySql(e);
			if (codigo != null && codigo == MYSQL_COMANDO_DENEGADO) {
				return null;
			}
			return "No se pudo comprobar «" + sentencia + "» (código " + codigo + "): " + e.getMostSpecificCause().getMessage();
		}
	}

	private static Integer codigoMySql(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		return null;
	}
}
