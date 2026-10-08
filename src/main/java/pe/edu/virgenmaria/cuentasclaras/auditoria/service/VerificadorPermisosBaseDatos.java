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
 *   <li>que los pagos en línea (sprint 4, tanda 1), la recaudación bancaria (tanda 2) y el extracto con su conciliación
 *       automática (tanda 3) tienen sus GRANT por columna, sus tablas de solo inserción y sus triggers;</li>
 *   <li>sprint 5, tanda 3: que los feriados, la semilla secreta del muestreo y el cierre mensual tienen sus GRANT y
 *       sus triggers;</li>
 *   <li>sprint 5: que los mensajes, el enlace con su mensaje y la huella diaria tienen sus GRANT, sus triggers y que en
 *       prod la base no admite la mensajería simulada;</li>
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
					+ "actualizado_en) VALUES (0, 0, 0, 1, NOW(6), 'verificador', NOW(6))", "trg_orden_pago_cuota_registro"),
			// Sprint 4, tanda 2 (recaudación bancaria): el archivo del banco es de solo inserción; los lotes y sus líneas no
			// se borran; el total del lote, el monto de la línea y la línea de un pago no cambian; y cada trigger nuevo
			// rechaza su inserción imposible (un lote que nace APLICADO y una línea de un lote que no existe).
			sinBorrado("archivo_cargado"), sinBorrado("lote_recaudacion"), sinBorrado("linea_recaudacion"),
			soloInsercion("archivo_cargado"),
			columna("UPDATE lote_recaudacion SET total = total WHERE 1 = 0", "lote_recaudacion"),
			columna("UPDATE linea_recaudacion SET monto = monto WHERE 1 = 0", "linea_recaudacion"),
			columna("UPDATE pago SET linea_recaudacion_id = linea_recaudacion_id WHERE 1 = 0", "pago"),
			trigger("INSERT INTO lote_recaudacion (colegio_id, archivo_id, archivo_sha256, sha_vigente, banco, formato, "
					+ "fecha_proceso, desde, hasta, lineas, total, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, "
					+ "REPEAT('0', 64), REPEAT('0', 64), 'BCP', 'verificador', '2000-01-01', '2000-01-01', '2000-01-01', 1, 1, "
					+ "'APLICADO', NOW(6), 'verificador', NOW(6))", "trg_lote_recaudacion_nace"),
			trigger("INSERT INTO linea_recaudacion (colegio_id, lote_id, numero, fecha_pago, codigo, monto, moneda, "
					+ "numero_operacion, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, 1, '2000-01-01', '0', 1, "
					+ "'PEN', '1234', 'PENDIENTE', NOW(6), 'verificador', NOW(6))", "trg_linea_recaudacion_registro"),
			// Sprint 4, tanda 3 (extracto y conciliación): nada se borra; los movimientos del banco y las liquidaciones de la
			// pasarela son de solo inserción; los saldos del extracto, los montos de la partida y el número de la cuenta no
			// cambian; y cada trigger nuevo rechaza su inserción imposible (un extracto que nace CONFIRMADO, un movimiento
			// de un extracto que no existe y una partida de un movimiento que no existe).
			sinBorrado("cuenta_bancaria"), sinBorrado("extracto_bancario"), sinBorrado("movimiento_bancario"),
			sinBorrado("liquidacion_pasarela"), sinBorrado("liquidacion_linea"), sinBorrado("partida_conciliacion"),
			soloInsercion("movimiento_bancario"), soloInsercion("liquidacion_pasarela"), soloInsercion("liquidacion_linea"),
			columna("UPDATE extracto_bancario SET saldo_final = saldo_final WHERE 1 = 0", "extracto_bancario"),
			columna("UPDATE partida_conciliacion SET monto_movimiento = monto_movimiento WHERE 1 = 0",
					"partida_conciliacion"),
			columna("UPDATE cuenta_bancaria SET numero = numero WHERE 1 = 0", "cuenta_bancaria"),
			trigger("INSERT INTO extracto_bancario (colegio_id, cuenta_id, secuencia, secuencia_vigente, archivo_id, "
					+ "archivo_sha256, formato, desde, hasta, saldo_inicial, total_abonos, total_cargos, saldo_final, "
					+ "movimientos, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, 1, 1, 0, REPEAT('0', 64), "
					+ "'verificador', '2000-01-01', '2000-01-01', 0, 0, 0, 0, 0, 'CONFIRMADO', NOW(6), 'verificador', NOW(6))",
					"trg_extracto_bancario_nace"),
			trigger("INSERT INTO movimiento_bancario (colegio_id, extracto_id, cuenta_id, numero, fecha, tipo, monto, "
					+ "descripcion, creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, 1, '2000-01-01', 'ABONO', 1, "
					+ "'verificador', NOW(6), 'verificador', NOW(6))", "trg_movimiento_bancario_registro"),
			trigger("INSERT INTO partida_conciliacion (colegio_id, movimiento_id, movimiento_vigente, objeto_tipo, pago_id, "
					+ "objeto_vigente, regla, monto_movimiento, monto_objeto, diferencia, estado, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 'PAGO', 0, 'PAGO:0', 'EXACTA', 1, 1, 0, 'PROPUESTA', NOW(6), "
					+ "'verificador', NOW(6))", "trg_partida_conciliacion_registro"),
			// Correcciones del sprint 4 (V16): la muestra fija de la confirmación a ciegas y la semilla secreta del
			// muestreo no se reescriben (S4-A1 y S4-A2).
			columna("UPDATE extracto_bancario SET muestra = muestra WHERE 1 = 0", "extracto_bancario"),
			columna("UPDATE extracto_bancario SET semilla_muestreo = semilla_muestreo WHERE 1 = 0", "extracto_bancario"),
			columna("UPDATE lote_recaudacion SET muestra = muestra WHERE 1 = 0", "lote_recaudacion"),
			// S4-A3: el reembolso de un pago en línea (por la API de la pasarela) es de solo inserción y su trigger rechaza
			// uno que no corresponde a una devolución aprobada.
			sinBorrado("reembolso_pasarela"), soloInsercion("reembolso_pasarela"),
			trigger("INSERT INTO reembolso_pasarela (colegio_id, anulacion_pago_id, pago_id, cargo_id, reembolso_id, monto, "
					+ "fecha, creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, 'verificador', 'verificador', 1, "
					+ "'2000-01-01', NOW(6), 'verificador', NOW(6))", "trg_reembolso_pasarela_registro"),
			// S4-A4: la cuenta de destino y el objeto de una partida no se reescriben.
			columna("UPDATE partida_conciliacion SET linea_recaudacion_id = linea_recaudacion_id WHERE 1 = 0",
					"partida_conciliacion"),
			// S4-M2: el enlace de activación no se borra ni cambia su hash, su usuario ni su vencimiento.
			sinBorrado("enlace_activacion"),
			columna("UPDATE enlace_activacion SET hash_token = hash_token WHERE 1 = 0", "enlace_activacion"),
			columna("UPDATE enlace_activacion SET vence_en = vence_en WHERE 1 = 0", "enlace_activacion"),
			columna("UPDATE enlace_activacion SET usuario_id = usuario_id WHERE 1 = 0", "enlace_activacion"),
			// Sprint 5, tanda 1 (V17): los mensajes no se borran ni cambian su destino, sus parámetros ni su destinatario;
			// el enlace no cambia su mensaje; la huella diaria es de solo inserción; y cada trigger nuevo rechaza su
			// inserción imposible (un mensaje que nace ENVIADO, un enlace sin su mensaje y una huella que no coincide).
			sinBorrado("mensaje"), sinBorrado("huella_bitacora"), soloInsercion("huella_bitacora"),
			columna("UPDATE mensaje SET destino = destino WHERE 1 = 0", "mensaje"),
			columna("UPDATE mensaje SET parametros = parametros WHERE 1 = 0", "mensaje"),
			columna("UPDATE mensaje SET apoderado_id = apoderado_id WHERE 1 = 0", "mensaje"),
			columna("UPDATE enlace_activacion SET mensaje_id = mensaje_id WHERE 1 = 0", "enlace_activacion"),
			trigger("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, "
					+ "parametros, estado, creado_en, creado_por, actualizado_en) VALUES (0, 'verificador', 'HUELLA_BITACORA', "
					+ "'CORREO', 'USUARIO', 0, 'x@y.pe', 'verificador', '', 'ENVIADO', NOW(6), 'verificador', NOW(6))",
					"trg_mensaje_nace"),
			trigger("INSERT INTO enlace_activacion (colegio_id, usuario_id, hash_token, vence_en, mensaje_id, proposito, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, 0, REPEAT('0', 64), NOW(6) + INTERVAL 1 HOUR, 0, "
					+ "'PERSONAL', NOW(6), 'verificador', NOW(6))", "trg_enlace_activacion_nace"),
			trigger("INSERT INTO huella_bitacora (colegio_id, fecha, secuencia, codigo, eventos_del_dia, creado_en, "
					+ "creado_por, actualizado_en) VALUES (0, '2000-01-01', 1, REPEAT('0', 16), 0, NOW(6), "
					+ "'sistema.auditoria', NOW(6))", "trg_huella_bitacora_registro"),
			trigger("INSERT INTO apoderado (colegio_id, familia_id, tipo_documento, numero_documento, apellido_paterno, "
					+ "nombres, parentesco, nombre_busqueda, activo, contacto_solicitud_id, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 'DNI', '00000000', 'verificador', 'verificador', 'MADRE', "
					+ "'verificador', TRUE, 1, NOW(6), 'verificador', NOW(6))", "trg_apoderado_nace"),
			// Sprint 5, tanda 2 (V18): la renovación y el aviso de la familia no se borran ni cambian su alumno ni su
			// texto; la renovación nace PROPUESTA y la matrícula ACTIVA solo en el año en curso.
			sinBorrado("renovacion_matricula"), sinBorrado("aviso_familia"),
			columna("UPDATE renovacion_matricula SET alumno_id = alumno_id WHERE 1 = 0", "renovacion_matricula"),
			columna("UPDATE aviso_familia SET texto = texto WHERE 1 = 0", "aviso_familia"),
			trigger("INSERT INTO renovacion_matricula (colegio_id, anio_destino_id, alumno_id, familia_id, "
					+ "matricula_origen_id, grado_destino, seccion_destino_id, vence_en, estado, creado_en, creado_por, "
					+ "actualizado_en) VALUES (0, 0, 0, 0, 0, 'PRIMARIA_1', 0, '2000-01-01', 'CONFIRMADA', NOW(6), "
					+ "'verificador', NOW(6))", "trg_renovacion_matricula_nace"),
			trigger("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, 0, '2000-01-01', 'ACTIVA', NOW(6), "
					+ "'verificador', NOW(6))", "trg_matricula_nace"),
			// Sprint 5, tanda 3 (V19): los feriados, la semilla y el cierre mensual no se borran; la semilla es de solo
			// inserción; la fecha del feriado y los totales calculados del cierre no cambian; y cada trigger nuevo rechaza
			// su inserción imposible (un feriado en el pasado y un cierre que nace CUADRADO).
			sinBorrado("feriado"), sinBorrado("semilla_muestreo"), sinBorrado("cierre_mensual_banco"),
			soloInsercion("semilla_muestreo"),
			columna("UPDATE feriado SET fecha = fecha WHERE 1 = 0", "feriado"),
			columna("UPDATE cierre_mensual_banco SET total_abonos = total_abonos WHERE 1 = 0", "cierre_mensual_banco"),
			columna("UPDATE cierre_mensual_banco SET saldo_final = saldo_final WHERE 1 = 0", "cierre_mensual_banco"),
			trigger("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, creado_en, creado_por, actualizado_en) "
					+ "VALUES (0, '2000-01-01', 'verificador', TRUE, NOW(6), 'verificador', NOW(6))", "trg_feriado_registro"),
			trigger("INSERT INTO cierre_mensual_banco (colegio_id, cuenta_id, anio, mes, total_abonos, total_cargos, "
					+ "saldo_final, estado, creado_en, creado_por, actualizado_en) VALUES (0, 0, 2026, 1, 0, 0, 0, 'CUADRADO', "
					+ "NOW(6), 'sistema.conciliacion', NOW(6))", "trg_cierre_mensual_banco_nace"),
			// Correcciones del sprint 5 (V20): la verificación del contacto y la huella de la hora no se borran; la huella
			// de la hora es de solo inserción; la verificación no cambia su contacto ni su hash; y cada trigger nuevo
			// rechaza su inserción imposible (una verificación sin su mensaje, una huella que no coincide, un apoderado
			// que nace verificado y un feriado que nace aprobado).
			sinBorrado("verificacion_contacto"), sinBorrado("huella_hora"), soloInsercion("huella_hora"),
			columna("UPDATE verificacion_contacto SET contacto = contacto WHERE 1 = 0", "verificacion_contacto"),
			columna("UPDATE verificacion_contacto SET hash_token = hash_token WHERE 1 = 0", "verificacion_contacto"),
			trigger("INSERT INTO verificacion_contacto (colegio_id, apoderado_id, canal, contacto, hash_token, mensaje_id, "
					+ "vence_en, creado_en, creado_por, actualizado_en) VALUES (0, 0, 'CORREO', 'x@y.pe', REPEAT('0', 64), 0, "
					+ "NOW(6) + INTERVAL 1 HOUR, NOW(6), 'sistema.mensajeria', NOW(6))", "trg_verificacion_contacto_nace"),
			trigger("INSERT INTO huella_hora (colegio_id, momento, secuencia, codigo, creado_en, creado_por, actualizado_en) "
					+ "VALUES (0, NOW(6), 1, REPEAT('0', 16), NOW(6), 'sistema.auditoria', NOW(6))", "trg_huella_hora_registro"),
			trigger("INSERT INTO apoderado (colegio_id, familia_id, tipo_documento, numero_documento, apellido_paterno, "
					+ "nombres, parentesco, nombre_busqueda, activo, telefono_whatsapp, telefono_verificado, creado_en, "
					+ "creado_por, actualizado_en) VALUES (0, 0, 'DNI', '00000000', 'verificador', 'verificador', 'MADRE', "
					+ "'verificador', TRUE, '+51999999999', '+51999999999', NOW(6), 'verificador', NOW(6))",
					"trg_apoderado_nace"),
			trigger("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, pendiente, aprobado_por, aprobado_en, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, '2999-01-04', 'verificador', TRUE, FALSE, 'b', NOW(6), "
					+ "NOW(6), 'verificador', NOW(6))", "trg_feriado_registro"));

	/** Sprint 5: la mensajería simulada solo existe en una base habilitada por el DBA (nunca en prod). */
	static final String SQL_MENSAJERIA_SIMULADA =
			"SELECT COUNT(*) FROM configuracion_bd WHERE clave = 'mensajeria_simulada'";

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
			"trg_apoderado_facturacion", "trg_orden_pago_nace", "trg_orden_pago_cuota_registro", "trg_orden_pago_estado",
			"trg_lote_recaudacion_nace", "trg_lote_recaudacion_estado", "trg_linea_recaudacion_registro",
			"trg_linea_recaudacion_estado", "trg_extracto_bancario_nace", "trg_extracto_bancario_estado",
			"trg_movimiento_bancario_registro", "trg_partida_conciliacion_registro", "trg_partida_conciliacion_estado",
			"trg_reembolso_pasarela_registro", "trg_mensaje_nace", "trg_mensaje_envio", "trg_enlace_activacion_nace",
			"trg_enlace_activacion_uso", "trg_huella_bitacora_registro", "trg_renovacion_matricula_nace",
			"trg_renovacion_matricula_estado", "trg_matricula_nace", "trg_matricula_estado", "trg_aviso_familia_estado",
			"trg_feriado_registro", "trg_feriado_anulacion", "trg_cierre_mensual_banco_nace",
			"trg_cierre_mensual_banco_estado", "trg_verificacion_contacto_nace", "trg_verificacion_contacto_uso",
			"trg_huella_hora_registro");

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
		verificarMensajeriaSimulada(escribe);
		LOG.info("Permisos y triggers de mensajería, acceso directo al titular y huella diaria verificados.");
		LOG.info("Permisos y triggers de la renovación de matrícula, la matrícula reservada y los avisos de las familias "
				+ "verificados.");
		LOG.info("Permisos y triggers de feriados, semilla del muestreo y cierre bancario mensual verificados.");
		LOG.info("Permisos y triggers de la verificación de contactos, la huella por hora y los feriados aprobados por "
				+ "otra persona verificados.");
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

	/**
	 * Sprint 5 (G11): en prod la fila 'mensajeria_simulada' NO existe (trg_mensaje_envio rechaza un mensaje simulado);
	 * en el piloto debe existir (si no, la mensajería simulada no podría marcar nada como enviado).
	 */
	private void verificarMensajeriaSimulada(boolean escribe) {
		Integer filas;
		try {
			filas = jdbc.queryForObject(SQL_MENSAJERIA_SIMULADA, Integer.class);
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudo leer configuracion_bd (código " + codigoMySql(e) + "). Revisa "
					+ "docs/operacion/mysql-usuarios.md.", e);
		}
		boolean habilitada = filas != null && filas > 0;
		if (produccion && habilitada) {
			throw new IllegalStateException("La base de PRODUCCIÓN tiene habilitada la mensajería simulada (fila "
					+ "'mensajeria_simulada' en configuracion_bd): las familias no recibirían sus avisos. El DBA debe borrarla. "
					+ "Revisa docs/operacion/mysql-usuarios.md.");
		}
		if (!produccion && !habilitada && escribe) {
			throw new IllegalStateException("En el piloto la base no tiene habilitada la mensajería simulada (falta la fila "
					+ "'mensajeria_simulada' en configuracion_bd, la registra el DBA). Revisa docs/operacion/mysql-usuarios.md.");
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
