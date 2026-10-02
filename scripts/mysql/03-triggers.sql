-- Cuentas Claras · triggers de MySQL (paso 3, después de migrar). Los aplica cc_migrador:
--   mysql -h <host> -u cc_migrador -p cuentasclaras < scripts/mysql/03-triggers.sql
-- Requisito (una vez, como administrador, porque el binlog está activo):
--   SET PERSIST log_bin_trust_function_creators = 1;
-- No van en Flyway: H2 (desarrollo y pruebas) no los soporta. La aplicación en prod NO ARRANCA si faltan
-- (VerificadorPermisosBaseDatos los comprueba con un INSERT imposible que el trigger rechaza con el error 1644).
-- El GRANT por columna (02-permisos-tablas.sql) no distingue estados: estos triggers sí.

DELIMITER $$

-- Un plan nace en BORRADOR (nadie inserta un plan ya aprobado).
DROP TRIGGER IF EXISTS trg_plan_pension_nace_borrador$$
CREATE TRIGGER trg_plan_pension_nace_borrador BEFORE INSERT ON plan_pension FOR EACH ROW
BEGIN
    IF NEW.estado IS NULL OR NEW.estado <> 'BORRADOR' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un plan de pensiones nace en BORRADOR';
    END IF;
END$$

-- Fuera de BORRADOR no cambian montos, fechas, configuración ni editores; un plan cerrado no cambia de estado.
DROP TRIGGER IF EXISTS trg_plan_pension_inmutable$$
CREATE TRIGGER trg_plan_pension_inmutable BEFORE UPDATE ON plan_pension FOR EACH ROW
BEGIN
    IF OLD.estado <> 'BORRADOR' AND (NEW.monto_matricula <> OLD.monto_matricula
            OR NEW.monto_pension <> OLD.monto_pension
            OR NEW.vencimiento_matricula <> OLD.vencimiento_matricula
            OR NEW.vencimientos_pension <> OLD.vencimientos_pension
            OR NOT (NEW.cobro_desde <=> OLD.cobro_desde)
            OR NEW.editado_por <> OLD.editado_por
            OR NEW.editores <> OLD.editores) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el plan solo se edita en BORRADOR';
    END IF;
    IF OLD.estado IN ('REEMPLAZADO', 'DESCARTADO') AND NEW.estado <> OLD.estado THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un plan cerrado no cambia de estado';
    END IF;
    IF OLD.estado = 'APROBADO' AND NEW.estado NOT IN ('APROBADO', 'REEMPLAZADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un plan aprobado solo se reemplaza';
    END IF;
END$$

-- Un lote nace en BORRADOR; confirmado o descartado ya no cambia de estado.
DROP TRIGGER IF EXISTS trg_lote_saldo_inicial_nace_borrador$$
CREATE TRIGGER trg_lote_saldo_inicial_nace_borrador BEFORE INSERT ON lote_saldo_inicial FOR EACH ROW
BEGIN
    IF NEW.estado IS NULL OR NEW.estado <> 'BORRADOR' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un lote de saldo inicial nace en BORRADOR';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_lote_saldo_inicial_cerrado$$
CREATE TRIGGER trg_lote_saldo_inicial_cerrado BEFORE UPDATE ON lote_saldo_inicial FOR EACH ROW
BEGIN
    IF OLD.estado IN ('CONFIRMADO', 'DESCARTADO') AND NEW.estado <> OLD.estado THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un lote confirmado o descartado no cambia';
    END IF;
END$$

-- Las líneas solo se agregan o se quitan mientras su lote está en BORRADOR (en preparación).
DROP TRIGGER IF EXISTS trg_linea_saldo_inicial_lote_abierto$$
CREATE TRIGGER trg_linea_saldo_inicial_lote_abierto BEFORE INSERT ON linea_saldo_inicial FOR EACH ROW
BEGIN
    IF COALESCE((SELECT estado FROM lote_saldo_inicial WHERE id = NEW.lote_id), '') <> 'BORRADOR' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el lote no está en preparación';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_linea_saldo_inicial_quitar$$
CREATE TRIGGER trg_linea_saldo_inicial_quitar BEFORE UPDATE ON linea_saldo_inicial FOR EACH ROW
BEGIN
    IF NEW.quitada <> OLD.quitada
            AND COALESCE((SELECT estado FROM lote_saldo_inicial WHERE id = OLD.lote_id), '') <> 'BORRADOR' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el lote no está en preparación';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 3 · Caja (tandas 1 y 2: cobro, comprobante, libro, anulaciones y descuentos) ==
-- Todas las comparaciones usan <=> o COALESCE: en un trigger, «IF NULL THEN» NO entra (igual que un CHECK con NULL pasa).
-- Versión FINAL del sprint 3 (docs/arquitectura/sprint-3-caja.md, sección 6.3): un trigger NUNCA nombra una tabla que
-- aún no existe (desde ese momento todo UPDATE sobre su tabla falla con 1146). Requiere V10 (anulacion_pago, descuento
-- y ajuste_cuota) y V11 (cierre_caja, deposito_caja y verificacion_bancaria). NO apliques este script sobre una base
-- sin V11: aplícalo siempre DESPUÉS de migrar.

DELIMITER $$

-- Una cuota nace PENDIENTE, sin pagos ni descuentos (nadie inserta una cuota ya pagada).
DROP TRIGGER IF EXISTS trg_cuota_nace_pendiente$$
CREATE TRIGGER trg_cuota_nace_pendiente BEFORE INSERT ON cuota FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'PENDIENTE') OR NOT (NEW.monto_pagado <=> 0.00) OR NOT (NEW.monto_descuento <=> 0.00) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una cuota nace PENDIENTE, sin pagos ni descuentos';
    END IF;
