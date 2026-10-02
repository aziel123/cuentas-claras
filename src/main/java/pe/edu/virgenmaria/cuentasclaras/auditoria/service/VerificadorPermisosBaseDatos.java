package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * En producción, al crearse (antes de que el servidor web acepte peticiones), comprueba:
 * <ul>
 *   <li>que el usuario de la aplicación NO puede editar ni borrar la bitácora: MySQL debe rechazar
 *       {@code UPDATE} y {@code DELETE} sobre {@code evento_auditoria} con el error 1142;</li>
 *   <li>que no puede borrar cuotas ({@code DELETE} sobre {@code cuota} → 1142) ni cambiar su monto
 *       ({@code UPDATE} de la columna {@code monto} → 1143, o 1142 si no tiene ningún UPDATE);</li>
 *   <li>que no puede borrar ni editar el libro de pagos, los comprobantes ni las cajas, ni cambiar sus columnas
 *       inmutables, y que están los triggers de caja (sprint 3);</li>
 *   <li>que no faltan migraciones (en producción la aplicación no migra: se corre {@code migrar} antes).</li>
 * </ul>
 * Si algo falla, la aplicación NO arranca. No hay interruptor para saltarse esta comprobación.
 * Las sentencias usan {@code WHERE 1 = 0}: aunque MySQL las permitiera, no tocan ninguna fila.
 * Es la ÚNICA clase autorizada a usar {@link JdbcTemplate} (regla ArchUnit).
 */
@Component
@Profile("prod")
public class VerificadorPermisosBaseDatos implements InitializingBean {

	static final int MYSQL_COMANDO_DENEGADO = 1142;

	static final int MYSQL_COLUMNA_DENEGADA = 1143;

	/** SIGNAL SQLSTATE '45000' de un trigger (scripts/mysql/03-triggers.sql). */
	static final int MYSQL_SIGNAL = 1644;

	/** Sentencia que la base DEBE rechazar, con los códigos de error aceptados y lo que significaría si no. */
	record SentenciaProhibida(String sql, Set<Integer> codigosAceptados, String riesgo) {
	}

