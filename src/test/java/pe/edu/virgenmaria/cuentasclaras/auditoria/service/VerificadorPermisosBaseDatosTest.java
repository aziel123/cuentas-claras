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
import java.util.List;
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

	/** Tablas de solo inserción: cc_app no tiene ningún UPDATE sobre ellas (1142). */
	private static final java.util.regex.Pattern SOLO_INSERCION = java.util.regex.Pattern
			.compile("^UPDATE (comprobante_linea|aplicacion_pago|anulacion_pago|ajuste_cuota|deposito_caja|"
					+ "verificacion_bancaria|reembolso) ");

	/** Sprint 3: el libro de pagos es de solo inserción; si cc_app pudiera editarlo, no arranca. */
	@Test
	void fallaSiSePuedeEditarElLibroDePagos() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update("UPDATE aplicacion_pago SET version = version WHERE 1 = 0");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("aplicacion_pago se podrían editar");
	}

	/** Tanda 2: las anulaciones y los ajustes de descuento son de solo inserción; un descuento pedido no cambia. */
	@Test
	void fallaSiSePuedeEditarUnaAnulacionOUnAjusteOElValorDeUnDescuento() {
		for (String[] caso : new String[][] { { "UPDATE anulacion_pago SET version = version WHERE 1 = 0", "anulacion_pago" },
				{ "UPDATE ajuste_cuota SET version = version WHERE 1 = 0", "ajuste_cuota" },
				{ "UPDATE descuento SET valor = valor WHERE 1 = 0", "descuento" },
				{ "DELETE FROM descuento WHERE 1 = 0", "descuento se podrían borrar" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(caso[0]);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	/** Tanda 3: el conteo de un cierre no cambia; depósitos y verificaciones son de solo inserción. */
	@Test
	void fallaSiSePuedeCambiarUnConteoOEditarUnDepositoOUnaVerificacion() {
		for (String[] caso : new String[][] { { "UPDATE cierre_caja SET contado = contado WHERE 1 = 0", "cierre_caja" },
				{ "UPDATE deposito_caja SET version = version WHERE 1 = 0", "deposito_caja" },
				{ "UPDATE verificacion_bancaria SET version = version WHERE 1 = 0", "verificacion_bancaria" },
				{ "DELETE FROM cierre_caja WHERE 1 = 0", "cierre_caja se podrían borrar" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(caso[0]);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	@Test
	void fallaSiSePuedeBorrarUnPago() {
		JdbcTemplate mysql = mysqlQueDeniega();
		doReturn(0).when(mysql).update("DELETE FROM pago WHERE 1 = 0");

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("pago se podrían borrar");
	}

	@Test
	void fallaSiSePuedeCambiarElTotalDeUnPagoOElNumeroDeUnComprobante() {
		for (String sql : new String[] { "UPDATE pago SET total = total WHERE 1 = 0",
				"UPDATE comprobante SET numero = numero WHERE 1 = 0", "UPDATE serie_comprobante SET serie = serie WHERE 1 = 0",
				"UPDATE caja_diaria SET fecha = fecha WHERE 1 = 0" }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(sql);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(sql).isInstanceOf(IllegalStateException.class).hasMessageContaining("GRANT por columna");
		}
	}

	/** Sin trg_cuota_nace_pendiente, la cuota PAGADA imposible falla por la FK (1452): no arranca. */
	@Test
	void fallaSiFaltanLosTriggersDeCaja() {
		for (String[] caso : new String[][] { { "INSERT INTO cuota", "trg_cuota_nace_pendiente" },
				{ "INSERT INTO serie_comprobante", "trg_serie_comprobante_nace" },
				{ "INSERT INTO comprobante ", "trg_comprobante_correlativo" },
				{ "INSERT INTO caja_diaria", "trg_caja_diaria_nace" }, { "INSERT INTO pago", "trg_pago_registro" },
				{ "INSERT INTO aplicacion_pago", "trg_aplicacion_pago_registro" },
				{ "INSERT INTO anulacion_pago", "trg_anulacion_pago_registro" },
				{ "INSERT INTO descuento", "trg_descuento_nace" }, { "INSERT INTO ajuste_cuota", "trg_ajuste_cuota_registro" },
				{ "INSERT INTO cierre_caja", "trg_cierre_caja_registro" },
				{ "INSERT INTO verificacion_bancaria", "trg_verificacion_bancaria_registro" },
				{ "INSERT INTO reembolso", "trg_reembolso_registro" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doThrow(denegado(1452)).when(mysql).update(org.mockito.ArgumentMatchers.startsWith(caso[0]));

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[1]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	/** Un MySQL bien configurado: DELETE → 1142, UPDATE de columnas inmutables → 1143, INSERT imposible → 1644. */
	private static JdbcTemplate mysqlQueDeniega() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenAnswer(invocacion -> {
			String sql = invocacion.getArgument(0);
			if (sql.startsWith("INSERT")) {
				throw denegado(1644);
			}
			if (sql.startsWith("UPDATE") && !sql.contains("evento_auditoria") && !SOLO_INSERCION.matcher(sql).find()) {
				throw denegado(1143);
			}
			throw denegado(1142);
		});
		when(mysql.queryForObject(VerificadorPermisosBaseDatos.SQL_TRIGGERS_INSTALADOS, String.class))
				.thenReturn(String.join(",", VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS));
		return mysql;
	}

	/**
	 * M2 (correcciones del sprint 3): si alguien borra un trigger BEFORE UPDATE (que ningún INSERT imposible detecta),
	 * la aplicación no arranca.
	 */
	@Test
	void fallaSiFaltaUnTriggerBeforeUpdate() {
		for (String borrado : List.of("trg_caja_diaria_estado", "trg_pago_anulacion", "trg_cuota_libro",
				"trg_cierre_caja_revisado", "trg_solicitud_cambio_resuelta", "trg_comprobante_envio")) {
			JdbcTemplate mysql = mysqlQueDeniega();
			when(mysql.queryForObject(VerificadorPermisosBaseDatos.SQL_TRIGGERS_INSTALADOS, String.class)).thenReturn(
					String.join(",", VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS.stream().filter(t -> !t.equals(borrado))
							.toList()));

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).afterPropertiesSet())
					.as(borrado).isInstanceOf(IllegalStateException.class).hasMessageContaining("Faltan triggers")
					.hasMessageContaining(borrado);
		}
	}

	/** Fase 1 (cc_app solo lee, antes de 02 y 03): no hay vista ni triggers y no hace falta: arranca. */
	@Test
	void enLaFase1SinFuncionNiTriggersArranca() {
		JdbcTemplate mysql = org.mockito.Mockito.mock(JdbcTemplate.class);
		doThrow(denegado(1142)).when(mysql).update(anyString());
		when(mysql.queryForObject(VerificadorPermisosBaseDatos.SQL_TRIGGERS_INSTALADOS, String.class))
				.thenThrow(denegado(1305));

		assertThatCode(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).afterPropertiesSet())
				.doesNotThrowAnyException();
	}

	@Test
	void fallaSiNoExisteLaFuncionDeTriggers() {
		JdbcTemplate mysql = mysqlQueDeniega();
		when(mysql.queryForObject(VerificadorPermisosBaseDatos.SQL_TRIGGERS_INSTALADOS, String.class))
				.thenThrow(denegado(1305));

		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).afterPropertiesSet())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("triggers_instalados")
				.hasMessageContaining("1305");
	}

	/** La lista del verificador es EXACTAMENTE la de scripts/mysql/03-triggers.sql (en el mismo orden). */
	@Test
	void listaDeTriggersCoincideConElScript() throws java.io.IOException {
		String script = java.nio.file.Files.readString(java.nio.file.Path.of("scripts/mysql/03-triggers.sql"));
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("CREATE TRIGGER (\\w+)").matcher(script);
		List<String> enScript = new java.util.ArrayList<>();
		while (m.find()) {
			enScript.add(m.group(1));
		}
		org.assertj.core.api.Assertions.assertThat(enScript).isEqualTo(VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS);
		java.util.regex.Matcher borrados = java.util.regex.Pattern.compile("DROP TRIGGER IF EXISTS (\\w+)").matcher(script);
		List<String> conDrop = new java.util.ArrayList<>();
		while (borrados.find()) {
			conDrop.add(borrados.group(1));
		}
		org.assertj.core.api.Assertions.assertThat(conDrop).as("cada trigger se puede volver a aplicar")
				.isEqualTo(enScript);
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
