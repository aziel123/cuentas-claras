-- Cuentas Claras · permisos por tabla de cc_app (paso 2 de 2). Ejecutar como administrador DESPUÉS de que Flyway
-- cree las tablas (MySQL no acepta un GRANT por tabla sobre una tabla que no existe).
-- Cada tabla nueva: agrega aquí su GRANT. Tablas financieras (pago, cuota...): nunca DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.usuario TO 'cc_app'@'%';
GRANT INSERT, UPDATE, DELETE ON cuentasclaras.usuario_rol TO 'cc_app'@'%';  -- @ElementCollection reescribe filas
GRANT INSERT ON cuentasclaras.evento_auditoria TO 'cc_app'@'%';              -- SIN UPDATE NI DELETE
GRANT UPDATE ON cuentasclaras.auditoria_cadena TO 'cc_app'@'%';              -- también lo exige SELECT ... FOR UPDATE

-- Sprint 2 · tanda 1: estructura académica, familias, apoderados, alumnos y matrículas.
-- Nada se borra (se desactiva, se retira o se mueve de sección): sin DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.anio_escolar TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.seccion TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.familia TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.apoderado TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.alumno TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.matricula TO 'cc_app'@'%';

-- Sprint 2 · tanda 2: registro de importaciones de alumnos desde Excel. SOLO inserción (sin UPDATE ni DELETE).
GRANT INSERT ON cuentasclaras.importacion_alumnos TO 'cc_app'@'%';

-- Sprint 2 · tanda 3: planes de pensiones, saldo inicial y cuotas. Tablas financieras: NUNCA DELETE.
-- UPDATE solo por columna (correcciones del sprint 2): deben coincidir EXACTAMENTE con las columnas updatable=true
-- de cada entidad (lo comprueba InmutabilidadCuotasTest). Año, nivel, versión, motivo, creador: inmutables (1143).
-- Los montos y fechas del plan solo cambian en BORRADOR: lo impide scripts/mysql/03-triggers.sql (el GRANT por
-- columna no distingue estados).
GRANT INSERT, UPDATE (estado, vigente, monto_matricula, vencimiento_matricula, monto_pension, vencimientos_pension,
    cobro_desde, editado_por, editores, enviado_por, enviado_en, devuelto_por, devuelto_en, motivo_devolucion,
    aprobado_por, aprobado_en, cerrado_por, cerrado_en, actualizado_en, version) ON cuentasclaras.plan_pension TO 'cc_app'@'%';
-- Corte, referencia y total declarado del lote: inmutables.
GRANT INSERT, UPDATE (estado, total_confirmado, enviado_por, enviado_en, confirmado_por, confirmado_en, devuelto_por,
    devuelto_en, motivo_devolucion, descartado_por, descartado_en, motivo_descarte, actualizado_en, version)
    ON cuentasclaras.lote_saldo_inicial TO 'cc_app'@'%';
-- Una línea solo se «quita» (flag): su alumno, concepto, año, mes, monto y vencimiento no cambian.
GRANT INSERT, UPDATE (quitada, actualizado_en, version) ON cuentasclaras.linea_saldo_inicial TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.cuota TO 'cc_app'@'%';
-- UPDATE de cuota SOLO por columna: monto, fecha de vencimiento, alumno, origen y clave quedan inmutables
-- (MySQL responde 1143). Debe coincidir EXACTAMENTE con las columnas updatable=true de la entidad Cuota.
-- Sprint 3: monto_descuento (libro de descuentos). monto_pagado y monto_descuento solo pueden ser la suma de su libro
-- (aplicacion_pago y ajuste_cuota): lo exige el trigger trg_cuota_libro de 03-triggers.sql.
-- Correcciones del sprint 3 (M1): anulacion_solicitud_id enlaza la solicitud aprobada (lo exige trg_cuota_libro).
GRANT UPDATE (estado, monto_pagado, monto_descuento, obligacion, anulacion_motivo, anulacion_solicitada_por,
    anulacion_aprobada_por, anulada_en, anulacion_solicitud_id, actualizado_en, version) ON cuentasclaras.cuota TO 'cc_app'@'%';

-- Sprint 2 · correcciones: solicitudes de cambio que aprueba otra persona. Nunca DELETE; tipo, entidad, datos, motivo y
-- solicitante no cambian (solo se resuelven).
GRANT INSERT, UPDATE (estado, pendiente, resuelto_por, resuelto_en, comentario, actualizado_en, version)
    ON cuentasclaras.solicitud_cambio TO 'cc_app'@'%';

