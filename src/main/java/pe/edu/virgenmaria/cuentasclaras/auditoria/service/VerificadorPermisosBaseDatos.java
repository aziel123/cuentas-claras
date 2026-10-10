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
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

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
 *   <li>sprint 6, tanda 2: que la foto del resumen diario es de solo inserción y su trigger la compara con los libros, que
 *       el resumen y las alertas los crea solo sistema.panel, y que está trg_usuario_contacto (BEFORE UPDATE: se comprueba
 *       por su presencia en {@link #TRIGGERS_ESPERADOS});</li>
 *   <li>sprint 6, tanda 3: que la llamada de control es de solo inserción y su trigger exige Promotoría o Dirección;</li>
 *   <li>que no faltan migraciones (en producción la aplicación no migra: se corre {@code migrar} antes);</li>
 *   <li>sprint 7, tanda 2 (sección 6.5): que la aplicación usa DOS usuarios de base distintos, {@code cc_app} y
 *       {@code cc_sistema} (el nombre del segundo es fijo: los triggers lo reconocen); que {@code cc_app} no crea cuentas,
 *       no cambia claves ni roles, no abre sesiones, no firma como sistema y no escribe la semilla, la muestra, la foto ni el
 *       envío al OSE o el estado de un mensaje (1142 o 1143); que los triggers de identidad, sesiones, firmas y semilla
 *       rechazan sus inserciones imposibles (1644) con las dos conexiones; que la HUELLA de cada trigger y función de la
 *       base es la de {@code 03-triggers.sql} y {@code 02-permisos-tablas.sql} del jar ({@link HuellasObjetosBd}: falta,
 *       sobra o difiere y no arranca); que ninguna conexión tiene un privilegio que {@code 02} no da
 *       ({@link PermisosEsperados}); y, en prod, que la conexión va cifrada (TLS) salvo que se apague a propósito.</li>
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
					+ "NOW(6), 'verificador', NOW(6))", "trg_feriado_registro"),
			// Sprint 6, tanda 2 (V21): la foto del resumen diario no se borra ni se edita (ni sistema.panel la corrige);
			// una foto con cifras que no son las de los libros (colegio 0: no tiene pagos) falla en su trigger; y el
			// resumen y las alertas solo los crea sistema.panel: con un destinatario imposible, la versión de
			// trg_mensaje_nace del sprint 6 responde 1644 antes que el CHECK (la anterior dejaría pasar al CHECK: 3819).
			sinBorrado("resumen_diario"), soloInsercion("resumen_diario"),
			trigger(VerificadorPermisosBaseDatos.RESUMEN_IMPOSIBLE, "trg_resumen_diario_registro"),
			trigger("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, destino, plantilla, parametros, "
					+ "estado, creado_en, creado_por, actualizado_en) VALUES (0, 'verificador-panel', 'ALERTA_PROMOTORIA', "
					+ "'CORREO', 'X', 'x@y.pe', 'verificador', '', 'PENDIENTE', NOW(6), 'verificador', NOW(6))",
						"trg_mensaje_nace (versión del sprint 6)"),
			// Sprint 6, tanda 3 (V22): la llamada de control no se borra ni se edita; una llamada del colegio 0 (nadie de
			// Promotoría ni Dirección firma como «verificador») falla en su trigger.
			sinBorrado("llamada_control"), soloInsercion("llamada_control"),
			trigger(VerificadorPermisosBaseDatos.LLAMADA_IMPOSIBLE, "trg_llamada_control_registro"),
			// Correcciones del sprint 6 (V23): la muestra congelada y la delegación de las llamadas no se borran ni se
			// editan; una fila del colegio 0 (nadie de Promotoría ni Dirección firma como «verificador») falla en su
			// trigger; y configuracion_colegio solo la escribe el DBA (QA-S6-6).
			sinBorrado("muestra_llamada"), soloInsercion("muestra_llamada"),
			sinBorrado("delegacion_llamada"), soloInsercion("delegacion_llamada"),
			trigger(VerificadorPermisosBaseDatos.MUESTRA_IMPOSIBLE, "trg_muestra_llamada_registro"),
			trigger(VerificadorPermisosBaseDatos.DELEGACION_IMPOSIBLE, "trg_delegacion_llamada_registro"),
			sinBorrado("configuracion_colegio"),
			new SentenciaProhibida("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en) VALUES (0, "
					+ "'resumen_correo_externo', 'x@y.pe', NOW(6))", Set.of(MYSQL_COMANDO_DENEGADO),
					"la aplicación podría poner el correo externo que recibe el resumen de un colegio."),
			new SentenciaProhibida("UPDATE configuracion_colegio SET valor = valor WHERE 1 = 0",
					Set.of(MYSQL_COMANDO_DENEGADO), "la aplicación podría cambiar el correo externo de un colegio."),
			// Sprint 7, tanda 1 (V24): el registro de respaldos lo escribe SOLO cc_respaldo. La aplicación no registra un
			// respaldo falso (1142 al insertar; trg_respaldo_registro, que exige cc_respaldo, se comprueba por su presencia
			// en TRIGGERS_ESPERADOS) ni edita ni borra los que hay.
			new SentenciaProhibida(VerificadorPermisosBaseDatos.RESPALDO_IMPOSIBLE, Set.of(MYSQL_COMANDO_DENEGADO),
					"la aplicación podría registrar un respaldo que no existe (el registro lo escribe solo cc_respaldo)."),
			sinBorrado("respaldo"), soloInsercion("respaldo"),
			// Sprint 7, tanda 2 (H1, E4, E5, E10, E11, E14 y E15): la identidad, las sesiones y las escrituras de los
			// procesos son de cc_sistema. cc_app no crea cuentas, no cambia claves ni roles, no abre sesiones, no planta
			// la semilla, la muestra ni la foto, no marca el envío al OSE ni el estado de un mensaje (1142).
			soloSistema("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, clave_hash, creado_en, "
					+ "creado_por, actualizado_en) VALUES (0, 'verificador', 'verificador', 'x', NOW(6), 'verificador', "
					+ "NOW(6))", "la aplicación podría crear cuentas (por ejemplo, una de Promotoría con una clave conocida)."),
			soloSistema("UPDATE usuario SET clave_hash = clave_hash WHERE 1 = 0",
					"la aplicación podría cambiar la clave de cualquier persona y entrar como ella."),
			soloSistema("INSERT INTO usuario_rol (usuario_id, rol) VALUES (0, 'PROMOTOR')",
					"la aplicación podría darse el rol de Promotoría."),
			soloSistema("DELETE FROM usuario_rol WHERE 1 = 0", "la aplicación podría quitar roles."),
			soloSistema(VerificadorPermisosBaseDatos.SESION_IMPOSIBLE,
					"la aplicación podría abrir sesiones y firmar aprobaciones a nombre de otra persona."),
			soloSistema("UPDATE sesion_usuario SET cerrada_en = cerrada_en WHERE 1 = 0",
					"la aplicación podría cerrar o reabrir sesiones."),
			soloSistema(VerificadorPermisosBaseDatos.SEMILLA_IMPOSIBLE, "la aplicación podría plantar la semilla del muestreo."),
			soloSistema(VerificadorPermisosBaseDatos.MUESTRA_IMPOSIBLE, "la aplicación podría fijar la muestra de la semana."),
			soloSistema(VerificadorPermisosBaseDatos.RESUMEN_IMPOSIBLE, "la aplicación podría plantar la foto del resumen."),
			soloSistema("UPDATE comprobante SET estado_envio = estado_envio WHERE 1 = 0",
					"la aplicación podría marcar un comprobante como ACEPTADO por el OSE."),
			soloSistema("UPDATE mensaje SET estado = estado WHERE 1 = 0",
					"la aplicación podría marcar un aviso como ENTREGADO."),
			sinBorrado("sesion_usuario"), sinBorrado("firma_operacion"), soloInsercion("firma_operacion"),
			// Sprint 7, tanda 3 (V26, Ley 29733): quién vio datos personales es de solo inserción (nadie borra ni cambia
			// el rastro de una consulta: 1142) y el derecho de un pedido sobre datos personales no cambia (1143).
			sinBorrado("acceso_dato_personal"), soloInsercion("acceso_dato_personal"),
			columna("UPDATE aviso_familia SET derecho = derecho WHERE 1 = 0", "aviso_familia"),
			// E1: con cc_app nadie firma como sistema (un evento de la bitácora o un pago de sistema.*: 1644; sin el
			// trigger, el evento llega al NOT NULL de hash, 1048).
			trigger(VerificadorPermisosBaseDatos.EVENTO_SISTEMA_IMPOSIBLE, "trg_evento_auditoria_actor"),
			trigger(VerificadorPermisosBaseDatos.PAGO_SISTEMA_IMPOSIBLE, "trg_pago_registro (versión del sprint 7)"),
			// Correcciones del sprint 7 (S7-A1): una solicitud nace PENDIENTE (con cc_app no se inserta una ya APROBADA a
			// nombre de la directora: 1644; sin el trigger, la FK del colegio 0: 1452).
			trigger(VerificadorPermisosBaseDatos.SOLICITUD_APROBADA_IMPOSIBLE, "trg_solicitud_cambio_nace"),
			// Correcciones del sprint 7 (QA-S7-1): la resolución de una alerta de respaldo la hace Promotoría con su firma
			// (1644; sin el trigger, la FK del respaldo 0: 1452) y no se edita ni se borra (1142).
			trigger(VerificadorPermisosBaseDatos.RESOLUCION_IMPOSIBLE, "trg_resolucion_respaldo_registro"),
			sinBorrado("resolucion_respaldo"),
			new SentenciaProhibida("UPDATE resolucion_respaldo SET motivo = motivo WHERE 1 = 0",
					Set.of(MYSQL_COMANDO_DENEGADO, MYSQL_COLUMNA_DENEGADA), "la resolución de una alerta de respaldo se podría reescribir."),
			// Correcciones del sprint 7: la marca de la primera Dirección de un colegio la escribe solo cc_sistema (1142).
			soloSistema(VerificadorPermisosBaseDatos.PRIMERA_DIRECCION_IMPOSIBLE,
					"la aplicación podría gastar o fabricar la excepción de la primera Dirección de un colegio."));

	/** Correcciones del sprint 7 (S7-A1): una solicitud ya APROBADA del colegio 0 (la rechaza trg_solicitud_cambio_nace). */
	static final String SOLICITUD_APROBADA_IMPOSIBLE = "INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, "
			+ "resumen, datos, motivo, estado, pendiente, solicitado_por, resuelto_por, resuelto_en, creado_en, creado_por, "
			+ "actualizado_en) VALUES (0, 'ANULACION_PAGO', 'pago', 0, 'verificador', '{}', 'verificador de permisos', "
			+ "'APROBADA', NULL, 'verificador', 'verificador.otro', NOW(6), NOW(6), 'verificador', NOW(6))";

	/** Correcciones del sprint 7 (QA-S7-1): la resolución del respaldo 0 (la rechaza trg_resolucion_respaldo_registro). */
	static final String RESOLUCION_IMPOSIBLE = "INSERT INTO resolucion_respaldo (respaldo_id, colegio_id, usuario_id, motivo, "
			+ "creado_en, creado_por) VALUES (0, 0, 0, 'verificador de permisos', NOW(6), 'verificador')";

	/** Correcciones del sprint 7: la primera Dirección del colegio 0 (cc_app no tiene INSERT: 1142). */
	static final String PRIMERA_DIRECCION_IMPOSIBLE = "INSERT INTO primera_direccion (colegio_id, usuario_id, creado_en, "
			+ "creado_por, actualizado_en) VALUES (0, 0, NOW(6), 'verificador', NOW(6))";

	/** Sprint 7, tanda 2: una sesión de la cuenta 0 (con cc_app, 1142; con cc_sistema, la rechaza su trigger: 1644). */
	static final String SESION_IMPOSIBLE = "INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, abierta_en, "
			+ "vence_en, creado_en, creado_por, actualizado_en) VALUES (0, 0, REPEAT('0', 64), NOW(6), NOW(6) + INTERVAL 1 "
			+ "HOUR, NOW(6), 'verificador', NOW(6))";

	/** Sprint 7, tanda 2: la semilla del colegio 0 del año 2000 (con cc_sistema, la rechaza su trigger: 1644). */
	static final String SEMILLA_IMPOSIBLE = "INSERT INTO semilla_muestreo (colegio_id, ambito, fecha, semilla, creado_en, "
			+ "creado_por, actualizado_en) VALUES (0, 'CAJA', '2000-01-03', 1, NOW(6), 'sistema.muestreo', NOW(6))";

	/** Sprint 7, tanda 2: una firma con un secreto que no es de ninguna sesión (1644; sin el trigger, la FK: 1452). */
	static final String FIRMA_IMPOSIBLE = "INSERT INTO firma_operacion (colegio_id, sesion_id, usuario_id, clave, token, "
			+ "creado_en, creado_por, actualizado_en) VALUES (0, 0, 0, 'verificador:0', REPEAT('0', 64), NOW(6), "
			+ "'verificador', NOW(6))";

	/** Sprint 7, tanda 2 (E1): un evento de un actor de sistema escrito por cc_app, sin hash (1644; sin el trigger, 1048). */
	static final String EVENTO_SISTEMA_IMPOSIBLE = "INSERT INTO evento_auditoria (secuencia, ocurrido_en, nombre_usuario, "
			+ "accion, hash) VALUES (0, NOW(6), 'sistema.verificador', 'VERIFICADOR', NULL)";

	/** Sprint 7, tanda 2 (E1): un pago de sistema.pasarela del colegio 0 escrito por cc_app (1644). */
	static final String PAGO_SISTEMA_IMPOSIBLE = "INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, "
			+ "comprobante_id, medio, total, recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, "
			+ "actualizado_en) VALUES (0, 0, 0, 'sistema.pasarela', '2000-01-01', 0, 'YAPE', 1, 1, 0, 'PASARELA', "
			+ "'verificador-sistema', 'VIGENTE', NOW(6), 'sistema.pasarela', NOW(6))";

	/**
	 * Sprint 7, tanda 2: lo que la conexión de cc_sistema debe ver rechazado: la bitácora y los libros (1142, como cc_app) y
	 * las inserciones imposibles de identidad, sesiones, firmas y semilla, que llegan a su trigger (1644; 1142 en la fase 1,
	 * antes de 02).
	 */
	static final List<SentenciaProhibida> SENTENCIAS_SISTEMA = List.of(
			new SentenciaProhibida("UPDATE evento_auditoria SET ip = ip WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"cc_sistema podría editar la bitácora."),
			new SentenciaProhibida("DELETE FROM evento_auditoria WHERE 1 = 0", Set.of(MYSQL_COMANDO_DENEGADO),
					"cc_sistema podría borrar la bitácora."),
			sinBorrado("pago"), sinBorrado("cuota"), sinBorrado("sesion_usuario"), sinBorrado("firma_operacion"),
			soloInsercion("firma_operacion"), soloInsercion("semilla_muestreo"), soloInsercion("muestra_llamada"),
			sinBorrado("acceso_dato_personal"), soloInsercion("acceso_dato_personal"),
			columna("UPDATE cuota SET monto = monto WHERE 1 = 0", "cuota"),
			columna("UPDATE sesion_usuario SET hash_token = hash_token WHERE 1 = 0", "sesion_usuario"),
			trigger(VerificadorPermisosBaseDatos.FIRMA_IMPOSIBLE, "trg_firma_operacion_nace"),
			trigger(VerificadorPermisosBaseDatos.SESION_IMPOSIBLE, "trg_sesion_usuario_nace"),
			trigger(VerificadorPermisosBaseDatos.SEMILLA_IMPOSIBLE, "trg_semilla_muestreo_registro"),
			trigger("INSERT INTO usuario (colegio_id, nombre_usuario, nombre_completo, clave_hash, activo, debe_cambiar_clave, "
					+ "creado_en, creado_por, actualizado_en) VALUES (0, 'verificador', 'verificador', 'x', TRUE, FALSE, "
					+ "NOW(6), 'verificador', NOW(6))", "trg_usuario_nace"),
			trigger("INSERT INTO usuario_rol (usuario_id, rol) VALUES (0, 'PROMOTOR')", "trg_usuario_rol_alta"),
			trigger(VerificadorPermisosBaseDatos.MUESTRA_IMPOSIBLE, "trg_muestra_llamada_registro (versión del sprint 7)"),
			// Correcciones del sprint 7: la primera Dirección y la resolución de un respaldo no se editan ni se borran.
			sinBorrado("primera_direccion"), soloInsercion("primera_direccion"), sinBorrado("resolucion_respaldo"),
			trigger(VerificadorPermisosBaseDatos.SOLICITUD_APROBADA_IMPOSIBLE, "trg_solicitud_cambio_nace"));

	/** 1142 (o 1143): solo cc_sistema la escribe. */
	private static SentenciaProhibida soloSistema(String sql, String riesgo) {
		return new SentenciaProhibida(sql, Set.of(MYSQL_COMANDO_DENEGADO, MYSQL_COLUMNA_DENEGADA), riesgo
				+ " Es una escritura exclusiva de cc_sistema: revisa 02-permisos-tablas.sql.");
	}

	/** Sprint 7, tanda 1: un respaldo del año 2000 registrado por la aplicación (cc_app no tiene INSERT: 1142). */
	static final String RESPALDO_IMPOSIBLE = "INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, "
			+ "secuencia_antes, hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, creado_en, "
			+ "creado_por) VALUES ('2000-01-01', '2000-01-01', 'cc-20000101-000000.sql.gz.age', REPEAT('0', 64), 1, '0', 0, "
			+ "REPEAT('0', 64), 0, REPEAT('0', 64), '{}', 'verificador', 'PRIMERO', NOW(6), 'cc_respaldo')";

	/** Sprint 7, tanda 1: el destino simulado de los respaldos solo existe en una base habilitada por el DBA. */
	static final String SQL_RESPALDO_SIMULADO = "SELECT COUNT(*) FROM configuracion_bd WHERE clave = 'respaldo_simulado'";

	/** Correcciones del sprint 6 (V23): una familia en la muestra del colegio 0 fijada por quien no es del personal. */
	static final String MUESTRA_IMPOSIBLE = "INSERT INTO muestra_llamada (colegio_id, semana, familia_id, motivo, creado_en, "
			+ "creado_por, actualizado_en) VALUES (0, '2000-01-03', 0, 'EFECTIVO', NOW(6), 'verificador', NOW(6))";

	/** Correcciones del sprint 6 (V23): una delegación del colegio 0 por quien no es de Promotoría. */
	static final String DELEGACION_IMPOSIBLE = "INSERT INTO delegacion_llamada (colegio_id, semana, creado_en, creado_por, "
			+ "actualizado_en) VALUES (0, '2000-01-03', NOW(6), 'verificador', NOW(6))";

	/** Sprint 6, tanda 3: una llamada de control del colegio 0 firmada por quien no es de Promotoría ni Dirección. */
	static final String LLAMADA_IMPOSIBLE = "INSERT INTO llamada_control (colegio_id, semana, familia_id, resultado, "
			+ "creado_en, creado_por, actualizado_en) VALUES (0, '2000-01-03', 0, 'CONFIRMA', NOW(6), 'verificador', NOW(6))";

	/**
	 * Una foto del colegio 0 de un día pasado, con todo en cero (las cifras de sus libros vacíos). Correcciones del sprint 6
	 * (S6-M1): la versión de V23 de trg_resumen_diario_registro la rechaza (1644) porque no es de hoy; la versión anterior la
	 * dejaba pasar hasta la FK del colegio (1452): así prod no arranca con el trigger viejo.
	 */
	static final String RESUMEN_IMPOSIBLE = "INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, "
			+ "pagos_cantidad, cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, cajas_sin_cerrar, "
			+ "cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, avisos_entregados, creado_en, "
			+ "creado_por, actualizado_en) VALUES (0, '2000-01-01', '2000-01-01 19:30:00', 0, 0, 0, 0, 0, 0, 0, 0, 0, "
			+ "0, 0, 0, 0, NOW(6), 'sistema.panel', NOW(6))";

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
			"trg_reembolso_registro", "trg_solicitud_cambio_nace", "trg_solicitud_cambio_resuelta", "trg_comprobante_envio", "trg_apoderado_nace",
			"trg_apoderado_facturacion", "trg_orden_pago_nace", "trg_orden_pago_cuota_registro", "trg_orden_pago_estado",
			"trg_lote_recaudacion_nace", "trg_lote_recaudacion_estado", "trg_linea_recaudacion_registro",
			"trg_linea_recaudacion_estado", "trg_extracto_bancario_nace", "trg_extracto_bancario_estado",
			"trg_movimiento_bancario_registro", "trg_partida_conciliacion_registro", "trg_partida_conciliacion_estado",
			"trg_reembolso_pasarela_registro", "trg_mensaje_nace", "trg_mensaje_envio", "trg_enlace_activacion_nace",
			"trg_enlace_activacion_uso", "trg_huella_bitacora_registro", "trg_renovacion_matricula_nace",
			"trg_renovacion_matricula_estado", "trg_matricula_nace", "trg_matricula_estado", "trg_aviso_familia_estado",
			"trg_feriado_registro", "trg_feriado_anulacion", "trg_cierre_mensual_banco_nace",
			"trg_cierre_mensual_banco_estado", "trg_verificacion_contacto_nace", "trg_verificacion_contacto_uso",
			"trg_huella_hora_registro", "trg_resumen_diario_registro", "trg_usuario_contacto",
			"trg_llamada_control_registro", "trg_muestra_llamada_registro", "trg_delegacion_llamada_registro",
			"trg_respaldo_registro", "trg_resolucion_respaldo_registro", "trg_firma_operacion_nace", "trg_sesion_usuario_nace", "trg_sesion_usuario_cierre",
			"trg_evento_auditoria_actor", "trg_semilla_muestreo_registro", "trg_usuario_nace", "trg_usuario_identidad",
			"trg_usuario_rol_alta", "trg_usuario_rol_baja");

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

	/** Sprint 7, tanda 2: la conexión de cc_sistema ({@code null} si la fuente de datos no tiene dos usuarios). */
	private final JdbcTemplate sistema;

	private final DataSource fuenteDatos;

	/** {@code true} en prod; {@code false} en piloto (allí la pasarela simulada debe estar habilitada por el DBA). */
	private final boolean produccion;

	/** Sprint 7, tanda 2 (H11): en prod la conexión va cifrada salvo {@code cuentasclaras.basedatos.exigir-tls: false}. */
	private final boolean exigirTls;

	/** Como en producción, con las dos conexiones de la fuente de datos de la aplicación y sin exigir TLS (pruebas). */
	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos) {
		this(jdbc, sistemaDe(fuenteDatos), fuenteDatos, true, false);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos,
			org.springframework.core.env.Environment entorno) {
		this(jdbc, sistemaDe(fuenteDatos), fuenteDatos, !Arrays.asList(entorno.getActiveProfiles()).contains("piloto"),
				Arrays.asList(entorno.getActiveProfiles()).contains("prod")
						&& entorno.getProperty("cuentasclaras.basedatos.exigir-tls", Boolean.class, true));
	}

	VerificadorPermisosBaseDatos(JdbcTemplate jdbc, DataSource fuenteDatos, boolean produccion) {
		this(jdbc, sistemaDe(fuenteDatos), fuenteDatos, produccion, false);
	}

	VerificadorPermisosBaseDatos(JdbcTemplate jdbc, JdbcTemplate sistema, DataSource fuenteDatos, boolean produccion,
			boolean exigirTls) {
		this.jdbc = jdbc;
		this.sistema = sistema;
		this.fuenteDatos = fuenteDatos;
		this.produccion = produccion;
		this.exigirTls = exigirTls;
	}

	/** La conexión de cc_sistema de la fuente enrutada (sprint 7, tanda 2), si tiene un usuario aparte. */
	private static JdbcTemplate sistemaDe(DataSource fuenteDatos) {
		return fuenteDatos instanceof FuenteDatosEnrutada enrutada && enrutada.separadas()
				? new JdbcTemplate(enrutada.sistema()) : null;
	}

	@Override
	public void afterPropertiesSet() {
		verificarMigraciones();
		// Primero las huellas (si 02 ya creó la función): un trigger debilitado o una función reemplazada se nombran tal
		// cual, antes de que una inserción imposible falle por otra razón.
		boolean huellas = verificarHuellasSiEstan();
		boolean escribe = comprobarPermisos();
		verificarUsuarios();
		boolean sistemaEscribe = comprobarSistema();
		if (escribe || sistemaEscribe) {
			verificarTriggers();
			if (!huellas) {
				verificarHuellas();
			}
		}
		else {
			// Fase 1 del despliegue (antes de 02 y 03): las conexiones solo leen: no hay nada que un trigger deba frenar.
			LOG.warn("La aplicación solo puede leer (faltan los GRANT de 02-permisos-tablas.sql): no se exigen los triggers.");
		}
		verificarPrivilegios();
		verificarTls();
		avisarConfiguracionVieja();
	}

	/**
	 * Las huellas, si la función existe y la aplicación puede ejecutarla (después de 02). En la fase 1 (sin 02) no hay
	 * función: se salta, y si después resulta que la aplicación escribe, {@link #verificarHuellas()} la exige.
	 *
	 * @return si se verificaron
	 */
	private boolean verificarHuellasSiEstan() {
		String json;
		try {
			json = jdbc.queryForObject(SQL_HUELLAS, String.class);
		}
		catch (DataAccessException e) {
			// Sin la función (1305) o sin permiso para ejecutarla (1370, 1142): la fase 1. Si la aplicación escribe, se
			// exige después con verificarHuellas().
			return false;
		}
		if (json == null) {
			return false;
		}
		compararHuellas(leerJson(json));
		return true;
	}

	static final String SQL_USUARIO_ACTUAL = "SELECT CURRENT_USER()";

	static final String SQL_HUELLAS = "SELECT huellas_objetos()";

	static final String SQL_PRIVILEGIOS = "SHOW GRANTS";

	static final String SQL_TLS = "SELECT VARIABLE_VALUE FROM performance_schema.session_status "
			+ "WHERE VARIABLE_NAME = 'Ssl_cipher'";

	static final String SQL_HUELLA_CORREO_VIEJA = "SELECT COUNT(*) FROM configuracion_bd "
			+ "WHERE clave = 'huella_correo_externo'";

	/**
	 * Sprint 7, tanda 2: dos usuarios distintos y el de los procesos e identidad se llama {@code cc_sistema} (los triggers
	 * lo reconocen por su nombre). Si son el mismo o falta el segundo, no arranca.
	 */
	public void verificarUsuarios() {
		if (sistema == null) {
			throw new IllegalStateException("Falta la conexión de cc_sistema (DB_SISTEMA_USUARIO y DB_SISTEMA_CLAVE): los "
					+ "procesos y la identidad (ingreso, claves, roles, sesiones) no se escriben con la conexión de la "
					+ "aplicación. Revisa docs/operacion/mysql-usuarios.md.");
		}
		String app = usuarioDe(jdbc);
		String deSistema = usuarioDe(sistema);
		if (deSistema.equals(app)) {
			throw new IllegalStateException("La aplicación y los procesos usan el MISMO usuario de base (" + app + "): las "
					+ "personas no deben poder escribir como el sistema. DB_USUARIO es cc_app y DB_SISTEMA_USUARIO, "
					+ "cc_sistema. Revisa docs/operacion/mysql-usuarios.md.");
		}
		if (!PermisosEsperados.SISTEMA.equals(deSistema)) {
			throw new IllegalStateException("La conexión de los procesos y la identidad usa el usuario «" + deSistema
					+ "» y debe ser cc_sistema (los triggers lo reconocen por su nombre). Revisa DB_SISTEMA_USUARIO.");
		}
		LOG.info("Conexiones verificadas: las personas con {} y los procesos y la identidad con {}.", app, deSistema);
	}

	private static String usuarioDe(JdbcTemplate conexion) {
		String actual = conexion.queryForObject(SQL_USUARIO_ACTUAL, String.class);
		if (actual == null) {
			return "";
		}
		int arroba = actual.indexOf('@');
		return arroba < 0 ? actual : actual.substring(0, arroba);
	}

	/**
	 * Sprint 7, tanda 2: con la conexión de cc_sistema, la bitácora y los libros siguen protegidos (1142) y los triggers
	 * nuevos rechazan sus inserciones imposibles (1644).
	 *
	 * @return si cc_sistema puede escribir (alguna inserción imposible llegó a su trigger)
	 */
	private boolean comprobarSistema() {
		boolean escribe = false;
		for (SentenciaProhibida sentencia : SENTENCIAS_SISTEMA) {
			String problema = comprobarDenegada(sistema, sentencia);
			if (problema != null) {
				throw new IllegalStateException(problema.replace("El usuario de la aplicación", "cc_sistema")
						+ " Revisa docs/operacion/mysql-usuarios.md.");
			}
			escribe |= sentencia.codigosAceptados().contains(MYSQL_SIGNAL) && llegoAlTrigger(sentencia);
		}
		LOG.info("Identidad, sesiones y firmas verificadas: cc_app no crea cuentas ni firma como sistema; cc_sistema no "
				+ "edita la bitácora ni los libros.");
		return escribe;
	}

	/**
	 * Sprint 7, tanda 2 (H7, E7 y E8): la huella de cada trigger y función de la base es la del jar. Un trigger debilitado
	 * que conserva su nombre, una función reemplazada o un objeto de más no dejan arrancar.
	 */
	public void verificarHuellas() {
		Map<String, String> instaladas;
		try {
			instaladas = leerJson(jdbc.queryForObject(SQL_HUELLAS, String.class));
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudo leer la función cuentasclaras.huellas_objetos() (código "
					+ codigoMySql(e) + "): aplica scripts/mysql/02-permisos-tablas.sql como administrador. Revisa "
					+ "docs/operacion/mysql-usuarios.md.", e);
		}
		compararHuellas(instaladas);
	}

	private void compararHuellas(Map<String, String> instaladas) {
		Map<String, String> esperadas = HuellasObjetosBd.esperadas();
		List<String> faltan = esperadas.keySet().stream().filter(n -> !instaladas.containsKey(n)).sorted().toList();
		List<String> sobran = instaladas.keySet().stream().filter(n -> !esperadas.containsKey(n)).sorted().toList();
		List<String> difieren = esperadas.keySet().stream()
				.filter(n -> instaladas.containsKey(n) && !esperadas.get(n).equals(instaladas.get(n))).sorted().toList();
		if (!faltan.isEmpty() || !sobran.isEmpty() || !difieren.isEmpty()) {
			throw new IllegalStateException("Los triggers y funciones de la base no son los de esta versión."
					+ (faltan.isEmpty() ? "" : " Faltan: " + String.join(", ", faltan) + ".")
					+ (sobran.isEmpty() ? "" : " Sobran (no están en 03-triggers.sql ni en 02): " + String.join(", ", sobran)
							+ ".")
					+ difieren.stream().map(n -> " huella distinta: " + n + ".").reduce("", String::concat)
					+ " Aplica 02-permisos-tablas.sql (administrador) y 03-triggers.sql (cc_migrador) de ESTA versión; si "
					+ "nadie los cambió a propósito, es un incidente (docs/operacion/incidente-auditoria.md).");
		}
		LOG.info("Huellas de los {} triggers y las {} funciones verificadas: son las de esta versión.",
				TRIGGERS_ESPERADOS.size(), esperadas.size() - TRIGGERS_ESPERADOS.size());
	}

	private static Map<String, String> leerJson(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		return tools.jackson.databind.json.JsonMapper.builder().build().readValue(json,
				new tools.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, String>>() {
				});
	}

	/** Sprint 7, tanda 2 (H6, E9): ninguna conexión tiene un privilegio que 02 no da. */
	public void verificarPrivilegios() {
		// Primero los roles: un rol anidado se nombra tal cual (SHOW GRANTS muestra sus privilegios como si fueran de
		// cc_negocio, sin decir de dónde salen).
		rolesDe(jdbc, "cc_app");
		rolesDe(sistema, PermisosEsperados.SISTEMA);
		privilegiosDe(jdbc, "cc_app", PermisosEsperados.de("cc_app"));
		privilegiosDe(sistema, PermisosEsperados.SISTEMA, PermisosEsperados.de(PermisosEsperados.SISTEMA));
		LOG.info("Privilegios verificados: cc_app y cc_sistema tienen solo los de 02-permisos-tablas.sql, sin roles "
				+ "anidados.");
	}

	/**
	 * Correcciones del sprint 7 (S7-M1): {@code SHOW GRANTS} no muestra los privilegios de un rol concedido DENTRO de
	 * {@code cc_negocio} («GRANT otro_rol TO cc_negocio»), pero la conexión los tiene. {@code APPLICABLE_ROLES} lista
	 * todos los roles que alcanzan a la conexión, también los anidados: el único permitido es {@code cc_negocio}, concedido
	 * directamente a la cuenta. Cualquier otro (o un rol dentro de cc_negocio) no deja arrancar.
	 */
	public static final String SQL_ROLES = "SELECT CONCAT(GRANTEE, '|', ROLE_NAME) FROM information_schema.APPLICABLE_ROLES";

	private static void rolesDe(JdbcTemplate conexion, String quien) {
		List<String> filas;
		try {
			filas = conexion.queryForList(SQL_ROLES, String.class);
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudieron leer los roles de " + quien + " (código " + codigoMySql(e)
					+ ").", e);
		}
		List<String> deMas = filas.stream().filter(f -> !f.equals(quien + "|" + PermisosEsperados.ROL_NEGOCIO))
				.map(f -> f.replace("|", " recibe el rol ")).sorted().toList();
		if (!deMas.isEmpty()) {
			throw new IllegalStateException("La conexión de " + quien + " tiene roles que 02-permisos-tablas.sql no da "
					+ "(roles anidados o de más): " + String.join(" | ", deMas) + ". Sus privilegios no salen en SHOW "
					+ "GRANTS. Aplica 02 de esta versión (recrea cc_negocio) y revisa quién los dio. Revisa "
					+ "docs/operacion/mysql-usuarios.md.");
		}
	}

	private static void privilegiosDe(JdbcTemplate conexion, String quien, PermisosEsperados esperados) {
		List<String> lineas;
		try {
			lineas = conexion.queryForList(SQL_PRIVILEGIOS, String.class);
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudieron leer los privilegios de " + quien + " (código " + codigoMySql(e)
					+ ").", e);
		}
		List<String> deMas = esperados.deMas(lineas);
		if (!deMas.isEmpty()) {
			throw new IllegalStateException("La conexión de " + quien + " tiene privilegios que 02-permisos-tablas.sql no "
					+ "da: " + String.join(" | ", deMas) + ". Aplica 02 de esta versión (empieza quitándolo todo) y revisa "
					+ "quién los dio. Revisa docs/operacion/mysql-usuarios.md.");
		}
	}

	/** Sprint 7, tanda 2 (H11): en prod las dos conexiones van cifradas; solo la instalación local en Docker lo apaga. */
	public void verificarTls() {
		if (!produccion) {
			return;
		}
		if (!exigirTls) {
			LOG.warn("La conexión a MySQL NO se exige cifrada (cuentasclaras.basedatos.exigir-tls: false). Solo es aceptable "
					+ "en la instalación local en Docker, con la base en el mismo servidor.");
			return;
		}
		for (Map.Entry<String, JdbcTemplate> conexion : Map.of("cc_app", jdbc, PermisosEsperados.SISTEMA, sistema)
				.entrySet()) {
			String cifrado = conexion.getValue().queryForList(SQL_TLS, String.class).stream().findFirst().orElse("");
			if (cifrado == null || cifrado.isBlank()) {
				throw new IllegalStateException("La conexión de " + conexion.getKey() + " a MySQL no va cifrada (TLS): "
						+ "agrega sslMode=VERIFY_IDENTITY (o REQUIRED) a DB_URL. Revisa docs/operacion/mysql-usuarios.md.");
			}
		}
		LOG.info("Conexiones a MySQL cifradas (TLS) verificadas.");
	}

	/** Sprint 7, tanda 2 (H5): la fila vieja del correo de la huella ya no se usa (ahora es por colegio). */
	private void avisarConfiguracionVieja() {
		try {
			Integer filas = jdbc.queryForObject(SQL_HUELLA_CORREO_VIEJA, Integer.class);
			if (filas != null && filas > 0) {
				LOG.warn("configuracion_bd todavía tiene 'huella_correo_externo': ya no se usa. Muévela a "
						+ "configuracion_colegio, una fila por colegio (docs/operacion/mysql-usuarios.md).");
			}
		}
		catch (DataAccessException e) {
			LOG.warn("No se pudo leer configuracion_bd: {}", e.getClass().getSimpleName());
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
		LOG.info("Permisos y triggers del panel (resumen diario, alertas a Promotoría, contacto del personal y llamadas de "
				+ "control) verificados.");
		LOG.info("Permisos y triggers de las correcciones del sprint 6 (muestra congelada, delegación de las llamadas, texto "
				+ "del resumen y correo externo por colegio) verificados.");
		verificarRespaldoSimulado();
		LOG.info("Permisos del respaldo verificados: la aplicación no registra, edita ni borra respaldos.");
		LOG.info("Permisos de la Ley 29733 verificados: nadie edita ni borra el registro de quién vio datos personales, ni "
				+ "cambia el derecho de un pedido sobre datos personales.");
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

	/**
	 * Sprint 7, tanda 1: en prod la base NO admite el destino simulado de los respaldos (una carpeta en el mismo servidor
	 * no protege nada). Sin la fila 'respaldo_simulado', trg_respaldo_registro rechaza registrar un respaldo simulado y
	 * el monitoreo lo da por atrasado.
	 */
	private void verificarRespaldoSimulado() {
		Integer filas;
		try {
			filas = jdbc.queryForObject(SQL_RESPALDO_SIMULADO, Integer.class);
		}
		catch (DataAccessException e) {
			throw new IllegalStateException("No se pudo leer configuracion_bd (código " + codigoMySql(e) + "). Revisa "
					+ "docs/operacion/mysql-usuarios.md.", e);
		}
		if (produccion && filas != null && filas > 0) {
			throw new IllegalStateException("La base de PRODUCCIÓN admite el destino simulado de los respaldos (fila "
					+ "'respaldo_simulado' en configuracion_bd): los respaldos quedarían en el mismo servidor. El DBA debe "
					+ "borrarla. Revisa docs/operacion/respaldos.md.");
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
		return comprobarDenegada(jdbc, sentencia);
	}

	private String comprobarDenegada(JdbcTemplate conexion, SentenciaProhibida sentencia) {
		try {
			conexion.update(sentencia.sql());
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
