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
GRANT UPDATE (estado, monto_pagado, monto_descuento, obligacion, anulacion_motivo, anulacion_solicitada_por,
    anulacion_aprobada_por, anulada_en, actualizado_en, version) ON cuentasclaras.cuota TO 'cc_app'@'%';

-- Sprint 2 · correcciones: solicitudes de cambio que aprueba otra persona. Nunca DELETE; tipo, entidad, datos, motivo y
-- solicitante no cambian (solo se resuelven).
GRANT INSERT, UPDATE (estado, pendiente, resuelto_por, resuelto_en, comentario, actualizado_en, version)
    ON cuentasclaras.solicitud_cambio TO 'cc_app'@'%';

-- Sprint 3 · tanda 1: comprobantes, caja diaria, pagos y libro de aplicaciones. Tablas financieras: NUNCA DELETE.
-- Serie: solo avanza su último número (y el trigger exige que sea de uno en uno).
GRANT INSERT, UPDATE (ultimo_numero, actualizado_en, version) ON cuentasclaras.serie_comprobante TO 'cc_app'@'%';
-- Comprobante: los datos tributarios (serie, número, receptor, total) no cambian; solo su envío al OSE.
GRANT INSERT, UPDATE (estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf, actualizado_en, version)
    ON cuentasclaras.comprobante TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.comprobante_linea TO 'cc_app'@'%';                  -- solo inserción
-- Caja: cajero, fecha y fondo no cambian; solo abre/cierra y registra el conteo a ciegas (los triggers lo vigilan).
GRANT INSERT, UPDATE (estado, cierres, conteos, primer_conteo, actualizado_en, version) ON cuentasclaras.caja_diaria TO 'cc_app'@'%';
-- Pago: familia, caja, medio, operación, total, vuelto y comprobante no cambian; solo se anula.
GRANT INSERT, UPDATE (estado, operacion_vigente, actualizado_en, version) ON cuentasclaras.pago TO 'cc_app'@'%';
GRANT INSERT ON cuentasclaras.aplicacion_pago TO 'cc_app'@'%';                    -- solo inserción