END$$

-- Lo pagado y lo descontado de una cuota son SIEMPRE la suma de su libro (aplicacion_pago y ajuste_cuota).
-- Cierra el pendiente del sprint 2: «cuota PAGADA sin pago». Una cuota anulada ya no cambia.
DROP TRIGGER IF EXISTS trg_cuota_libro$$
CREATE TRIGGER trg_cuota_libro BEFORE UPDATE ON cuota FOR EACH ROW
BEGIN
    IF OLD.estado = 'ANULADA' AND NEW.estado <> 'ANULADA' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una cuota anulada no cambia';
    END IF;
    IF NOT (NEW.monto_pagado <=> OLD.monto_pagado) AND NOT (NEW.monto_pagado <=>
            (SELECT COALESCE(SUM(a.monto), 0.00) FROM aplicacion_pago a WHERE a.cuota_id = NEW.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el monto pagado no coincide con el libro de pagos';
    END IF;
    IF NOT (NEW.monto_descuento <=> OLD.monto_descuento) AND NOT (NEW.monto_descuento <=>
            (SELECT COALESCE(SUM(j.monto), 0.00) FROM ajuste_cuota j WHERE j.cuota_id = NEW.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el descuento no coincide con los ajustes aprobados';
    END IF;
END$$

-- Series: nacen en 0 y solo avanzan de uno en uno.
DROP TRIGGER IF EXISTS trg_serie_comprobante_nace$$
CREATE TRIGGER trg_serie_comprobante_nace BEFORE INSERT ON serie_comprobante FOR EACH ROW
BEGIN
    IF NOT (NEW.ultimo_numero <=> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una serie nace en 0';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_serie_comprobante_correlativo$$
CREATE TRIGGER trg_serie_comprobante_correlativo BEFORE UPDATE ON serie_comprobante FOR EACH ROW
BEGIN
    IF NOT (NEW.ultimo_numero <=> OLD.ultimo_numero + 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la serie avanza de uno en uno';
    END IF;
END$$

-- El comprobante usa el número que la serie acaba de asignar: sin saltos ni reutilización (y UNIQUE serie+número).
DROP TRIGGER IF EXISTS trg_comprobante_correlativo$$
CREATE TRIGGER trg_comprobante_correlativo BEFORE INSERT ON comprobante FOR EACH ROW
BEGIN
    IF NOT (NEW.numero <=> (SELECT s.ultimo_numero FROM serie_comprobante s WHERE s.id = NEW.serie_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el número no es el siguiente de la serie';
    END IF;
    IF NEW.tipo = 'NOTA_CREDITO' AND NOT (LEFT(NEW.serie, 1) <=>
            (SELECT LEFT(m.serie, 1) FROM comprobante m WHERE m.id = NEW.modifica_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la nota de crédito usa la letra del comprobante que anula';
    END IF;
END$$

-- La caja nace ABIERTA y sin cierres.
DROP TRIGGER IF EXISTS trg_caja_diaria_nace$$
CREATE TRIGGER trg_caja_diaria_nace BEFORE INSERT ON caja_diaria FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'ABIERTA') OR NOT (NEW.cierres <=> 0) OR NOT (NEW.conteos <=> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una caja nace ABIERTA, sin cierres ni conteos';
    END IF;
END$$

-- Cerrar exige el cierre registrado; reabrir exige una reapertura que se aprueba en esta transacción y reinicia el
-- conteo a ciegas. El primer conteo no se reescribe (no se puede «tantear» el esperado contando una y otra vez).
DROP TRIGGER IF EXISTS trg_caja_diaria_estado$$
CREATE TRIGGER trg_caja_diaria_estado BEFORE UPDATE ON caja_diaria FOR EACH ROW
BEGIN
    DECLARE reabre BOOLEAN DEFAULT (OLD.estado = 'CERRADA' AND NEW.estado = 'ABIERTA');
    IF OLD.estado = 'ABIERTA' AND NEW.estado = 'CERRADA' AND (NOT (NEW.cierres <=> OLD.cierres + 1)
            OR NOT EXISTS (SELECT 1 FROM cierre_caja c WHERE c.caja_diaria_id = NEW.id AND c.numero = NEW.cierres)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la caja se cierra con un cierre registrado';
    END IF;
    IF reabre AND (NOT (NEW.cierres <=> OLD.cierres) OR NOT (NEW.conteos <=> 0)
            OR NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.tipo = 'REAPERTURA_CAJA'
                AND s.entidad = 'caja_diaria' AND s.entidad_id = NEW.id AND s.estado = 'PENDIENTE')
            OR EXISTS (SELECT 1 FROM deposito_caja x WHERE x.caja_diaria_id = NEW.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la caja solo se reabre con una reapertura aprobada y sin depósito';
    END IF;
    IF NEW.estado = OLD.estado AND NOT (NEW.cierres <=> OLD.cierres) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los cierres solo cambian al cerrar';
    END IF;
    IF NOT reabre AND (NEW.conteos < OLD.conteos OR NEW.conteos > OLD.conteos + 1
            OR (OLD.primer_conteo IS NOT NULL AND NOT (NEW.primer_conteo <=> OLD.primer_conteo))
            OR (NEW.conteos <> OLD.conteos AND OLD.estado <> 'ABIERTA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el conteo a ciegas no se reescribe';
    END IF;
END$$

-- Un pago nace VIGENTE, con un comprobante (boleta o factura) del mismo total. En efectivo, solo en una caja ABIERTA;
-- la única excepción es el pago que reemplaza a otro ya anulado de la misma caja, con el mismo medio y el mismo total.
DROP TRIGGER IF EXISTS trg_pago_registro$$
CREATE TRIGGER trg_pago_registro BEFORE INSERT ON pago FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'VIGENTE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un pago nace VIGENTE';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM comprobante c WHERE c.id = NEW.comprobante_id AND c.tipo IN ('BOLETA', 'FACTURA')
            AND c.total = NEW.total) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el pago necesita su boleta o factura por el mismo total';
    END IF;
    IF NEW.origen = 'CAJA' AND NEW.medio = 'EFECTIVO'
            AND NOT ((SELECT d.estado FROM caja_diaria d WHERE d.id = NEW.caja_diaria_id) <=> 'ABIERTA') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la caja está cerrada: no acepta efectivo';
    END IF;
    IF NEW.origen = 'REEMPLAZO' AND NOT EXISTS (SELECT 1 FROM pago r WHERE r.id = NEW.reemplaza_pago_id
            AND r.estado = 'ANULADO' AND r.caja_diaria_id = NEW.caja_diaria_id AND r.medio = NEW.medio
            AND r.total = NEW.total) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el reemplazo debe ser del pago anulado (misma caja, medio y total)';
    END IF;
END$$

-- Un pago solo pasa a ANULADO (nunca vuelve) y solo si ya está registrada su anulación aprobada.
DROP TRIGGER IF EXISTS trg_pago_anulacion$$
CREATE TRIGGER trg_pago_anulacion BEFORE UPDATE ON pago FOR EACH ROW
BEGIN
    IF OLD.estado = 'ANULADO' AND NOT (NEW.estado <=> 'ANULADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un pago anulado no vuelve';
    END IF;
    IF OLD.estado = 'VIGENTE' AND NEW.estado = 'ANULADO'
            AND NOT EXISTS (SELECT 1 FROM anulacion_pago n WHERE n.pago_id = NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: falta la anulación aprobada del pago';
    END IF;
    IF NEW.estado = 'VIGENTE' AND NOT (NEW.operacion_vigente <=> OLD.operacion_vigente) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el número de operación no cambia';
    END IF;
END$$

-- Libro de aplicaciones: solo a cuotas de la familia del pago, sin pasar el total del pago, y una reversión solo con la
-- anulación registrada y por el monto exacto (negativo) de la aplicación original.
DROP TRIGGER IF EXISTS trg_aplicacion_pago_registro$$
CREATE TRIGGER trg_aplicacion_pago_registro BEFORE INSERT ON aplicacion_pago FOR EACH ROW
BEGIN
    IF NEW.tipo = 'APLICACION' THEN
        IF NOT EXISTS (SELECT 1 FROM pago p JOIN cuota c ON c.id = NEW.cuota_id JOIN alumno a ON a.id = c.alumno_id
                WHERE p.id = NEW.pago_id AND p.estado = 'VIGENTE' AND a.familia_id = p.familia_id
                AND c.colegio_id = p.colegio_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el pago solo se aplica a cuotas de su familia';
        END IF;
        IF (SELECT COALESCE(SUM(x.monto), 0.00) FROM aplicacion_pago x WHERE x.pago_id = NEW.pago_id) + NEW.monto
                > (SELECT p.total FROM pago p WHERE p.id = NEW.pago_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: las aplicaciones superan el total del pago';
        END IF;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM anulacion_pago n WHERE n.pago_id = NEW.pago_id)
                OR NOT (NEW.monto <=> -(SELECT o.monto FROM aplicacion_pago o WHERE o.id = NEW.revierte_id
                    AND o.tipo = 'APLICACION')) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: reversión sin anulación o por otro monto';
        END IF;
    END IF;
END$$

-- La anulación corresponde al pago vigente, a su cajero y a una nota de crédito que anula SU comprobante.
DROP TRIGGER IF EXISTS trg_anulacion_pago_registro$$
CREATE TRIGGER trg_anulacion_pago_registro BEFORE INSERT ON anulacion_pago FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pago p JOIN comprobante n ON n.id = NEW.nota_credito_id
            WHERE p.id = NEW.pago_id AND p.estado = 'VIGENTE' AND p.cajero = NEW.cajero_pago AND p.total = NEW.monto
            AND n.tipo = 'NOTA_CREDITO' AND n.modifica_id = p.comprobante_id AND n.total = p.total) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: anulación que no corresponde al pago o a su nota de crédito';
    END IF;
END$$

-- Un ajuste solo nace de un descuento APROBADO del mismo alumno de la cuota.
DROP TRIGGER IF EXISTS trg_ajuste_cuota_registro$$
CREATE TRIGGER trg_ajuste_cuota_registro BEFORE INSERT ON ajuste_cuota FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM descuento d JOIN cuota c ON c.id = NEW.cuota_id
            WHERE d.id = NEW.descuento_id AND d.estado = 'APROBADO' AND d.alumno_id = c.alumno_id
            AND d.cuotas LIKE CONCAT('%,', NEW.cuota_id, ',%')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el ajuste necesita un descuento aprobado para esa cuota';
    END IF;
END$$

-- Un descuento nace SOLICITADO; resuelto no vuelve a cambiar.
DROP TRIGGER IF EXISTS trg_descuento_nace$$
CREATE TRIGGER trg_descuento_nace BEFORE INSERT ON descuento FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'SOLICITADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un descuento nace SOLICITADO';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_descuento_resuelto$$
CREATE TRIGGER trg_descuento_resuelto BEFORE UPDATE ON descuento FOR EACH ROW
BEGIN
    IF OLD.estado <> 'SOLICITADO' AND NOT (NEW.estado <=> OLD.estado) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un descuento resuelto no cambia';
    END IF;
END$$

-- El cierre lo registra el cajero de la caja ABIERTA, con el número siguiente y con el esperado que dice el libro:
-- fondo + pagos en efectivo VIGENTES de la caja. Nadie puede «acomodar» el esperado.
DROP TRIGGER IF EXISTS trg_cierre_caja_registro$$
CREATE TRIGGER trg_cierre_caja_registro BEFORE INSERT ON cierre_caja FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM caja_diaria d WHERE d.id = NEW.caja_diaria_id AND d.estado = 'ABIERTA'
            AND d.cajero = NEW.creado_por AND d.cierres + 1 = NEW.numero AND d.fondo_fijo = NEW.fondo_fijo
            AND d.conteos > 0 AND d.primer_conteo = NEW.primer_conteo) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el cierre es del cajero de una caja abierta';
    END IF;
    IF NOT (NEW.efectivo_cobrado <=> (SELECT COALESCE(SUM(p.total), 0.00) FROM pago p
            WHERE p.caja_diaria_id = NEW.caja_diaria_id AND p.medio = 'EFECTIVO' AND p.estado = 'VIGENTE')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el efectivo esperado no coincide con los pagos';
    END IF;
    IF NOT (NEW.estado <=> 'POR_REVISAR') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un cierre nace POR_REVISAR';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_cierre_caja_revisado$$
CREATE TRIGGER trg_cierre_caja_revisado BEFORE UPDATE ON cierre_caja FOR EACH ROW
BEGIN
    IF OLD.estado <> 'POR_REVISAR' AND NOT (NEW.estado <=> OLD.estado) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un cierre revisado no cambia';
    END IF;
END$$

-- Verifica contra el banco alguien que no cobró ni depositó; solo pagos digitales.
DROP TRIGGER IF EXISTS trg_verificacion_bancaria_registro$$
CREATE TRIGGER trg_verificacion_bancaria_registro BEFORE INSERT ON verificacion_bancaria FOR EACH ROW
BEGIN
    IF NEW.pago_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.id = NEW.pago_id
            AND p.medio <> 'EFECTIVO' AND p.cajero <> NEW.creado_por AND p.creado_por <> NEW.creado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: verifica un pago digital alguien que no lo cobró';
    END IF;
    IF NEW.deposito_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM deposito_caja x JOIN caja_diaria d
            ON d.id = x.caja_diaria_id WHERE x.id = NEW.deposito_id AND x.creado_por <> NEW.creado_por
            AND d.cajero <> NEW.creado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: verifica un depósito alguien que no lo hizo';
    END IF;
END$$

DELIMITER ;
