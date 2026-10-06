package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorPermisosBaseDatos;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FASE 1 del job "mysql" del CI (antes de los permisos por tabla). Antes corre «java -jar ... migrar» con
 * cc_migrador; aquí la aplicación arranca con cc_app y sin Flyway: Hibernate valida el esquema
 * ({@code ddl-auto: validate}) y no deben faltar migraciones. Solo corre si {@code CC_PRUEBA_MYSQL=true}.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class MigracionMySqlTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Test
	void lasMigracionesSeAplicaronYHibernateValidoElEsquema() {
		List<String> versiones = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
				String.class);
		assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
		assertThat(jdbc.queryForObject("SELECT ultima_secuencia FROM auditoria_cadena WHERE id = 1", Long.class))
				.isNotNull();
	}

	@Test
	void elUsuarioDeLaAplicacionNoPuedeEditarNiBorrarLaBitacoraYNoFaltanMigraciones() {
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
	}
}
