-- Cuentas Claras · permisos de cc_app, cc_sistema y cc_respaldo (paso 2 de 3). Ejecutar como administrador DESPUÉS de que
-- Flyway cree las tablas (MySQL no acepta un GRANT por tabla sobre una tabla que no existe) y CON LA APLICACIÓN DETENIDA:
-- el REVOKE inicial deja un instante sin permisos (docs/operacion/respaldos.md, despliegue).
-- Cada tabla nueva: agrega aquí su GRANT. Tablas financieras (pago, cuota...): nunca DELETE.
--
-- Sprint 7, tanda 2 (sección 6.2 del diseño):
--   * Este archivo es la FUENTE ÚNICA de los permisos (H6): empieza quitándolo TODO, así un GRANT dado a mano no
--     sobrevive a un despliegue. «ALL PRIVILEGES» no quita la concesión de un rol (comprobado en MySQL 8.4.11): el rol
--     cc_negocio se revoca aparte.
--   * Los permisos de negocio van al ROL cc_negocio, que reciben cc_app (las peticiones de las personas) y cc_sistema (los
--     procesos sistema.* y la identidad). Las escrituras exclusivas de cc_sistema van directo a cc_sistema (bloque 2).
--   * VerificadorPermisosBaseDatos compara al arrancar los privilegios de cada conexión con los de este archivo (el jar
--     lo lleva en db/mysql/): un privilegio de más, en cualquier tabla, no deja arrancar prod.

-- Los cuerpos de las funciones se interpretan como UTF-8 con cualquier cliente (su huella se compara con este archivo).
SET NAMES utf8mb4;

-- 0. Fuente única: se quita todo (privilegios, GRANT OPTION y el rol) y se vuelve a dar.
REVOKE IF EXISTS ALL PRIVILEGES, GRANT OPTION FROM 'cc_app'@'%', 'cc_sistema'@'%', 'cc_respaldo'@'%' IGNORE UNKNOWN USER;
REVOKE IF EXISTS 'cc_negocio' FROM 'cc_app'@'%', 'cc_sistema'@'%', 'cc_respaldo'@'%' IGNORE UNKNOWN USER;
REVOKE IF EXISTS ALL PRIVILEGES, GRANT OPTION FROM 'cc_negocio' IGNORE UNKNOWN USER;

-- 1. Rol de negocio: lo que tenía cc_app hasta el sprint 7, salvo la identidad (usuario, usuario_rol, sesion_usuario), las
--    escrituras de los procesos (huellas, resumen, muestra, semilla, pasarela, liquidaciones) y el UPDATE del envío al
--    OSE y del estado de los mensajes, que pasan a cc_sistema (bloque 2).
GRANT SELECT ON cuentasclaras.* TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.evento_auditoria TO 'cc_negocio';              -- SIN UPDATE NI DELETE
GRANT UPDATE ON cuentasclaras.auditoria_cadena TO 'cc_negocio';              -- también lo exige SELECT ... FOR UPDATE

-- Sprint 2 · tanda 1: estructura académica, familias, apoderados, alumnos y matrículas.
-- Nada se borra (se desactiva, se retira o se mueve de sección): sin DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.anio_escolar TO 'cc_negocio';
GRANT INSERT, UPDATE ON cuentasclaras.seccion TO 'cc_negocio';
GRANT INSERT, UPDATE ON cuentasclaras.familia TO 'cc_negocio';
GRANT INSERT, UPDATE ON cuentasclaras.apoderado TO 'cc_negocio';
GRANT INSERT, UPDATE ON cuentasclaras.alumno TO 'cc_negocio';
GRANT INSERT, UPDATE ON cuentasclaras.matricula TO 'cc_negocio';

-- Sprint 2 · tanda 2: registro de importaciones de alumnos desde Excel. SOLO inserción (sin UPDATE ni DELETE).
GRANT INSERT ON cuentasclaras.importacion_alumnos TO 'cc_negocio';

