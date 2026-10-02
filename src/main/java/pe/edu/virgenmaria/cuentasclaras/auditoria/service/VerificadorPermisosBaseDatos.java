package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * En producción comprueba al arrancar que el usuario de la aplicación NO puede editar ni borrar la
 * bitácora: MySQL debe rechazar {@code UPDATE} y {@code DELETE} sobre {@code evento_auditoria} con el
 * error 1142 (comando denegado). Si los permite, la aplicación no arranca
 * (ver {@code docs/operacion/mysql-usuarios.md}).
 * <p>
 * Las sentencias usan {@code WHERE 1 = 0}: aunque MySQL las permitiera, no tocan ninguna fila.
 * Es la ÚNICA clase autorizada a usar {@link JdbcTemplate} (regla ArchUnit).
 */
@Component
@Profile("prod")
@Order(0)
public class VerificadorPermisosBaseDatos implements ApplicationRunner {

	static final int MYSQL_COMANDO_DENEGADO = 1142;

	static final String[] SENTENCIAS_PROHIBIDAS = {
			"UPDATE evento_auditoria SET ip = ip WHERE 1 = 0",
			"DELETE FROM evento_auditoria WHERE 1 = 0" };

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorPermisosBaseDatos.class);

	private final JdbcTemplate jdbc;

	private final boolean exigir;

	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc,
			@Value("${cuentasclaras.auditoria.exigir-permisos-restringidos:true}") boolean exigir) {
		this.jdbc = jdbc;
		this.exigir = exigir;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		verificar();
	}

	public void verificar() {
		for (String sentencia : SENTENCIAS_PROHIBIDAS) {
			String problema = comprobarDenegada(sentencia);
			if (problema == null) {
				continue;
			}
			if (exigir) {
				throw new IllegalStateException(problema + " Revisa docs/operacion/mysql-usuarios.md.");
			}
			LOG.warn("{} (no se exige porque cuentasclaras.auditoria.exigir-permisos-restringidos=false)", problema);
		}
		LOG.info("Permisos de la bitácora verificados: la aplicación no puede editar ni borrar eventos.");
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
