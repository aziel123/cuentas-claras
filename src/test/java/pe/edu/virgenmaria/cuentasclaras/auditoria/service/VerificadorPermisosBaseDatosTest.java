package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * En H2 el usuario de pruebas puede todo: sirve para ver que el verificador impide arrancar.
 * El caso de MySQL (error 1142) se simula aquí; contra MySQL real lo prueba el job de CI.
 * No hay forma de desactivar la comprobación.
 */
@PruebaJpa
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VerificadorPermisosBaseDatosTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource fuenteDatos;

	@Test
	void fallaElArranqueSiLaAppPuedeEditarLaAuditoria() {
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).afterPropertiesSet())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PUEDE ejecutar")
				.hasMessageContaining("mysql-usuarios.md");
	}

	@Test
	void arrancaSiMysqlDeniegaConError1142() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenThrow(new UncategorizedSQLException("verificar", "UPDATE ...",
				new SQLException("UPDATE command denied to user 'cc_app'", "42000", 1142)));

		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
	}

	@Test
	void arrancaSiMysqlDeniegaElMontoDeLaCuotaPorColumnaCon1143() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doThrow(denegado(1143)).when(mysql).update(SQL_MONTO_CUOTA);

		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.doesNotThrowAnyException();
	}

	@Test
	void fallaElArranqueSiLaAppPuedeBorrarCuotas() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update(SQL_BORRAR_CUOTA);

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PUEDE ejecutar «" + SQL_BORRAR_CUOTA + "»")
				.hasMessageContaining("cuotas se podrían borrar");
	}

	@Test
	void fallaSiPuedeCambiarElMontoDeUnaCuota() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update(SQL_MONTO_CUOTA);

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PUEDE ejecutar «" + SQL_MONTO_CUOTA + "»")
				.hasMessageContaining("GRANT por columna");
	}

	@Test
	void borrarCuotasDenegadoConOtroCodigoTambienImpideArrancar() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doThrow(denegado(1143)).when(mysql).update(SQL_BORRAR_CUOTA);

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("1143");
	}

	private static final String SQL_BORRAR_CUOTA = "DELETE FROM cuota WHERE 1 = 0";

	private static final String SQL_MONTO_CUOTA = "UPDATE cuota SET monto = monto WHERE 1 = 0";

	private static JdbcTemplate mysqlQueDeniega() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenThrow(denegado(1142));
		return mysql;
	}

	private static UncategorizedSQLException denegado(int codigo) {
		return new UncategorizedSQLException("verificar", "SQL", new SQLException("command denied", "42000", codigo));
	}

	@Test
	void otroErrorTambienImpideArrancar() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenThrow(new UncategorizedSQLException("verificar", "UPDATE ...",
				new SQLException("Table 'evento_auditoria' doesn't exist", "42S02", 1146)));

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("1146");
	}

	@Test
	void conMigracionesPendientesNoArranca() {
		DataSource baseVacia = new DriverManagerDataSource(
				"jdbc:h2:mem:vacia-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(jdbc, baseVacia).verificarMigraciones())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Faltan migraciones")
				.hasMessageContaining("V1")
				.hasMessageContaining("migrar");
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, fuenteDatos).verificarMigraciones())
				.doesNotThrowAnyException();
	}
}
