package pe.edu.virgenmaria.cuentasclaras.comun.migracion;

import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El modo «migrar» aplica las migraciones sin levantar la aplicación y es repetible.
 */
class MigradorBaseDatosTest {

	private final String url = "jdbc:h2:mem:migrador-" + UUID.randomUUID()
			+ ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

	@Test
	void aplicaTodasLasMigracionesYEsRepetible() throws Exception {
		MigrateResult primera = MigradorBaseDatos.migrar(url, "sa", "");
		MigrateResult segunda = MigradorBaseDatos.migrar(url, "sa", "");

		assertThat(primera.migrationsExecuted).isEqualTo(19);
		assertThat(segunda.migrationsExecuted).isZero();
		try (var conexion = DriverManager.getConnection(url, "sa", "");
				var consulta = conexion.createStatement().executeQuery("SELECT COUNT(*) FROM evento_auditoria")) {
			assertThat(consulta.next()).isTrue();
		}
	}

	@Test
	void desdeElEntornoExigeLasVariablesDelMigrador() {
		assertThat(MigradorBaseDatos.ejecutarDesdeEntorno(Map.of("DB_URL", url))).isEqualTo(2);
		assertThat(MigradorBaseDatos.ejecutarDesdeEntorno(
				Map.of("DB_URL", url, "DB_MIGRADOR_USUARIO", "sa", "DB_MIGRADOR_CLAVE", " "))).isEqualTo(2);
	}

	@Test
	void desdeElEntornoDevuelveCeroSiMigraYUnoSiFalla() {
		assertThat(MigradorBaseDatos.ejecutarDesdeEntorno(
				Map.of("DB_URL", url, "DB_MIGRADOR_USUARIO", "sa", "DB_MIGRADOR_CLAVE", "x"))).isZero();
		assertThat(MigradorBaseDatos.ejecutarDesdeEntorno(
				Map.of("DB_URL", "jdbc:base-que-no-existe:x", "DB_MIGRADOR_USUARIO", "sa", "DB_MIGRADOR_CLAVE", "x")))
				.isEqualTo(1);
	}
}