-- Sprint 2 · tanda 3: planes de pensiones, saldo inicial y cuotas. Tablas financieras: NUNCA DELETE.
-- UPDATE solo por columna (correcciones del sprint 2): deben coincidir EXACTAMENTE con las columnas updatable=true
-- de cada entidad (lo comprueba InmutabilidadCuotasTest). Año, nivel, versión, motivo, creador: inmutables (1143).
-- Los montos y fechas del plan solo cambian en BORRADOR: lo impide scripts/mysql/03-triggers.sql (el GRANT por
-- columna no distingue estados).
GRANT INSERT, UPDATE (estado, vigente, monto_matricula, vencimiento_matricula, monto_pension, vencimientos_pension,
    cobro_desde, editado_por, editores, enviado_por, enviado_en, devuelto_por, devuelto_en, motivo_devolucion,
    aprobado_por, aprobado_en, cerrado_por, cerrado_en, actualizado_en, version) ON cuentasclaras.plan_pension TO 'cc_negocio';
-- Corte, referencia y total declarado del lote: inmutables.
GRANT INSERT, UPDATE (estado, total_confirmado, enviado_por, enviado_en, confirmado_por, confirmado_en, devuelto_por,
    devuelto_en, motivo_devolucion, descartado_por, descartado_en, motivo_descarte, actualizado_en, version)
    ON cuentasclaras.lote_saldo_inicial TO 'cc_negocio';
-- Una línea solo se «quita» (flag): su alumno, concepto, año, mes, monto y vencimiento no cambian.
GRANT INSERT, UPDATE (quitada, actualizado_en, version) ON cuentasclaras.linea_saldo_inicial TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.cuota TO 'cc_negocio';
-- UPDATE de cuota SOLO por columna: monto, fecha de vencimiento, alumno, origen y clave quedan inmutables
-- (MySQL responde 1143). Debe coincidir EXACTAMENTE con las columnas updatable=true de la entidad Cuota.
-- Sprint 3: monto_descuento (libro de descuentos). monto_pagado y monto_descuento solo pueden ser la suma de su libro
-- (aplicacion_pago y ajuste_cuota): lo exige el trigger trg_cuota_libro de 03-triggers.sql.
-- Correcciones del sprint 3 (M1): anulacion_solicitud_id enlaza la solicitud aprobada (lo exige trg_cuota_libro).
GRANT UPDATE (estado, monto_pagado, monto_descuento, obligacion, anulacion_motivo, anulacion_solicitada_por,
    anulacion_aprobada_por, anulada_en, anulacion_solicitud_id, actualizado_en, version) ON cuentasclaras.cuota TO 'cc_negocio';

-- Sprint 2 · correcciones: solicitudes de cambio que aprueba otra persona. Nunca DELETE; tipo, entidad, datos, motivo y
-- solicitante no cambian (solo se resuelven).
GRANT INSERT, UPDATE (estado, pendiente, resuelto_por, resuelto_en, comentario, actualizado_en, version)
    ON cuentasclaras.solicitud_cambio TO 'cc_negocio';

-- Sprint 3 · tanda 1: comprobantes, caja diaria, pagos y libro de aplicaciones. Tablas financieras: NUNCA DELETE.
-- Serie: solo avanza su último número (y el trigger exige que sea de uno en uno).
GRANT INSERT, UPDATE (ultimo_numero, actualizado_en, version) ON cuentasclaras.serie_comprobante TO 'cc_negocio';
-- Comprobante: los datos tributarios (serie, número, receptor, total) no cambian; solo su envío al OSE.
-- Sprint 4 (tanda 1): outbox del OSE (ENVIADO, reintentos con espera creciente, aceptación). reemplaza_id no cambia.
-- Sprint 7, tanda 2: el UPDATE del envío lo hace SOLO sistema.ose (cc_sistema, bloque 2); el rol conserva el INSERT
-- (emisión y reemisión).
GRANT INSERT ON cuentasclaras.comprobante TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.comprobante_linea TO 'cc_negocio';                  -- solo inserción
-- Caja: cajero, fecha y fondo no cambian; solo abre/cierra y registra el conteo a ciegas (los triggers lo vigilan).
-- Correcciones del sprint 3 (M1): reapertura_solicitud_id enlaza la reapertura aprobada (lo exige trg_caja_diaria_estado).
GRANT INSERT, UPDATE (estado, cierres, conteos, primer_conteo, reapertura_solicitud_id, actualizado_en, version)
    ON cuentasclaras.caja_diaria TO 'cc_negocio';
-- Pago: familia, caja, medio, operación, total, vuelto y comprobante no cambian; solo se anula.
GRANT INSERT, UPDATE (estado, operacion_vigente, actualizado_en, version) ON cuentasclaras.pago TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.aplicacion_pago TO 'cc_negocio';                    -- solo inserción

