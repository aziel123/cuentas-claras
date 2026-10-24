package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Sprint 11, tanda 1: el camino documentado del DBA en las pruebas de MySQL real (la conexión de {@code cc_migrador}, que
 * los triggers no frenan: no es cc_app ni cc_sistema). Lo usan las pruebas para simular fechas (mover las clases de un año
 * o la fecha efectiva de un cambio de estado), nunca la aplicación. Sin la clave en el entorno, devuelve {@code null}.
 */
public final class DbaMySql {

	private DbaMySql() {
	}

	public static JdbcTemplate migrador() {
		String clave = System.getenv("CC_MYSQL_CLAVE_MIGRADOR");
		if (clave == null || clave.isBlank()) {
			return null;
		}
		return new JdbcTemplate(new DriverManagerDataSource(System.getenv().getOrDefault("CC_MYSQL_URL",
				"jdbc:mysql://127.0.0.1:3306/cuentasclaras?allowPublicKeyRetrieval=true&useSSL=false"), "cc_migrador", clave));
	}

	/** Como el DBA; si no hay su clave, falla con un mensaje claro. */
	public static JdbcTemplate exigir() {
		JdbcTemplate dba = migrador();
		if (dba == null) {
			throw new IllegalStateException("Falta CC_MYSQL_CLAVE_MIGRADOR (la conexión del DBA de las pruebas de MySQL)");
		}
		return dba;
	}
}
