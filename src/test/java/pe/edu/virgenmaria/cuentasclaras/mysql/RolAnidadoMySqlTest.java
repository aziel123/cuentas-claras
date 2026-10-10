package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorPermisosBaseDatos;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Correcciones del sprint 7 (S7-M1) contra MySQL 8 real. El paso del job mysql del CI anida, como administrador, un rol con
 * DELETE sobre acceso_dato_personal DENTRO de cc_negocio ({@code CC_PRUEBA_ROL_ANIDADO=presente}): cc_app ya puede borrar
 * (el privilegio oculto funciona) y el verificador de prod lo detecta con {@code APPLICABLE_ROLES}, aunque SHOW GRANTS no
 * lo muestre. Después aplica 02, que recrea cc_negocio ({@code ausente}): el borrado vuelve a dar 1142 y el verificador
 * pasa.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_ROL_ANIDADO", matches = "presente|ausente")
class RolAnidadoMySqlTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource fuenteDatos;

	@Test
	void conElRolAnidadoElVerificadorNoDejaArrancar() {
		assumeTrue("presente".equals(System.getenv("CC_PRUEBA_ROL_ANIDADO")));
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM acceso_dato_personal WHERE 1 = 0")))
				.as("el privilegio oculto del rol anidado funciona para cc_app").isNull();
		assertThat(jdbc.queryForList("SHOW GRANTS", String.class)).as("SHOW GRANTS no nombra el rol anidado")
				.noneMatch(l -> l.contains("cc_rol_oculto"));
		assertThat(jdbc.queryForList(VerificadorPermisosBaseDatos.SQL_ROLES, String.class)).as("APPLICABLE_ROLES sí")
				.contains("cc_negocio|cc_rol_oculto");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).verificarPrivilegios())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("cc_rol_oculto")
				.hasMessageContaining("roles anidados");
	}

	@Test
	void sinElRolAnidadoNoBorraYElVerificadorPasa() {
		assumeTrue("ausente".equals(System.getenv("CC_PRUEBA_ROL_ANIDADO")));
		assertThat(codigoAl(() -> jdbc.update("DELETE FROM acceso_dato_personal WHERE 1 = 0"))).isEqualTo(1142);

		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).verificarPrivilegios())
				.doesNotThrowAnyException();
	}

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			return -1;
		}
	}
}