-- Sprint 3 · tanda 2: anulaciones de pago y descuentos. anulacion_pago y ajuste_cuota: SOLO INSERCIÓN.
GRANT INSERT ON cuentasclaras.anulacion_pago TO 'cc_negocio';                     -- solo inserción
-- Descuento: lo pedido (alumno, tipo, valor, cuotas, total, motivo, sustento) no cambia; solo se resuelve.
GRANT INSERT, UPDATE (estado, resuelto_por, resuelto_en, actualizado_en, version) ON cuentasclaras.descuento TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.ajuste_cuota TO 'cc_negocio';                       -- solo inserción
-- Sprint 3 · tanda 3: cierre, depósito y verificación bancaria. El conteo del cierre no cambia: solo se revisa.
GRANT INSERT, UPDATE (estado, revisado_por, revisado_en, comentario_revision, actualizado_en, version) ON cuentasclaras.cierre_caja TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.deposito_caja TO 'cc_negocio';                      -- solo inserción
GRANT INSERT ON cuentasclaras.verificacion_bancaria TO 'cc_negocio';              -- solo inserción

-- Correcciones del sprint 3 (docs/arquitectura/sprint-3-correcciones.md). Reembolso de devoluciones: SOLO INSERCIÓN
-- (lo registra Administración; trg_reembolso_registro y un CHECK impiden que sea la cajera del pago).
GRANT INSERT ON cuentasclaras.reembolso TO 'cc_negocio';                          -- solo inserción
-- Sprint 4 · tanda 1 (V13): pagos en línea y outbox del OSE. Tablas financieras: NUNCA DELETE.
-- configuracion_bd: SIN GRANT a propósito (cc_app solo la lee con el SELECT general; la escribe el DBA, y en prod no
-- existe la fila 'pasarela_simulada'). Lo comprueba VerificadorPermisosBaseDatos al arrancar (1142).
-- Orden: monto, familia, apoderado, cuotas, referencia y vencimiento no cambian; solo el enlace (una vez), la
-- confirmación (una vez), el estado y la resolución (trg_orden_pago_estado).
GRANT INSERT, UPDATE (estado, proveedor_orden_id, enlace_pago, cargo_id, operacion, monto_confirmado, moneda_confirmada,
    medio_confirmado, confirmado_en, tardia, motivo_revision, detalle_revision, devolucion_operacion, devuelto_por,
    devuelto_en, contracargo_en, contracargo_origen, actualizado_en, version) ON cuentasclaras.orden_pago TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.orden_pago_cuota TO 'cc_negocio';                   -- solo inserción
-- evento_pasarela: sprint 7, tanda 2: lo escribe SOLO sistema.pasarela (cc_sistema, bloque 2).
-- pago, caja_diaria y usuario no cambian su GRANT: las columnas nuevas (pago.orden_pago_id, caja_diaria.canal) son
-- inmutables (1143) y usuario.apoderado_id es updatable = false en la entidad (y su cambio se audita).
-- Sprint 4 · tanda 2 (V14): recaudación bancaria. Tablas financieras: NUNCA DELETE. El archivo del banco es evidencia:
-- SOLO INSERCIÓN. Del lote no cambian el archivo, su SHA-256, el banco, las fechas, las líneas ni el total; de la línea
-- no cambian el monto, la fecha, el código ni la operación (trg_lote_recaudacion_estado y trg_linea_recaudacion_estado
-- vigilan los estados). pago.linea_recaudacion_id es inmutable (1143): pago no cambia su GRANT.
GRANT INSERT ON cuentasclaras.archivo_cargado TO 'cc_negocio';                    -- solo inserción
GRANT INSERT, UPDATE (estado, sha_vigente, intentos_confirmacion, total_ciego, confirmado_por, confirmado_en, aplicado_en,
    lineas_aplicadas, lineas_excepcion, monto_aplicado, monto_excepcion, rechazado_por, rechazado_en, motivo_rechazo,
    actualizado_en, version) ON cuentasclaras.lote_recaudacion TO 'cc_negocio';
GRANT INSERT, UPDATE (estado, motivo_excepcion, detalle, devolucion_operacion, devuelto_por, devuelto_en,
    devolucion_banco, devolucion_cuenta, devolucion_titular, actualizado_en, version)
    ON cuentasclaras.linea_recaudacion TO 'cc_negocio';
