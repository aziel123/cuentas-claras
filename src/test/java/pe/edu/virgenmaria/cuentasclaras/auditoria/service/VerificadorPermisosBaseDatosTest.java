package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * En H2 el usuario de pruebas puede todo: sirve para ver que el verificador impide arrancar.
 * El caso de MySQL (error 1142) se simula; contra MySQL real lo prueba el job de CI.
 */
@PruebaJpa
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VerificadorPermisosBaseDatosTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void fallaElArranqueSiLaAppPuedeEditarLaAuditoria() {
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(jdbc, true).verificar())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PUEDE ejecutar")
				.hasMessageContaining("mysql-usuarios.md");
	}

	@Test
	void sinExigirSoloAdvierte() {
		assertThatCode(() -> new VerificadorPermisosBaseDatos(jdbc, false).verificar()).doesNotThrowAnyException();
	}

	@Test
	void arrancaSiMysqlDeniegaConError1142() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenThrow(new UncategorizedSQLException("verificar", "UPDATE ...",
				new SQLException("UPDATE command denied to user 'cc_app'", "42000", 1142)));

		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysql, true).verificar()).doesNotThrowAnyException();
	}

	@Test
	void otroErrorTambienImpideArrancar() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenThrow(new UncategorizedSQLException("verificar", "UPDATE ...",
				new SQLException("Table 'evento_auditoria' doesn't exist", "42S02", 1146)));

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, true).verificar())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("1146");
	}
}
