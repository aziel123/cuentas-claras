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
	void arrancaSiMysqlDeniegaComoDebe() {
		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysqlQueDeniega(), fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
	}

	@Test
	void fallaSiFaltanLosTriggers() {
		JdbcTemplate mysql = mysqlQueDeniega();
		// Sin el trigger, el INSERT imposible falla por la FK (1452), no por el trigger (1644).
		doThrow(denegado(1452)).when(mysql).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO plan_pension"));

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("trg_plan_pension_nace_borrador").hasMessageContaining("1452");
	}

	@Test
	void fallaSiLasColumnasDelPlanEstanAbiertas() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update("UPDATE plan_pension SET numero_version = numero_version WHERE 1 = 0");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("plan_pension")
				.hasMessageContaining("GRANT por columna");
	}

	@Test
	void fallaSiSePuedeBorrarUnaSolicitud() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update("DELETE FROM solicitud_cambio WHERE 1 = 0");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("solicitud_cambio se podrían borrar");
	}

	/** Fase 1 del despliegue (antes de 02 y 03): cc_app solo tiene SELECT. No puede saltarse nada: arranca. */
	@Test
	void arrancaSiLaAppSoloPuedeLeer() {
		JdbcTemplate mysql = org.mockito.Mockito.mock(JdbcTemplate.class);
		doThrow(denegado(1142)).when(mysql).update(org.mockito.ArgumentMatchers.anyString());

		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.doesNotThrowAnyException();
	}

	@Test
	void arrancaSiMysqlDeniegaElMontoDeLaCuotaSinNingunUpdateCon1142() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doThrow(denegado(1142)).when(mysql).update(SQL_MONTO_CUOTA);

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

	/** Un MySQL bien configurado: DELETE → 1142, UPDATE de columnas inmutables → 1143, INSERT imposible → 1644. */
	private static JdbcTemplate mysqlQueDeniega() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenAnswer(invocacion -> {
			String sql = invocacion.getArgument(0);
			if (sql.startsWith("INSERT")) {
				throw denegado(1644);
			}
			if (sql.startsWith("UPDATE") && !sql.contains("evento_auditoria")) {
				throw denegado(1143);
			}
			throw denegado(1142);
		});
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