-- Sprint 4 · tanda 3 (V15): extracto, conciliación y liquidaciones de la pasarela. Tablas financieras: NUNCA DELETE.
-- La cuenta no cambia su número (se desactiva). Del extracto no cambian la cuenta, la secuencia, el archivo, las fechas
-- ni los saldos (trg_extracto_bancario_estado vigila la confirmación a ciegas en cadena). Los movimientos y las
-- liquidaciones son de SOLO INSERCIÓN. De la partida no cambian el movimiento, el objeto, la regla ni los montos
-- (trg_partida_conciliacion_estado: una partida resuelta no cambia). verificacion_bancaria no cambia su GRANT: sus
-- columnas nuevas (origen, partida_id) son inmutables y la tabla sigue siendo de solo inserción.
GRANT INSERT, UPDATE (activa, actualizado_en, version) ON cuentasclaras.cuenta_bancaria TO 'cc_negocio';
GRANT INSERT, UPDATE (estado, secuencia_vigente, intentos_confirmacion, saldo_final_ciego, confirmacion_extracto_id,
    confirmado_por, confirmado_en, rechazado_por, rechazado_en, motivo_rechazo, actualizado_en, version)
    ON cuentasclaras.extracto_bancario TO 'cc_negocio';
GRANT INSERT ON cuentasclaras.movimiento_bancario TO 'cc_negocio';                -- solo inserción
-- liquidacion_pasarela y liquidacion_linea: sprint 7, tanda 2: las importa SOLO sistema.pasarela (cc_sistema, bloque 2).
GRANT INSERT, UPDATE (estado, movimiento_vigente, objeto_vigente, resuelto_por, resuelto_en, actualizado_en, version)
    ON cuentasclaras.partida_conciliacion TO 'cc_negocio';

-- Correcciones del sprint 4 (V16). Columnas nuevas inmutables (sin UPDATE: 1143): extracto_bancario.muestra y
-- semilla_muestreo, lote_recaudacion.muestra y partida_conciliacion.linea_recaudacion_id (S4-A1, S4-A2 y S4-A4).
-- orden_pago.contracargo_en y contracargo_origen se escriben una vez (trg_orden_pago_estado); la cuenta de destino de
-- la devolución de una línea de recaudación se escribe al ejecutarla (trg_linea_recaudacion_estado). El reembolso de un
-- pago en línea por la API de la pasarela es de SOLO INSERCIÓN (S4-A3).
GRANT INSERT ON cuentasclaras.reembolso_pasarela TO 'cc_negocio';                 -- solo inserción
-- S4-M2: el enlace de activación de la cuenta del apoderado solo cambia al usarse o anularse (nunca su hash, su
-- usuario, su vencimiento ni quién lo creó). Sin DELETE.
GRANT INSERT, UPDATE (usado_en, usado_ip, anulado_en, actualizado_en, version)
    ON cuentasclaras.enlace_activacion TO 'cc_negocio';

-- Sprint 5 · tanda 1 (V17): mensajes y huella. Nunca DELETE. El destino, la plantilla y los parámetros no cambian (1143).
-- Sprint 7, tanda 2: el estado del mensaje lo cambia SOLO sistema.mensajeria (despacho y avisos del proveedor; cc_sistema,
-- bloque 2); el rol conserva el INSERT (los avisos nacen en la transacción de quien cobra). huella_bitacora: solo
-- sistema.auditoria (bloque 2).
GRANT INSERT ON cuentasclaras.mensaje TO 'cc_negocio';
-- enlace_activacion: SIN cambios (mensaje_id y proposito no están en su UPDATE: 1143). usuario y apoderado mantienen su
-- UPDATE por tabla: telefono_whatsapp se audita; el contacto del apoderado lo vigila trg_apoderado_facturacion.
-- Sprint 5 · tanda 2 (V18): renovación y avisos de la familia. Nunca DELETE. El alumno, la familia, el año destino y la
-- matrícula de origen de una renovación no cambian, ni el texto, el tipo y las referencias de un aviso (1143). matricula
-- mantiene su GRANT por tabla: los estados y la activación los vigila trg_matricula_estado.
GRANT INSERT, UPDATE (estado, grado_destino, seccion_destino_id, canal_respuesta, respondido_por, respondido_en,
    matricula_id, actualizado_en, version) ON cuentasclaras.renovacion_matricula TO 'cc_negocio';
