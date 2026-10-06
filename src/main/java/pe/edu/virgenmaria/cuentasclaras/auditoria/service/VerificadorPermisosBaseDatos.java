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
 *   <li>que están TODOS los triggers de {@code scripts/mysql/03-triggers.sql} (correcciones del sprint 3, M2), también
 *       los BEFORE UPDATE, que no se pueden probar con un INSERT imposible: llama a la función
 *       {@code cuentasclaras.triggers_instalados()} (02-permisos-tablas.sql) y la compara con {@link #TRIGGERS_ESPERADOS};</li>
 *   <li>que no faltan migraciones (en producción la aplicación no migra: se corre {@code migrar} antes).</li>
 * </ul>
 * Si algo falla, la aplicación NO arranca. No hay interruptor para saltarse esta comprobación.
 * Las sentencias usan {@code WHERE 1 = 0}: aunque MySQL las permitiera, no tocan ninguna fila.
 * Es la ÚNICA clase autorizada a usar {@link JdbcTemplate} (regla ArchUnit).
 */
@Component
@Profile({ "prod", "piloto" })
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
					+ "actualizado_en) VALUES (0, 0, 0, 1, NOW(6), 'verificador', NOW(6))", "trg_ajuste_cuota_registro"),
			// Sprint 3, tanda 3 (cierre, depósito y verificación bancaria).
			sinBorrado("cierre_caja"), sinBorrado("deposito_caja"), sinBorrado("verificacion_bancaria"),
			soloInsercion("deposito_caja"), soloInsercion("verificacion_bancaria"),
			columna("UPDATE cierre_caja SET contado = contado WHERE 1 = 0", "cierre_caja"),
			trigger("INSERT INTO cierre_caja (colegio_id, caja_diaria_id, numero, fondo_fijo, efectivo_cobrado, esperado, "
					+ "primer_conteo, contado, diferencia, pagos_efectivo, pagos_digitales, total_digital, estado, creado_en, "
					+ "creado_por, actualizado_en) VALUES (0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'POR_REVISAR', NOW(6), "
					+ "'verificador', NOW(6))", "trg_cierre_caja_registro"),
			trigger("INSERT INTO verificacion_bancaria (colegio_id, pago_id, resultado, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 'ENCONTRADO', NOW(6), 'verificador', NOW(6))",
					"trg_verificacion_bancaria_registro"),
			// Correcciones del sprint 3: el reembolso de una devolución es de solo inserción y lo vigila su trigger.
			sinBorrado("reembolso"), soloInsercion("reembolso"),
			trigger("INSERT INTO reembolso (colegio_id, anulacion_pago_id, medio, numero_operacion, monto, fecha, "
					+ "cajero_pago, creado_en, creado_por, actualizado_en) VALUES (0, 0, 'YAPE', 'VERIFICADOR', 1, "
					+ "'2000-01-01', 'a', NOW(6), 'b', NOW(6))", "trg_reembolso_registro"),
			// Sprint 4, tanda 1 (pagos en línea y outbox del OSE): las órdenes, sus cuotas y los avisos no se borran; las
			// cuotas de una orden son de solo inserción; configuracion_bd solo la escribe el DBA; las columnas inmutables
			// no cambian; y cada trigger nuevo rechaza su inserción imposible.
			sinBorrado("orden_pago"), sinBorrado("orden_pago_cuota"), sinBorrado("evento_pasarela"),
			sinBorrado("configuracion_bd"), soloInsercion("orden_pago_cuota"),
			new SentenciaProhibida("INSERT INTO configuracion_bd VALUES ('verificador', 'x', NOW(6))",
					Set.of(MYSQL_COMANDO_DENEGADO), "la aplicación podría habilitar la pasarela simulada en la base."),
			new SentenciaProhibida("UPDATE configuracion_bd SET valor = valor WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"la aplicación podría cambiar la configuración que solo escribe el DBA."),
			columna("UPDATE orden_pago SET monto = monto WHERE 1 = 0", "orden_pago"),
			columna("UPDATE evento_pasarela SET orden_pago_id = orden_pago_id WHERE 1 = 0", "evento_pasarela"),
			columna("UPDATE caja_diaria SET canal = canal WHERE 1 = 0", "caja_diaria"),
			columna("UPDATE pago SET orden_pago_id = orden_pago_id WHERE 1 = 0", "pago"),
			columna("UPDATE comprobante SET reemplaza_id = reemplaza_id WHERE 1 = 0", "comprobante"),
			trigger(ordenImposible("CULQI", "PAGADA"), "trg_orden_pago_nace"),
			trigger("INSERT INTO orden_pago_cuota (colegio_id, orden_pago_id, cuota_id, monto, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 1, NOW(6), 'verificador', NOW(6))", "trg_orden_pago_cuota_registro"));

	/** Solo en prod: la base no admite órdenes de la pasarela simulada (sin la fila 'pasarela_simulada'). */
	static final SentenciaProhibida ORDEN_SIMULADA = new SentenciaProhibida(ordenImposible("SIMULADA", "CREADA"),
			Set.of(MYSQL_SIGNAL, MYSQL_COMANDO_DENEGADO), "la base admite órdenes de la pasarela SIMULADA en producción "
					+ "(revisa trg_orden_pago_nace y que configuracion_bd no tenga la fila 'pasarela_simulada').");

	static final String SQL_PASARELA_SIMULADA = "SELECT COUNT(*) FROM configuracion_bd WHERE clave = 'pasarela_simulada'";

	private static String ordenImposible(String proveedor, String estado) {
		return "INSERT INTO orden_pago (colegio_id, referencia, familia_id, apoderado_id, proveedor, monto, moneda, "
				+ "comprobante_tipo, clave_idempotencia, vence_en, estado, creado_en, creado_por, actualizado_en) VALUES (0, "
				+ "'verificador', 0, 0, '" + proveedor + "', 1, 'PEN', 'BOLETA', 'verificador', NOW(6), '" + estado
				+ "', NOW(6), 'verificador', NOW(6))";
	}

	/**
	 * Todos los triggers de {@code scripts/mysql/03-triggers.sql}, en su orden. {@code TriggersEsperadosTest} exige que
	 * coincidan con el archivo: un trigger nuevo se agrega en los dos lugares.
	 */
	static final List<String> TRIGGERS_ESPERADOS = List.of("trg_plan_pension_nace_borrador", "trg_plan_pension_inmutable",
			"trg_lote_saldo_inicial_nace_borrador", "trg_lote_saldo_inicial_cerrado", "trg_linea_saldo_inicial_lote_abierto",
			"trg_linea_saldo_inicial_quitar", "trg_cuota_nace_pendiente", "trg_cuota_libro", "trg_serie_comprobante_nace",
			"trg_serie_comprobante_correlativo", "trg_comprobante_correlativo", "trg_caja_diaria_nace",
			"trg_caja_diaria_estado", "trg_pago_registro", "trg_pago_anulacion", "trg_aplicacion_pago_registro",
			"trg_anulacion_pago_registro", "trg_ajuste_cuota_registro", "trg_descuento_nace", "trg_descuento_resuelto",
			"trg_cierre_caja_registro", "trg_cierre_caja_revisado", "trg_verificacion_bancaria_registro",
			"trg_reembolso_registro", "trg_solicitud_cambio_resuelta", "trg_comprobante_envio", "trg_apoderado_nace",
			"trg_apoderado_facturacion", "trg_orden_pago_nace", "trg_orden_pago_cuota_registro", "trg_orden_pago_estado");

	static final String SQL_TRIGGERS_INSTALADOS = "SELECT triggers_instalados()";

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

	/** {@code true} en prod; {@code false} en piloto (allí la pasarela simulada debe estar habilitada por el DBA). */
	private final boolean produccion;

	/** Como en producción. */
	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos) {
		this(jdbc, fuenteDatos, true);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos,
			org.springframework.core.env.Environment entorno) {
		this(jdbc, fuenteDatos, !Arrays.asList(entorno.getActiveProfiles()).contains("piloto"));
	}

	VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos, boolean produccion) {
		this.jdbc = jdbc;
		this.fuenteDatos = fuenteDatos;
		this.produccion = produccion;
	}

	@Override
	public void afterPropertiesSet() {
		verificarMigraciones();
		if (comprobarPermisos()) {
			verificarTriggers();
		}
		else {
			// Fase 1 del despliegue (antes de 02 y 03): cc_app solo lee, así que no hay nada que un trigger deba frenar.
			LOG.warn("La aplicación solo puede leer (faltan los GRANT de 02-permisos-tablas.sql): no se exigen los triggers.");
		}
	}

	/** M2: están todos los triggers de 03-triggers.sql (también los BEFORE UPDATE). Si falta uno, no arranca. */
	public void verificarTriggers() {
		List<String> instalados;
		try {
			String lista = jdbc.queryForObject(SQL_TRIGGERS_INSTALADOS, String.class);
			instalados = lista == null || lista.isBlank() ? List.of() : List.of(lista.split(","));
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudo leer la función cuentasclaras.triggers_instalados() (código "
					+ codigoMySql(e) + "): aplica scripts/mysql/02-permisos-tablas.sql como administrador. Revisa "
					+ "docs/operacion/mysql-usuarios.md.", e);
		}
		Set<String> presentes = instalados.stream().map(t -> t.toLowerCase(java.util.Locale.ROOT))
				.collect(Collectors.toSet());
		List<String> faltan = TRIGGERS_ESPERADOS.stream().filter(t -> !presentes.contains(t)).toList();
		if (!faltan.isEmpty()) {
			throw new IllegalStateException("Faltan triggers en la base: " + String.join(", ", faltan) + ". Sin ellos "
					+ "cc_app podría saltarse las reglas por SQL. Aplica scripts/mysql/03-triggers.sql con cc_migrador. "
					+ "Revisa docs/operacion/mysql-usuarios.md.");
		}
		LOG.info("Triggers verificados: están los {} de 03-triggers.sql.", TRIGGERS_ESPERADOS.size());
	}

	public void verificarPermisos() {
		comprobarPermisos();
	}

	/** @return si la aplicación puede escribir (algún INSERT imposible llegó al trigger: 1644) */
	private boolean comprobarPermisos() {
		boolean escribe = false;
		for (SentenciaProhibida sentencia : SENTENCIAS_PROHIBIDAS) {
			String problema = comprobarDenegada(sentencia);
			if (problema != null) {
				throw new IllegalStateException(problema + " Revisa docs/operacion/mysql-usuarios.md.");
			}
			escribe |= sentencia.codigosAceptados().contains(MYSQL_SIGNAL) && llegoAlTrigger(sentencia);
		}
		LOG.info("Permisos de la bitácora verificados: la aplicación no puede editar ni borrar eventos.");
		LOG.info("Permisos de las cuotas verificados: la aplicación no puede borrarlas ni cambiar su monto.");
		LOG.info("Permisos por columna y triggers de planes, lotes y solicitudes verificados.");
		LOG.info("Permisos y triggers de caja, comprobantes, anulaciones, descuentos y cierres verificados.");
		verificarPasarelaSimulada(escribe);
		LOG.info("Permisos y triggers de pagos en línea, recaudación, conciliación y outbox del OSE verificados.");
		return escribe;
	}

	/**
	 * Prod: la fila 'pasarela_simulada' NO existe y la base rechaza una orden SIMULADA (1644, o 1142 en la fase 1).
	 * Piloto: la fila debe existir (si no, la pasarela simulada no funcionaría).
	 */
	private void verificarPasarelaSimulada(boolean escribe) {
		Integer filas;
		try {
			filas = jdbc.queryForObject(SQL_PASARELA_SIMULADA, Integer.class);
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudo leer configuracion_bd (código " + codigoMySql(e) + "): aplica la "
					+ "migración V13 y scripts/mysql/02-permisos-tablas.sql. Revisa docs/operacion/mysql-usuarios.md.", e);
		}
		boolean habilitada = filas != null && filas > 0;
		if (produccion) {
			if (habilitada) {
				throw new IllegalStateException("La base de PRODUCCIÓN tiene habilitada la pasarela simulada (fila "
						+ "'pasarela_simulada' en configuracion_bd): el DBA debe borrarla. Revisa docs/operacion/mysql-usuarios.md.");
			}
			String problema = comprobarDenegada(ORDEN_SIMULADA);
			if (problema != null) {
				throw new IllegalStateException(problema + " Revisa docs/operacion/mysql-usuarios.md.");
			}
		}
		else if (!habilitada && escribe) {
			throw new IllegalStateException("En el piloto la base no tiene habilitada la pasarela simulada (falta la fila "
					+ "'pasarela_simulada' en configuracion_bd, la registra el DBA). Revisa docs/operacion/mysql-usuarios.md.");
		}
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

	/** Códigos con que la base rechazó cada sentencia en la última comprobación. */
	private final java.util.Map<String, Integer> rechazos = new java.util.HashMap<>();

	private boolean llegoAlTrigger(SentenciaProhibida sentencia) {
		return Integer.valueOf(MYSQL_SIGNAL).equals(rechazos.get(sentencia.sql()));
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
				rechazos.put(sentencia.sql(), codigo);
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