-- Sprint 3 · tanda 1: comprobantes, caja diaria, pagos y libro de aplicaciones. Tablas financieras: NUNCA DELETE.
-- Serie: solo avanza su último número (y el trigger exige que sea de uno en uno).
GRANT INSERT, UPDATE (ultimo_numero, actualizado_en, version) ON cuentasclaras.serie_comprobante TO 'cc_app'@'%';
-- Comprobante: los datos tributarios (serie, número, receptor, total) no cambian; solo su envío al OSE.
-- Sprint 4 (tanda 1): outbox del OSE (ENVIADO, reintentos con espera creciente, aceptación). reemplaza_id no cambia.
GRANT INSERT, UPDATE (estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf, proximo_intento_en,
    ultimo_error, codigo_respuesta, aceptado_en, actualizado_en, version) ON cuentasclaras.comprobante TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.comprobante_linea TO 'cc_app'@'%';                  -- solo inserción
-- Caja: cajero, fecha y fondo no cambian; solo abre/cierra y registra el conteo a ciegas (los triggers lo vigilan).
-- Correcciones del sprint 3 (M1): reapertura_solicitud_id enlaza la reapertura aprobada (lo exige trg_caja_diaria_estado).
GRANT INSERT, UPDATE (estado, cierres, conteos, primer_conteo, reapertura_solicitud_id, actualizado_en, version)
    ON cuentasclaras.caja_diaria TO 'cc_app'@'%';
-- Pago: familia, caja, medio, operación, total, vuelto y comprobante no cambian; solo se anula.
GRANT INSERT, UPDATE (estado, operacion_vigente, actualizado_en, version) ON cuentasclaras.pago TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.aplicacion_pago TO 'cc_app'@'%';                    -- solo inserción

-- Sprint 3 · tanda 2: anulaciones de pago y descuentos. anulacion_pago y ajuste_cuota: SOLO INSERCIÓN.
GRANT INSERT ON cuentasclaras.anulacion_pago TO 'cc_app'@'%';                     -- solo inserción
-- Descuento: lo pedido (alumno, tipo, valor, cuotas, total, motivo, sustento) no cambia; solo se resuelve.
GRANT INSERT, UPDATE (estado, resuelto_por, resuelto_en, actualizado_en, version) ON cuentasclaras.descuento TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.ajuste_cuota TO 'cc_app'@'%';                       -- solo inserción
-- Sprint 3 · tanda 3: cierre, depósito y verificación bancaria. El conteo del cierre no cambia: solo se revisa.
GRANT INSERT, UPDATE (estado, revisado_por, revisado_en, comentario_revision, actualizado_en, version) ON cuentasclaras.cierre_caja TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.deposito_caja TO 'cc_app'@'%';                      -- solo inserción
GRANT INSERT ON cuentasclaras.verificacion_bancaria TO 'cc_app'@'%';              -- solo inserción

-- Correcciones del sprint 3 (docs/arquitectura/sprint-3-correcciones.md). Reembolso de devoluciones: SOLO INSERCIÓN
-- (lo registra Administración; trg_reembolso_registro y un CHECK impiden que sea la cajera del pago).
GRANT INSERT ON cuentasclaras.reembolso TO 'cc_app'@'%';                          -- solo inserción
-- Sprint 4 · tanda 1 (V13): pagos en línea y outbox del OSE. Tablas financieras: NUNCA DELETE.
-- configuracion_bd: SIN GRANT a propósito (cc_app solo la lee con el SELECT general; la escribe el DBA, y en prod no
-- existe la fila 'pasarela_simulada'). Lo comprueba VerificadorPermisosBaseDatos al arrancar (1142).
-- Orden: monto, familia, apoderado, cuotas, referencia y vencimiento no cambian; solo el enlace (una vez), la
-- confirmación (una vez), el estado y la resolución (trg_orden_pago_estado).
GRANT INSERT, UPDATE (estado, proveedor_orden_id, enlace_pago, cargo_id, operacion, monto_confirmado, moneda_confirmada,
    medio_confirmado, confirmado_en, tardia, motivo_revision, detalle_revision, devolucion_operacion, devuelto_por,
    devuelto_en, actualizado_en, version) ON cuentasclaras.orden_pago TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.orden_pago_cuota TO 'cc_app'@'%';                   -- solo inserción
GRANT INSERT, UPDATE (estado, intentos, resultado, procesado_en, actualizado_en, version)
    ON cuentasclaras.evento_pasarela TO 'cc_app'@'%';