GRANT INSERT, UPDATE (estado, atendido_por, atendido_en, respuesta, actualizado_en, version)
    ON cuentasclaras.aviso_familia TO 'cc_negocio';
-- Sprint 5 · tanda 3 (V19): feriados extra, semilla del muestreo y cierre mensual. Nunca DELETE. La fecha del feriado,
-- la semilla del día y los totales calculados del cierre no cambian (1143 y 1142). apoderado mantiene su UPDATE por tabla
-- (recordatorios_activos lo cambia solo el propio apoderado, y se audita).
GRANT INSERT, UPDATE (vigente, anulado_por, anulado_en, motivo_anulacion, pendiente, aprobado_por, aprobado_en,
    actualizado_en, version) ON cuentasclaras.feriado TO 'cc_negocio';
-- Correcciones del sprint 5 (V20). Nunca DELETE.
-- S5-M3: el día propuesto lo aprueba otra persona (pendiente, aprobado_por y aprobado_en están en el GRANT de feriado
-- de arriba; los vigila trg_feriado_anulacion).
-- S5-A1: la verificación del contacto solo cambia al usarse o anularse (nunca su hash, su contacto ni su mensaje: 1143).
GRANT INSERT, UPDATE (verificado_en, verificado_ip, anulado_en, actualizado_en, version)
    ON cuentasclaras.verificacion_contacto TO 'cc_negocio';
-- S5-M4: huella por hora, solo inserción (1142).
-- huella_hora: sprint 7, tanda 2: solo sistema.auditoria (cc_sistema, bloque 2).
-- apoderado mantiene su UPDATE por tabla: telefono_verificado, correo_verificado y contacto_aprobado_* los vigila
-- trg_apoderado_facturacion (verificación usada y solicitud aprobada).
-- semilla_muestreo: sprint 7, tanda 2 (H4): solo sistema.muestreo (cc_sistema, bloque 2).
GRANT INSERT, UPDATE (estado, intentos, abonos_ciego, cargos_ciego, saldo_ciego, registrado_por, registrado_en,
    actualizado_en, version) ON cuentasclaras.cierre_mensual_banco TO 'cc_negocio';
-- configuracion_bd sigue SIN GRANT. Filas nuevas que solo escribe el DBA:
--   ('mensajeria_simulada', 'PERMITIDA')  -> solo en las bases de dev, test (MySQL) y piloto. NUNCA en prod.
--   ('huella_correo_externo', '<correo del contador>') -> opcional, en prod (decisión 49).

-- Sprint 6 · tanda 1: sin cambios (panel, reportes y Excel solo leen con el SELECT general).
-- Sprint 6 · tanda 2 (V21): foto del resumen diario, SOLO inserción (1142): ni sistema.panel puede corregirla. mensaje
-- no cambia su GRANT (los tipos nuevos usan las mismas columnas). usuario mantiene su UPDATE por tabla: el celular, el
-- correo y contacto_solicitud_id del personal los vigila trg_usuario_contacto (mismo criterio que apoderado y
-- trg_apoderado_facturacion).
-- resumen_diario: sprint 7, tanda 2: solo sistema.panel (cc_sistema, bloque 2).
-- configuracion_bd sigue SIN GRANT. Fila nueva que solo escribe el DBA (opcional, en prod; decisión 69):
--   ('resumen_correo_externo', '<correo del contador>') -> el resumen diario también le llega por correo.
-- Sprint 6 · tanda 3 (V22): llamada de control, SOLO inserción (1142): el resultado de una llamada no se corrige ni se
-- borra. semilla_muestreo no cambia su GRANT (el ámbito nuevo LLAMADA_CONTROL usa las mismas columnas). Quién la
-- registra, la semana y el pago en efectivo de la familia los vigila trg_llamada_control_registro.
GRANT INSERT ON cuentasclaras.llamada_control TO 'cc_negocio';                    -- solo inserción
-- Correcciones del sprint 6 (V23): la muestra congelada de la llamada de control (S6-B3) y la delegación de la semana a
-- Dirección (S6-M2), SOLO inserción (1142): ni la muestra ni la delegación se corrigen ni se borran. Sus triggers
-- (trg_muestra_llamada_registro y trg_delegacion_llamada_registro) exigen la semana en curso, la familia candidata y quién
-- las crea. llamada_control mantiene su GRANT (intento y por_delegacion se escriben al insertar).
-- muestra_llamada: sprint 7, tanda 2 (residual S6): solo sistema.panel (cc_sistema, bloque 2).
GRANT INSERT ON cuentasclaras.delegacion_llamada TO 'cc_negocio';                 -- solo inserción
-- QA-S6-6: configuracion_colegio (por colegio) sigue la regla de configuracion_bd: SIN GRANT de escritura (1142). Fila
-- que solo escribe el DBA (opcional, en prod; decisión 69), reemplaza a ('resumen_correo_externo') de configuracion_bd:
--   INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en)
--   VALUES (<id del colegio>, 'resumen_correo_externo', '<correo del contador de ese colegio>', NOW(6));

