-- Cuentas Claras · sprint 11, tanda 2 (sección 3.11; AP-14, AP2-5, AP2-8 y AP2-11): la SUPRESIÓN de un colegio en una
-- instalación compartida, con su acta aprobada por dos operadores. La corre el DBA (cc_migrador), en UNA conexión:
--
--   mysql -h <host> -u cc_migrador -p cuentasclaras -e "SET @cc_acta = <id del acta>; SOURCE scripts/fin-de-colegio/suprimir.sql"
--
-- 1. pasa el acta a EJECUTANDO (la base exige la cadena verificada hace 1 hora o menos, sin retención legal y con el plazo
--    vencido, y guarda su conexión, su vencimiento de 4 horas y la huella de los eventos del colegio);
-- 2. BORRA las filas del colegio de cada tabla de inventario.txt (con las FK apagadas en ESTA sesión: el orden no
--    importa y ninguna tabla que queda apunta a lo que se borra), contándolas;
-- 3. TACHA los eventos del colegio en evento_auditoria (trg_evento_auditoria_tachado exige este tachado exacto);
-- 4. pasa el acta a EJECUTADA con los conteos y el colegio a SUPRIMIDO con la ficha mínima (nombre, RUC, contrato).
-- Si algo falla, el cliente se detiene (sin --force) y el acta queda EJECUTANDO hasta su vencimiento: se corrige y se
-- vuelve a correr en otra conexión después de que venza (o se repite en la misma, si sigue abierta).
-- GENERADO desde inventario.txt (InventarioSupresionTest exige que coincidan). No lo edites a mano.

SET @cc_colegio = (SELECT a.colegio_id FROM acta_supresion a WHERE a.id = @cc_acta);
-- Sin acta, se detiene aquí (la subconsulta devuelve dos filas: error 1242).
SELECT IF(@cc_colegio IS NULL, (SELECT 1 UNION SELECT 2), 'acta encontrada') AS comprobacion;
UPDATE acta_supresion SET estado = 'EJECUTANDO', ejecutado_por = SUBSTRING_INDEX(SESSION_USER(), '@', 1)
 WHERE id = @cc_acta AND estado = 'APROBADA';
SELECT IF(cc_supresion_en_curso(@cc_colegio), 'supresion en curso', (SELECT 1 UNION SELECT 2)) AS comprobacion;