-- pago, caja_diaria y usuario no cambian su GRANT: las columnas nuevas (pago.orden_pago_id, caja_diaria.canal) son
-- inmutables (1143) y usuario.apoderado_id es updatable = false en la entidad (y su cambio se audita).
-- Sprint 4 · tanda 2 (V14): recaudación bancaria. Tablas financieras: NUNCA DELETE. El archivo del banco es evidencia:
-- SOLO INSERCIÓN. Del lote no cambian el archivo, su SHA-256, el banco, las fechas, las líneas ni el total; de la línea
-- no cambian el monto, la fecha, el código ni la operación (trg_lote_recaudacion_estado y trg_linea_recaudacion_estado
-- vigilan los estados). pago.linea_recaudacion_id es inmutable (1143): pago no cambia su GRANT.
GRANT INSERT ON cuentasclaras.archivo_cargado TO 'cc_app'@'%';                    -- solo inserción
GRANT INSERT, UPDATE (estado, sha_vigente, intentos_confirmacion, total_ciego, confirmado_por, confirmado_en, aplicado_en,
    lineas_aplicadas, lineas_excepcion, monto_aplicado, monto_excepcion, rechazado_por, rechazado_en, motivo_rechazo,
    actualizado_en, version) ON cuentasclaras.lote_recaudacion TO 'cc_app'@'%';
GRANT INSERT, UPDATE (estado, motivo_excepcion, detalle, devolucion_operacion, devuelto_por, devuelto_en, actualizado_en,
    version) ON cuentasclaras.linea_recaudacion TO 'cc_app'@'%';
-- Sprint 4 · tanda 3 (V15): extracto, conciliación y liquidaciones de la pasarela. Tablas financieras: NUNCA DELETE.
-- La cuenta no cambia su número (se desactiva). Del extracto no cambian la cuenta, la secuencia, el archivo, las fechas
-- ni los saldos (trg_extracto_bancario_estado vigila la confirmación a ciegas en cadena). Los movimientos y las
-- liquidaciones son de SOLO INSERCIÓN. De la partida no cambian el movimiento, el objeto, la regla ni los montos
-- (trg_partida_conciliacion_estado: una partida resuelta no cambia). verificacion_bancaria no cambia su GRANT: sus
-- columnas nuevas (origen, partida_id) son inmutables y la tabla sigue siendo de solo inserción.
GRANT INSERT, UPDATE (activa, actualizado_en, version) ON cuentasclaras.cuenta_bancaria TO 'cc_app'@'%';
GRANT INSERT, UPDATE (estado, secuencia_vigente, intentos_confirmacion, saldo_final_ciego, confirmacion_extracto_id,
    confirmado_por, confirmado_en, rechazado_por, rechazado_en, motivo_rechazo, actualizado_en, version)
    ON cuentasclaras.extracto_bancario TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.movimiento_bancario TO 'cc_app'@'%';                -- solo inserción
GRANT INSERT ON cuentasclaras.liquidacion_pasarela TO 'cc_app'@'%';               -- solo inserción
GRANT INSERT ON cuentasclaras.liquidacion_linea TO 'cc_app'@'%';                  -- solo inserción
GRANT INSERT, UPDATE (estado, movimiento_vigente, objeto_vigente, resuelto_por, resuelto_en, actualizado_en, version)
    ON cuentasclaras.partida_conciliacion TO 'cc_app'@'%';

-- M2: cc_app no lee information_schema.TRIGGERS (necesitaría el privilegio TRIGGER, que no debe tener). Esta función
-- (SQL SECURITY DEFINER: corre con los permisos de quien la crea) devuelve solo los nombres de los triggers del esquema,
-- separados por comas; al arrancar en prod, VerificadorPermisosBaseDatos los compara con la lista de 03-triggers.sql.
-- (Una vista DEFINER no sirve: MySQL 8 filtra information_schema con los permisos de quien consulta y cc_app vería 0.)
DROP FUNCTION IF EXISTS cuentasclaras.triggers_instalados;
-- Sprint 4, tanda 3: JSON_ARRAYAGG en lugar de GROUP_CONCAT. Con 40 triggers la lista pasa de 1024 caracteres
-- (group_concat_max_len por defecto) y GROUP_CONCAT la cortaba: dentro de una función eso es el error 1260 y la
-- aplicación no arrancaba. Los nombres solo tienen letras, dígitos y «_», así que quitar [ ] " y espacios es seguro.
CREATE FUNCTION cuentasclaras.triggers_instalados() RETURNS TEXT READS SQL DATA SQL SECURITY DEFINER
    RETURN (SELECT COALESCE(REPLACE(REPLACE(REPLACE(REPLACE(JSON_ARRAYAGG(TRIGGER_NAME), '[', ''), ']', ''), '"', ''),
        ' ', ''), '')
        FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'cuentasclaras');
GRANT EXECUTE ON FUNCTION cuentasclaras.triggers_instalados TO 'cc_app'@'%';