-- Sprint 7 · tanda 1 (V24): respaldos. cc_app NO escribe respaldo (solo lo lee con el SELECT general: 1142 al insertar,
-- editar o borrar). cc_respaldo (scripts/respaldo/respaldar.sh) solo lee el esquema para el volcado
-- (mysqldump --single-transaction --no-tablespaces --skip-triggers: no necesita LOCK TABLES, PROCESS, RELOAD ni TRIGGER)
-- e inserta la fila de su respaldo; trg_respaldo_registro exige que la escriba cc_respaldo, al terminar y con anclas que
-- son eventos reales de la bitácora. Sin UPDATE ni DELETE: el registro de respaldos es de solo inserción.
-- configuracion_bd sigue SIN GRANT. Fila nueva que solo escribe el DBA, NUNCA en prod (el verificador no arranca):
--   ('respaldo_simulado', 'PERMITIDA') -> admite el destino «simulado» (una carpeta local) en dev, CI y piloto.
GRANT SELECT, SHOW VIEW ON cuentasclaras.* TO 'cc_respaldo'@'%';
GRANT INSERT ON cuentasclaras.respaldo TO 'cc_respaldo'@'%';

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
GRANT EXECUTE ON FUNCTION cuentasclaras.triggers_instalados TO 'cc_negocio';

-- ===================== Sprint 7 · tanda 2 (V25): identidad, sesiones, firmas y procesos =====================
-- La firma de una aprobación la inserta la persona (cc_app, por el rol); trg_firma_operacion_nace valida su secreto
-- contra una sesión abierta de esa persona y lo deja en NULL. Solo inserción (1142 al editar o borrar).
GRANT INSERT ON cuentasclaras.firma_operacion TO 'cc_negocio';

-- ===================== Sprint 7 · tanda 3 (V26): Ley 29733 =====================
-- Quién vio datos personales: lo inserta la persona que los vio (cc_app, por el rol), ANTES de que se le muestren. Solo
-- inserción: nadie lo edita ni lo borra (1142); lo purga el DBA a los 2 años con un script revisado (sección 8.4).
GRANT INSERT ON cuentasclaras.acceso_dato_personal TO 'cc_negocio';
-- aviso_familia: SIN cambios. El derecho de un pedido sobre datos personales (columna nueva) no está en su UPDATE: 1143.

-- 2. Exclusivas de cc_sistema (H1, H4 y sección 3.9). El nombre del usuario es FIJO: los triggers lo reconocen con
--    cc_es_sistema() (SESSION_USER() dentro de un trigger o de una función DEFINER es el usuario de la conexión).
--    Identidad: altas, claves, bloqueo, desactivación, roles y sesiones. usuario_rol sin UPDATE: un rol no se edita, se
--    quita (DELETE de esa fila, que vigila trg_usuario_rol_baja) o se agrega (trg_usuario_rol_alta).
GRANT INSERT, UPDATE ON cuentasclaras.usuario TO 'cc_sistema'@'%';
GRANT INSERT, DELETE ON cuentasclaras.usuario_rol TO 'cc_sistema'@'%';
GRANT INSERT, UPDATE (cerrada_en, motivo_cierre, actualizado_en, version) ON cuentasclaras.sesion_usuario TO 'cc_sistema'@'%';
--    Escrituras que solo hace un proceso: con cc_app, 1142.
GRANT INSERT ON cuentasclaras.semilla_muestreo TO 'cc_sistema'@'%';               -- sistema.muestreo, solo inserción
GRANT INSERT ON cuentasclaras.muestra_llamada TO 'cc_sistema'@'%';                -- sistema.panel, solo inserción
GRANT INSERT ON cuentasclaras.resumen_diario TO 'cc_sistema'@'%';                 -- sistema.panel, solo inserción
GRANT INSERT ON cuentasclaras.huella_bitacora TO 'cc_sistema'@'%';                -- sistema.auditoria, solo inserción
GRANT INSERT ON cuentasclaras.huella_hora TO 'cc_sistema'@'%';                    -- sistema.auditoria, solo inserción
GRANT INSERT ON cuentasclaras.liquidacion_pasarela TO 'cc_sistema'@'%';           -- sistema.pasarela, solo inserción
GRANT INSERT ON cuentasclaras.liquidacion_linea TO 'cc_sistema'@'%';              -- sistema.pasarela, solo inserción
GRANT INSERT, UPDATE (estado, intentos, resultado, procesado_en, actualizado_en, version)
    ON cuentasclaras.evento_pasarela TO 'cc_sistema'@'%';                         -- sistema.pasarela