	static final List<SentenciaProhibida> SENTENCIAS_PROHIBIDAS = List.of(
			new SentenciaProhibida("UPDATE evento_auditoria SET ip = ip WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"la bitácora no está protegida en la base."),
			new SentenciaProhibida("DELETE FROM evento_auditoria WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"la bitácora no está protegida en la base."),
			new SentenciaProhibida("DELETE FROM cuota WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"las cuotas se podrían borrar."),
			new SentenciaProhibida("UPDATE cuota SET monto = monto WHERE 1 = 0",
					Set.of(MYSQL_COLUMNA_DENEGADA, MYSQL_COMANDO_DENEGADO),
					"el monto de una cuota se podría cambiar por SQL (falta el GRANT por columna)."),
			// Correcciones del sprint 2: el resto de tablas financieras, también por columna y sin DELETE.
			columna("UPDATE plan_pension SET numero_version = numero_version WHERE 1 = 0", "plan_pension"),
			columna("UPDATE lote_saldo_inicial SET total_declarado = total_declarado WHERE 1 = 0", "lote_saldo_inicial"),
			columna("UPDATE linea_saldo_inicial SET monto = monto WHERE 1 = 0", "linea_saldo_inicial"),
			columna("UPDATE solicitud_cambio SET datos = datos WHERE 1 = 0", "solicitud_cambio"),
			sinBorrado("plan_pension"), sinBorrado("lote_saldo_inicial"), sinBorrado("linea_saldo_inicial"),
			sinBorrado("solicitud_cambio"),
			// Triggers de 03-triggers.sql: un INSERT imposible (colegio 0) que el trigger rechaza antes de tocar la fila.
			// Sin el trigger, MySQL respondería otro error (FK o CHECK) y la aplicación no arranca.
			trigger("INSERT INTO plan_pension (colegio_id, anio_escolar_id, nivel, numero_version, estado, vigente, "
					+ "monto_matricula, vencimiento_matricula, monto_pension, vencimientos_pension, editado_por, editores, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, 0, 'PRIMARIA', 1, 'APROBADO', TRUE, 0, "
					+ "'2000-02-28', 1, '2000-03-31', 'verificador', ',verificador,', NOW(6), 'verificador', NOW(6))",
					"trg_plan_pension_nace_borrador"),
			trigger("INSERT INTO lote_saldo_inicial (colegio_id, anio_escolar_id, fecha_corte, documento_referencia, "
					+ "total_declarado, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, '2000-01-01', "
					+ "'verificador', 1, 'CONFIRMADO', NOW(6), 'verificador', NOW(6))", "trg_lote_saldo_inicial_nace_borrador"),
			trigger("INSERT INTO linea_saldo_inicial (colegio_id, lote_id, alumno_id, concepto, descripcion, monto, "
					+ "fecha_vencimiento, creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, 'OTRO', 'verificador', 1, "
					+ "'2000-01-01', NOW(6), 'verificador', NOW(6))", "trg_linea_saldo_inicial_lote_abierto"),
			// Sprint 3, tanda 1 (caja y comprobantes): nada se borra; el libro y las líneas son de solo inserción; los
			// datos tributarios y del pago no cambian; y cada trigger rechaza su inserción imposible.
			sinBorrado("serie_comprobante"), sinBorrado("comprobante"), sinBorrado("comprobante_linea"),
			sinBorrado("caja_diaria"), sinBorrado("pago"), sinBorrado("aplicacion_pago"),
			soloInsercion("comprobante_linea"), soloInsercion("aplicacion_pago"),
			columna("UPDATE pago SET total = total WHERE 1 = 0", "pago"),
			columna("UPDATE comprobante SET numero = numero WHERE 1 = 0", "comprobante"),
			columna("UPDATE serie_comprobante SET serie = serie WHERE 1 = 0", "serie_comprobante"),
			columna("UPDATE caja_diaria SET fecha = fecha WHERE 1 = 0", "caja_diaria"),
			trigger("INSERT INTO cuota (colegio_id, alumno_id, anio_escolar_id, tipo, descripcion, monto, monto_pagado, "
					+ "monto_descuento, fecha_vencimiento, estado, clave, creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, "
					+ "'PENSION', 'verificador', 1, 1, 0, '2000-01-01', 'PAGADA', 'verificador', NOW(6), 'verificador', NOW(6))",
					"trg_cuota_nace_pendiente"),
			trigger("INSERT INTO serie_comprobante (colegio_id, tipo, serie, proveedor, ultimo_numero, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 'BOLETA', 'B999', 'SIMULADO', 5, NOW(6), 'verificador', NOW(6))",
					"trg_serie_comprobante_nace"),
			trigger("INSERT INTO comprobante (colegio_id, serie_id, tipo, serie, numero, fecha_emision, receptor_tipo_documento, "
					+ "receptor_numero_documento, receptor_nombre, moneda, total, afectacion_igv, proveedor, estado_envio, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, 0, 'BOLETA', 'B999', 1, '2000-01-01', 'DNI', '00000000', "
					+ "'verificador', 'PEN', 1, 'INAFECTO', 'SIMULADO', 'PENDIENTE', NOW(6), 'verificador', NOW(6))",
					"trg_comprobante_correlativo"),
			trigger("INSERT INTO caja_diaria (colegio_id, cajero, fecha, fondo_fijo, estado, cierres, conteos, creado_en, "
					+ "creado_por, actualizado_en) VALUES (0, 'verificador', '2000-01-01', 0, 'CERRADA', 1, 0, NOW(6), "
					+ "'verificador', NOW(6))", "trg_caja_diaria_nace"),
			trigger("INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, total, "
					+ "recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, "
					+ "0, 'verificador', '2000-01-01', 0, 'EFECTIVO', 1, 1, 0, 'CAJA', 'verificador', 'ANULADO', NOW(6), "
					+ "'verificador', NOW(6))", "trg_pago_registro"),
			trigger("INSERT INTO aplicacion_pago (colegio_id, pago_id, cuota_id, tipo, monto, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 'APLICACION', 1, NOW(6), 'verificador', NOW(6))",
					"trg_aplicacion_pago_registro"),
			// Sprint 3, tanda 2 (anulaciones y descuentos).
			sinBorrado("anulacion_pago"), sinBorrado("descuento"), sinBorrado("ajuste_cuota"),
			soloInsercion("anulacion_pago"), soloInsercion("ajuste_cuota"),
			columna("UPDATE descuento SET valor = valor WHERE 1 = 0", "descuento"),
			trigger("INSERT INTO anulacion_pago (colegio_id, pago_id, solicitud_id, nota_credito_id, tipo, motivo, monto, "
					+ "cajero_pago, solicitado_por, aprobado_por, posterior_al_cierre, creado_en, creado_por, actualizado_en) "
					+ "VALUES (0, 0, 0, 0, 'DEVOLUCION', 'verificador de permisos', 1, 'a', 'b', 'c', FALSE, NOW(6), 'c', "
					+ "NOW(6))", "trg_anulacion_pago_registro"),
			trigger("INSERT INTO descuento (colegio_id, alumno_id, anio_escolar_id, tipo, modalidad, valor, cuotas, "
					+ "total_estimado, motivo, sustento, estado, resuelto_por, resuelto_en, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 'OTRO', 'MONTO', 1, ',0,', 1, 'verificador de permisos', "
					+ "'verificador', 'APROBADO', 'b', NOW(6), NOW(6), 'a', NOW(6))", "trg_descuento_nace"),
			trigger("INSERT INTO ajuste_cuota (colegio_id, cuota_id, descuento_id, monto, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 1, NOW(6), 'verificador', NOW(6))", "trg_ajuste_cuota_registro"));

	/** 1143 (columna sin GRANT) o 1142 (ningún UPDATE sobre la tabla, por ejemplo antes de aplicar el paso 2). */
	private static SentenciaProhibida columna(String sql, String tabla) {
		return new SentenciaProhibida(sql, Set.of(MYSQL_COLUMNA_DENEGADA, MYSQL_COMANDO_DENEGADO),
				"las columnas inmutables de " + tabla + " se podrían cambiar por SQL (falta el GRANT por columna).");
	}

	/** Tabla de solo inserción: cc_app no tiene ningún UPDATE sobre ella (1142). */
	private static SentenciaProhibida soloInsercion(String tabla) {
		return new SentenciaProhibida("UPDATE " + tabla + " SET version = version WHERE 1 = 0",
				Set.of(MYSQL_COMANDO_DENEGADO), "los registros de " + tabla + " se podrían editar (es de solo inserción).");
	}

	private static SentenciaProhibida sinBorrado(String tabla) {
		return new SentenciaProhibida("DELETE FROM " + tabla + " WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
				"los registros de " + tabla + " se podrían borrar.");
	}

	/**
	 * 1644 (el trigger lo rechazó) o 1142 (la aplicación no puede insertar en esa tabla, así que tampoco puede saltarse
	 * el trigger; pasa en la fase 1 del despliegue, antes de los GRANT del paso 2). Cualquier otro código (la FK o un
	 * CHECK, que llegan después del trigger) significa que falta el trigger.
	 */
	private static SentenciaProhibida trigger(String sql, String nombre) {
		return new SentenciaProhibida(sql, Set.of(MYSQL_SIGNAL, MYSQL_COMANDO_DENEGADO), "falta el trigger " + nombre
				+ " (aplica scripts/mysql/03-triggers.sql con cc_migrador).");
	}

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorPermisosBaseDatos.class);

	private final JdbcTemplate jdbc;

	private final DataSource fuenteDatos;

	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos) {
		this.jdbc = jdbc;
		this.fuenteDatos = fuenteDatos;
	}

	@Override
	public void afterPropertiesSet() {
		verificarMigraciones();
		verificarPermisos();
	}

	public void verificarPermisos() {
		for (SentenciaProhibida sentencia : SENTENCIAS_PROHIBIDAS) {
			String problema = comprobarDenegada(sentencia);
			if (problema != null) {
				throw new IllegalStateException(problema + " Revisa docs/operacion/mysql-usuarios.md.");
			}
		}
		LOG.info("Permisos de la bitácora verificados: la aplicación no puede editar ni borrar eventos.");
		LOG.info("Permisos de las cuotas verificados: la aplicación no puede borrarlas ni cambiar su monto.");
		LOG.info("Permisos por columna y triggers de planes, lotes y solicitudes verificados.");
		LOG.info("Permisos y triggers de caja, comprobantes, anulaciones y descuentos verificados.");
	}

	public void verificarMigraciones() {
		MigrationInfo[] pendientes = Flyway.configure().dataSource(fuenteDatos).locations("classpath:db/migration")
				.load().info().pending();
		if (pendientes.length > 0) {
			throw new IllegalStateException("Faltan migraciones de la base: "
					+ Arrays.stream(pendientes).map(m -> "V" + m.getVersion()).collect(Collectors.joining(", "))
					+ ". Antes de arrancar ejecuta «java -jar cuentas-claras.jar migrar» con el usuario cc_migrador.");
		}
	}

	/** @return {@code null} si la base la rechazó con un código aceptado; si no, la descripción del problema */
	private String comprobarDenegada(SentenciaProhibida sentencia) {
		try {
			jdbc.update(sentencia.sql());
			return "El usuario de la aplicación PUEDE ejecutar «" + sentencia.sql() + "»: " + sentencia.riesgo();
		}
		catch (DataAccessException e) {
			Integer codigo = codigoMySql(e);
			if (codigo != null && sentencia.codigosAceptados().contains(codigo)) {
				return null;
			}
			return "No se pudo comprobar «" + sentencia.sql() + "» (código " + codigo + "): "
					+ e.getMostSpecificCause().getMessage() + ". Si no se rechaza como se espera, " + sentencia.riesgo();
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