SET @cc_filas = JSON_OBJECT();
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM usuario_rol WHERE usuario_id IN (SELECT u.id FROM usuario u WHERE u.colegio_id = @cc_colegio);
SET @cc_filas = JSON_SET(@cc_filas, '$.usuario_rol', ROW_COUNT());
DELETE FROM acceso_dato_personal WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.acceso_dato_personal', ROW_COUNT());
DELETE FROM ajuste_cuota WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.ajuste_cuota', ROW_COUNT());
DELETE FROM alumno WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.alumno', ROW_COUNT());
DELETE FROM anio_escolar WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.anio_escolar', ROW_COUNT());
DELETE FROM anulacion_pago WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.anulacion_pago', ROW_COUNT());
DELETE FROM aplicacion_pago WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.aplicacion_pago', ROW_COUNT());
DELETE FROM apoderado WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.apoderado', ROW_COUNT());
DELETE FROM archivo_cargado WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.archivo_cargado', ROW_COUNT());
DELETE FROM asignacion_area WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.asignacion_area', ROW_COUNT());
DELETE FROM asignacion_docente WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.asignacion_docente', ROW_COUNT());
DELETE FROM aviso_asistencia_corte WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.aviso_asistencia_corte', ROW_COUNT());
DELETE FROM aviso_familia WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.aviso_familia', ROW_COUNT());
DELETE FROM borrador_conclusion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.borrador_conclusion', ROW_COUNT());
DELETE FROM caja_diaria WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.caja_diaria', ROW_COUNT());
DELETE FROM calificacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.calificacion', ROW_COUNT());
DELETE FROM cambio_contacto_titular WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cambio_contacto_titular', ROW_COUNT());
DELETE FROM cambio_correo_control WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cambio_correo_control', ROW_COUNT());
DELETE FROM cierre_caja WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cierre_caja', ROW_COUNT());
DELETE FROM cierre_mensual_banco WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cierre_mensual_banco', ROW_COUNT());
DELETE FROM codigo_modular WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.codigo_modular', ROW_COUNT());
DELETE FROM colegio_logo WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.colegio_logo', ROW_COUNT());
DELETE FROM comprobante WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.comprobante', ROW_COUNT());
DELETE FROM comprobante_linea WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.comprobante_linea', ROW_COUNT());
DELETE FROM comunicado WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.comunicado', ROW_COUNT());
DELETE FROM comunicado_adjunto WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.comunicado_adjunto', ROW_COUNT());
DELETE FROM comunicado_destinatario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.comunicado_destinatario', ROW_COUNT());
DELETE FROM conector_colegio WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.conector_colegio', ROW_COUNT());
DELETE FROM configuracion_academica WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.configuracion_academica', ROW_COUNT());
DELETE FROM configuracion_colegio WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.configuracion_colegio', ROW_COUNT());
DELETE FROM configuracion_evaluacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.configuracion_evaluacion', ROW_COUNT());
DELETE FROM confirmacion_lectura WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.confirmacion_lectura', ROW_COUNT());
DELETE FROM consentimiento_aviso WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.consentimiento_aviso', ROW_COUNT());
DELETE FROM cuenta_bancaria WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cuenta_bancaria', ROW_COUNT());
DELETE FROM cuota WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.cuota', ROW_COUNT());
DELETE FROM delegacion_llamada WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.delegacion_llamada', ROW_COUNT());
DELETE FROM delegacion_siagie WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.delegacion_siagie', ROW_COUNT());
DELETE FROM deposito_caja WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.deposito_caja', ROW_COUNT());
DELETE FROM descuento WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.descuento', ROW_COUNT());
DELETE FROM enlace_activacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.enlace_activacion', ROW_COUNT());
DELETE FROM entrega_libreta WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.entrega_libreta', ROW_COUNT());
DELETE FROM evento_pasarela WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.evento_pasarela', ROW_COUNT());
DELETE FROM exoneracion_area WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.exoneracion_area', ROW_COUNT());
DELETE FROM exportacion_calificaciones WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.exportacion_calificaciones', ROW_COUNT());
DELETE FROM exportacion_nomina WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.exportacion_nomina', ROW_COUNT());
DELETE FROM extracto_bancario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.extracto_bancario', ROW_COUNT());
DELETE FROM familia WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.familia', ROW_COUNT());
DELETE FROM feriado WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.feriado', ROW_COUNT());
DELETE FROM firma_operacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.firma_operacion', ROW_COUNT());
DELETE FROM importacion_alumnos WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.importacion_alumnos', ROW_COUNT());
DELETE FROM libreta_emitida WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.libreta_emitida', ROW_COUNT());
DELETE FROM linea_recaudacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.linea_recaudacion', ROW_COUNT());
DELETE FROM linea_saldo_inicial WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.linea_saldo_inicial', ROW_COUNT());
DELETE FROM liquidacion_linea WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.liquidacion_linea', ROW_COUNT());
DELETE FROM liquidacion_pasarela WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.liquidacion_pasarela', ROW_COUNT());
DELETE FROM llamada_control WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.llamada_control', ROW_COUNT());
DELETE FROM lote_calificacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.lote_calificacion', ROW_COUNT());
DELETE FROM lote_recaudacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.lote_recaudacion', ROW_COUNT());
DELETE FROM lote_saldo_inicial WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.lote_saldo_inicial', ROW_COUNT());
DELETE FROM marca_asistencia WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.marca_asistencia', ROW_COUNT());
DELETE FROM matricula WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.matricula', ROW_COUNT());
DELETE FROM matricula_historial WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.matricula_historial', ROW_COUNT());
DELETE FROM mensaje WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.mensaje', ROW_COUNT());
DELETE FROM movimiento_bancario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.movimiento_bancario', ROW_COUNT());
DELETE FROM muestra_llamada WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.muestra_llamada', ROW_COUNT());
DELETE FROM orden_pago WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.orden_pago', ROW_COUNT());
DELETE FROM orden_pago_cuota WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.orden_pago_cuota', ROW_COUNT());
DELETE FROM pago WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.pago', ROW_COUNT());
DELETE FROM partida_conciliacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.partida_conciliacion', ROW_COUNT());
DELETE FROM periodo_evaluacion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.periodo_evaluacion', ROW_COUNT());
DELETE FROM plan_pension WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.plan_pension', ROW_COUNT());
DELETE FROM primera_direccion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.primera_direccion', ROW_COUNT());
DELETE FROM primera_promotoria WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.primera_promotoria', ROW_COUNT());
DELETE FROM reembolso WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.reembolso', ROW_COUNT());
DELETE FROM reembolso_pasarela WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.reembolso_pasarela', ROW_COUNT());
DELETE FROM registro_asistencia WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.registro_asistencia', ROW_COUNT());
DELETE FROM renovacion_matricula WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.renovacion_matricula', ROW_COUNT());
DELETE FROM resolucion_respaldo WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.resolucion_respaldo', ROW_COUNT());
DELETE FROM resumen_diario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.resumen_diario', ROW_COUNT());
DELETE FROM seccion WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.seccion', ROW_COUNT());
DELETE FROM semilla_muestreo WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.semilla_muestreo', ROW_COUNT());
DELETE FROM serie_comprobante WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.serie_comprobante', ROW_COUNT());
DELETE FROM sesion_usuario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.sesion_usuario', ROW_COUNT());
DELETE FROM solicitud_cambio WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.solicitud_cambio', ROW_COUNT());
DELETE FROM uso_facultad_titular WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.uso_facultad_titular', ROW_COUNT());
DELETE FROM usuario WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.usuario', ROW_COUNT());
DELETE FROM verificacion_bancaria WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.verificacion_bancaria', ROW_COUNT());
DELETE FROM verificacion_contacto WHERE colegio_id = @cc_colegio;
SET @cc_filas = JSON_SET(@cc_filas, '$.verificacion_contacto', ROW_COUNT());
SET FOREIGN_KEY_CHECKS = 1;

UPDATE evento_auditoria SET nombre_usuario = 'suprimido', usuario_id = NULL, roles = NULL, valor_anterior = NULL,
    valor_nuevo = NULL, detalle = NULL, ip = NULL
 WHERE colegio_id = @cc_colegio
   AND secuencia <= (SELECT a.ultima_secuencia FROM acta_supresion a WHERE a.id = @cc_acta);
SET @cc_tachados = ROW_COUNT();

UPDATE acta_supresion SET estado = 'EJECUTADA', filas_suprimidas = @cc_filas, eventos_tachados = @cc_tachados
 WHERE id = @cc_acta AND estado = 'EJECUTANDO';
UPDATE colegio SET estado = 'SUPRIMIDO', acta_supresion_id = @cc_acta WHERE id = @cc_colegio;
SELECT IF((SELECT c.estado FROM colegio c WHERE c.id = @cc_colegio) = 'SUPRIMIDO', 'colegio suprimido',
    (SELECT 1 UNION SELECT 2)) AS comprobacion;
SELECT @cc_filas AS filas_suprimidas, @cc_tachados AS eventos_tachados;
