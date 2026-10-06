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
					+ "verificacion_bancaria|reembolso|orden_pago_cuota|configuracion_bd|archivo_cargado|movimiento_bancario|"
					+ "liquidacion_pasarela|liquidacion_linea|reembolso_pasarela) ");

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

	/**
	 * Correcciones del sprint 4 (V16): si cc_app pudiera reescribir la muestra fija de una confirmación a ciegas o la
	 * semilla del muestreo, editar un reembolso por la pasarela o insertar uno que no corresponde (sin su trigger), o
	 * borrar o reescribir un enlace de activación (S4-M2), la aplicación no arranca.
	 */
	@Test
	void fallaSiSePuedenTocarLasCorreccionesDelSprint4() {
		for (String sql : new String[] { "UPDATE extracto_bancario SET muestra = muestra WHERE 1 = 0",
				"UPDATE extracto_bancario SET semilla_muestreo = semilla_muestreo WHERE 1 = 0",
				"UPDATE lote_recaudacion SET muestra = muestra WHERE 1 = 0",
				"UPDATE reembolso_pasarela SET version = version WHERE 1 = 0",
				"DELETE FROM reembolso_pasarela WHERE 1 = 0",
				"UPDATE partida_conciliacion SET linea_recaudacion_id = linea_recaudacion_id WHERE 1 = 0",
				"DELETE FROM enlace_activacion WHERE 1 = 0",
				"UPDATE enlace_activacion SET hash_token = hash_token WHERE 1 = 0",
				"UPDATE enlace_activacion SET vence_en = vence_en WHERE 1 = 0",
				"UPDATE enlace_activacion SET usuario_id = usuario_id WHERE 1 = 0" }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(sql);
			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos()).as(sql)
					.isInstanceOf(IllegalStateException.class);
		}
		JdbcTemplate sinTrigger = mysqlQueDeniega();
		doThrow(denegado(1452)).when(sinTrigger).update(org.mockito.ArgumentMatchers.startsWith(
				"INSERT INTO reembolso_pasarela"));
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(sinTrigger, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("trg_reembolso_pasarela_registro");
	}

	/** Un MySQL bien configurado: DELETE → 1142, UPDATE de columnas inmutables → 1143, INSERT imposible → 1644. */
	private static JdbcTemplate mysqlQueDeniega() {
		JdbcTemplate mysql = mock(JdbcTemplate.class);
		when(mysql.update(anyString())).thenAnswer(invocacion -> {
			String sql = invocacion.getArgument(0);
			if (sql.startsWith("INSERT INTO configuracion_bd")) {
				throw denegado(1142);
			}
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
		when(mysql.queryForObject(VerificadorPermisosBaseDatos.SQL_PASARELA_SIMULADA, Integer.class)).thenReturn(0);
		return mysql;
	}

	/** Sprint 4, tanda 1: órdenes, cuotas de orden y avisos sin borrado; columnas inmutables; configuracion_bd del DBA. */
	@Test
	void fallaSiSePuedeTocarLoDePagosEnLinea() {
		for (String[] caso : new String[][] { { "DELETE FROM orden_pago WHERE 1 = 0", "orden_pago se podrían borrar" },
				{ "DELETE FROM evento_pasarela WHERE 1 = 0", "evento_pasarela se podrían borrar" },
				{ "UPDATE orden_pago_cuota SET version = version WHERE 1 = 0", "orden_pago_cuota se podrían editar" },
				{ "UPDATE orden_pago SET monto = monto WHERE 1 = 0", "orden_pago" },
				{ "UPDATE evento_pasarela SET orden_pago_id = orden_pago_id WHERE 1 = 0", "evento_pasarela" },
				{ "UPDATE caja_diaria SET canal = canal WHERE 1 = 0", "caja_diaria" },
				{ "UPDATE pago SET orden_pago_id = orden_pago_id WHERE 1 = 0", "pago" },
				{ "UPDATE comprobante SET reemplaza_id = reemplaza_id WHERE 1 = 0", "comprobante" },
				{ "INSERT INTO configuracion_bd VALUES ('verificador', 'x', NOW(6))", "pasarela simulada" },
				{ "UPDATE configuracion_bd SET valor = valor WHERE 1 = 0", "solo escribe el DBA" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(caso[0]);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	@Test
	void fallaSiFaltanLosTriggersDePagosEnLinea() {
		for (String[] caso : new String[][] { { "INSERT INTO orden_pago ", "trg_orden_pago_nace" },
				{ "INSERT INTO orden_pago_cuota", "trg_orden_pago_cuota_registro" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doThrow(denegado(1452)).when(mysql).update(org.mockito.ArgumentMatchers.startsWith(caso[0]));

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[1]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	/** Sprint 4, tanda 3: si se puede borrar o editar algo del extracto o de la conciliación, no arranca. */
	@Test
	void fallaSiElExtractoOLaConciliacionSePuedenBorrarOEditar() {
		for (String[] caso : new String[][] { { "DELETE FROM cuenta_bancaria WHERE 1 = 0", "cuenta_bancaria" },
				{ "DELETE FROM extracto_bancario WHERE 1 = 0", "extracto_bancario" },
				{ "DELETE FROM movimiento_bancario WHERE 1 = 0", "movimiento_bancario" },
				{ "DELETE FROM partida_conciliacion WHERE 1 = 0", "partida_conciliacion" },
				{ "DELETE FROM liquidacion_pasarela WHERE 1 = 0", "liquidacion_pasarela" },
				{ "DELETE FROM liquidacion_linea WHERE 1 = 0", "liquidacion_linea" },
				{ "UPDATE movimiento_bancario SET version = version WHERE 1 = 0", "solo inserción" },
				{ "UPDATE liquidacion_pasarela SET version = version WHERE 1 = 0", "solo inserción" },
				{ "UPDATE liquidacion_linea SET version = version WHERE 1 = 0", "solo inserción" },
				{ "UPDATE extracto_bancario SET saldo_final = saldo_final WHERE 1 = 0", "extracto_bancario" },
				{ "UPDATE partida_conciliacion SET monto_movimiento = monto_movimiento WHERE 1 = 0",
						"partida_conciliacion" },
				{ "UPDATE cuenta_bancaria SET numero = numero WHERE 1 = 0", "cuenta_bancaria" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(caso[0]);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	@Test
	void fallaSiFaltanLosTriggersDelExtractoYLaConciliacion() {
		for (String[] caso : new String[][] { { "INSERT INTO extracto_bancario", "trg_extracto_bancario_nace" },
				{ "INSERT INTO movimiento_bancario", "trg_movimiento_bancario_registro" },
				{ "INSERT INTO partida_conciliacion", "trg_partida_conciliacion_registro" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doThrow(denegado(1452)).when(mysql).update(org.mockito.ArgumentMatchers.startsWith(caso[0]));

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[1]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
		// Correcciones del sprint 4 (V16): 41 con trg_reembolso_pasarela_registro.
		org.assertj.core.api.Assertions.assertThat(VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS).hasSize(41);
	}

	/**
	 * Hallado en MySQL 8 real (tanda 3): con 40 triggers la lista pasa de 1024 caracteres y GROUP_CONCAT (con su
	 * group_concat_max_len por defecto) la cortaba; dentro de la función eso es el error 1260 y prod no arrancaba. La
	 * función arma la lista con JSON_ARRAYAGG, que no tiene ese límite.
	 */
	@Test
	void laListaDeTriggersNoDependeDeGroupConcat() throws java.io.IOException {
		String script = java.nio.file.Files.readString(java.nio.file.Path.of("scripts/mysql/02-permisos-tablas.sql"));
		String funcion = script.substring(script.indexOf("CREATE FUNCTION cuentasclaras.triggers_instalados"),
				script.indexOf("GRANT EXECUTE ON FUNCTION cuentasclaras.triggers_instalados"));
		org.assertj.core.api.Assertions.assertThat(funcion).contains("JSON_ARRAYAGG(TRIGGER_NAME)")
				.doesNotContain("GROUP_CONCAT");
		org.assertj.core.api.Assertions.assertThat(String.join(",", VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS))
				.hasSizeGreaterThan(1024);
	}

	/** Sprint 4, tanda 2: si se puede borrar o editar algo de la recaudación bancaria, no arranca. */
	@Test
	void fallaSiLaRecaudacionSePuedeBorrarOEditar() {
		for (String[] caso : new String[][] { { "DELETE FROM archivo_cargado WHERE 1 = 0", "archivo_cargado" },
				{ "DELETE FROM lote_recaudacion WHERE 1 = 0", "lote_recaudacion" },
				{ "DELETE FROM linea_recaudacion WHERE 1 = 0", "linea_recaudacion" },
				{ "UPDATE archivo_cargado SET version = version WHERE 1 = 0", "solo inserción" },
				{ "UPDATE lote_recaudacion SET total = total WHERE 1 = 0", "lote_recaudacion" },
				{ "UPDATE linea_recaudacion SET monto = monto WHERE 1 = 0", "linea_recaudacion" },
				{ "UPDATE pago SET linea_recaudacion_id = linea_recaudacion_id WHERE 1 = 0", "pago" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doReturn(0).when(mysql).update(caso[0]);

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	@Test
	void fallaSiFaltanLosTriggersDeRecaudacion() {
		for (String[] caso : new String[][] { { "INSERT INTO lote_recaudacion", "trg_lote_recaudacion_nace" },
				{ "INSERT INTO linea_recaudacion", "trg_linea_recaudacion_registro" } }) {
			JdbcTemplate mysql = mysqlQueDeniega();
			doThrow(denegado(1452)).when(mysql).update(org.mockito.ArgumentMatchers.startsWith(caso[0]));

			assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(mysql, fuenteDatos).verificarPermisos())
					.as(caso[1]).isInstanceOf(IllegalStateException.class).hasMessageContaining(caso[1]);
		}
	}

	/** Prod: con la fila 'pasarela_simulada' en configuracion_bd, o si la base admite una orden SIMULADA, no arranca. */
	@Test
	void enProduccionLaBaseNoAdmiteLaPasarelaSimulada() {
		JdbcTemplate conFila = mysqlQueDeniega();
		when(conFila.queryForObject(VerificadorPermisosBaseDatos.SQL_PASARELA_SIMULADA, Integer.class)).thenReturn(1);
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(conFila, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("PRODUCCIÓN");

		JdbcTemplate admite = mysqlQueDeniega();
		doReturn(1).when(admite).update(VerificadorPermisosBaseDatos.ORDEN_SIMULADA.sql());
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(admite, fuenteDatos).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("SIMULADA");
	}

	/** Piloto: lo contrario, la fila debe existir; con ella arranca aunque la orden SIMULADA sí se pueda crear. */
	@Test
	void enElPilotoLaPasarelaSimuladaDebeEstarHabilitada() {
		JdbcTemplate sinFila = mysqlQueDeniega();
		assertThatThrownBy(() -> new VerificadorPermisosBaseDatos(sinFila, fuenteDatos, false).verificarPermisos())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("piloto");

		JdbcTemplate conFila = mysqlQueDeniega();
		when(conFila.queryForObject(VerificadorPermisosBaseDatos.SQL_PASARELA_SIMULADA, Integer.class)).thenReturn(1);
		assertThatCode(() -> new VerificadorPermisosBaseDatos(conFila, fuenteDatos, false).verificarPermisos())
				.doesNotThrowAnyException();
	}

	/**
	 * M2 (correcciones del sprint 3): si alguien borra un trigger BEFORE UPDATE (que ningún INSERT imposible detecta),
	 * la aplicación no arranca.
	 */
	@Test
	void fallaSiFaltaUnTriggerBeforeUpdate() {
		for (String borrado : List.of("trg_caja_diaria_estado", "trg_pago_anulacion", "trg_cuota_libro",
				"trg_cierre_caja_revisado", "trg_solicitud_cambio_resuelta", "trg_comprobante_envio",
				"trg_lote_recaudacion_estado", "trg_linea_recaudacion_estado", "trg_extracto_bancario_estado",
				"trg_partida_conciliacion_estado")) {
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