--    El envío al OSE (sistema.ose) y el estado de los mensajes (sistema.mensajeria): mismas columnas que antes tenía cc_app.
GRANT UPDATE (estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf, proximo_intento_en, ultimo_error,
    codigo_respuesta, aceptado_en, actualizado_en, version) ON cuentasclaras.comprobante TO 'cc_sistema'@'%';
GRANT UPDATE (estado, proveedor, proveedor_mensaje_id, intentos, proximo_intento_en, enviado_en, entregado_en, leido_en,
    ultimo_error, actualizado_en, version) ON cuentasclaras.mensaje TO 'cc_sistema'@'%';

-- 3. Huellas de los objetos (H7, sección 3.7): {nombre: SHA-256} de cada trigger (momento, evento, tabla y cuerpo) y de cada
--    función del esquema (tipo y cuerpo), con el cuerpo NORMALIZADO igual que HuellasObjetosBd en Java: sin comentarios
--    «--» hasta el fin de la línea, cada tramo de espacios en un espacio y recortado (el cliente mysql 8.0 quita los
--    comentarios y el 8.4 los deja; el archivo puede tener CRLF). Al arrancar, VerificadorPermisosBaseDatos compara este
--    JSON con las huellas calculadas desde 03-triggers.sql y este archivo, empaquetados en el jar: falta, sobra o difiere
--    un objeto y prod no arranca. El patrón del comentario se arma con CONCAT para que este cuerpo no contenga «--».
--    Residual: un DBA puede reemplazar esta función por otra que devuelva lo esperado (lo cubre el simulacro semanal,
--    que lee information_schema como administrador: docs/operacion/respaldos.md).
DROP FUNCTION IF EXISTS cuentasclaras.huellas_objetos;
CREATE FUNCTION cuentasclaras.huellas_objetos() RETURNS JSON READS SQL DATA SQL SECURITY DEFINER
    RETURN (SELECT JSON_OBJECTAGG(o.nombre, o.huella) FROM (
        SELECT t.TRIGGER_NAME AS nombre, SHA2(CONCAT_WS('|', t.ACTION_TIMING, t.EVENT_MANIPULATION, t.EVENT_OBJECT_TABLE,
            TRIM(REGEXP_REPLACE(REGEXP_REPLACE(t.ACTION_STATEMENT, CONCAT('-', '-[^\n]*'), ''), '[[:space:]]+', ' '))),
            256) AS huella
          FROM information_schema.TRIGGERS t WHERE t.TRIGGER_SCHEMA = 'cuentasclaras'
        UNION ALL
        SELECT r.ROUTINE_NAME, SHA2(CONCAT_WS('|', r.ROUTINE_TYPE,
            TRIM(REGEXP_REPLACE(REGEXP_REPLACE(r.ROUTINE_DEFINITION, CONCAT('-', '-[^\n]*'), ''), '[[:space:]]+', ' '))),
            256)
          FROM information_schema.ROUTINES r WHERE r.ROUTINE_SCHEMA = 'cuentasclaras') o);
GRANT EXECUTE ON FUNCTION cuentasclaras.huellas_objetos TO 'cc_negocio';
GRANT EXECUTE ON FUNCTION cuentasclaras.huellas_objetos TO 'cc_respaldo'@'%';

-- 4. El rol a las dos conexiones de la aplicación, activo al conectar.
GRANT 'cc_negocio' TO 'cc_app'@'%', 'cc_sistema'@'%';
SET DEFAULT ROLE 'cc_negocio' TO 'cc_app'@'%', 'cc_sistema'@'%';
