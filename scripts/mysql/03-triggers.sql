-- Cuentas Claras · triggers de MySQL (paso 3, después de migrar). Los aplica cc_migrador:
--   mysql -h <host> -u cc_migrador -p cuentasclaras < scripts/mysql/03-triggers.sql
-- Requisito (una vez, como administrador, porque el binlog está activo):
--   SET PERSIST log_bin_trust_function_creators = 1;
-- No van en Flyway: H2 (desarrollo y pruebas) no los soporta. La aplicación en prod NO ARRANCA si falta alguno:
-- VerificadorPermisosBaseDatos compara la función cuentasclaras.triggers_instalados() (02-permisos-tablas.sql) con la lista
-- completa de este archivo (una prueba exige que las dos coincidan) y además prueba varios con un INSERT imposible
-- que el trigger rechaza con el error 1644.
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
    -- Correcciones del sprint 3 (M1): se anula solo con SU solicitud APROBADA el mismo día, por quien figura como
    -- aprobador: la de anulación de esta cuota o el ingreso tardío de su matrícula. El enlace no cambia después.
    IF OLD.estado <> 'ANULADA' AND NEW.estado = 'ANULADA' AND NOT EXISTS (SELECT 1 FROM solicitud_cambio s
            WHERE s.id = NEW.anulacion_solicitud_id AND s.estado = 'APROBADA'
            AND s.solicitado_por = NEW.anulacion_solicitada_por AND s.resuelto_por = NEW.anulacion_aprobada_por
            AND DATE(s.resuelto_en) = DATE(NEW.anulada_en)
            AND ((s.tipo = 'ANULACION_CUOTA' AND s.entidad = 'cuota' AND s.entidad_id = NEW.id)
                OR (s.tipo = 'FECHA_MATRICULA' AND s.entidad = 'matricula' AND s.entidad_id = NEW.matricula_id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la cuota se anula solo con su solicitud aprobada';
    END IF;
    IF NOT (NEW.anulacion_solicitud_id <=> OLD.anulacion_solicitud_id)
            AND NOT (OLD.estado <> 'ANULADA' AND NEW.estado = 'ANULADA') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la solicitud de la anulación no cambia';
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

-- (Reemplaza la versión del sprint 3.) El número es el siguiente de la serie; la nota de crédito usa la letra del
-- comprobante que anula; la reemisión solo reemplaza a un comprobante RECHAZADO del mismo tipo y total.
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
    IF NEW.reemplaza_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM comprobante r WHERE r.id = NEW.reemplaza_id
            AND r.estado_envio = 'RECHAZADO' AND r.tipo = NEW.tipo AND r.total = NEW.total
            AND r.modifica_id <=> NEW.modifica_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: solo se reemite un comprobante RECHAZADO del mismo tipo y total';
    END IF;
    IF NOT (NEW.estado_envio <=> 'PENDIENTE') OR NOT (NEW.intentos <=> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un comprobante nace PENDIENTE de envío';
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

-- Cerrar exige el cierre registrado; reabrir exige SU solicitud de reapertura (enlazada por id, una sola vez) APROBADA
-- el mismo día de la caja por alguien que no es su cajero, y reinicia el conteo. El primer conteo no se reescribe (no se
-- puede «tantear» el esperado contando una y otra vez).
DROP TRIGGER IF EXISTS trg_caja_diaria_estado$$
CREATE TRIGGER trg_caja_diaria_estado BEFORE UPDATE ON caja_diaria FOR EACH ROW
BEGIN
    DECLARE reabre BOOLEAN DEFAULT (OLD.estado = 'CERRADA' AND NEW.estado = 'ABIERTA');
    IF OLD.estado = 'ABIERTA' AND NEW.estado = 'CERRADA' AND (NOT (NEW.cierres <=> OLD.cierres + 1)
            OR NOT EXISTS (SELECT 1 FROM cierre_caja c WHERE c.caja_diaria_id = NEW.id AND c.numero = NEW.cierres)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la caja se cierra con un cierre registrado';
    END IF;
    IF reabre AND (NOT (NEW.cierres <=> OLD.cierres) OR NOT (NEW.conteos <=> 0)
            OR (NEW.reapertura_solicitud_id <=> OLD.reapertura_solicitud_id)
            OR NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.id = NEW.reapertura_solicitud_id
                AND s.tipo = 'REAPERTURA_CAJA' AND s.entidad = 'caja_diaria' AND s.entidad_id = NEW.id
                AND s.estado = 'APROBADA' AND DATE(s.resuelto_en) = NEW.fecha AND s.resuelto_por <> NEW.cajero)
            OR EXISTS (SELECT 1 FROM deposito_caja x WHERE x.caja_diaria_id = NEW.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la caja solo se reabre con una reapertura aprobada y sin depósito';
    END IF;
    IF NOT reabre AND NOT (NEW.reapertura_solicitud_id <=> OLD.reapertura_solicitud_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la solicitud de reapertura solo cambia al reabrir';
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

-- (Reemplaza la versión del sprint 3; versión final del sprint 4, tanda 2.) Un pago nace VIGENTE con su comprobante del
-- mismo total; efectivo solo en caja ABIERTA; el reemplazo, de un pago anulado por CORRECCIÓN; el pago en línea, de una
-- orden CONFIRMADA por la pasarela con ese monto, operación y medio (o, si la orden quedó POR_REVISAR, con la aplicación
-- aprobada); el pago de recaudación, de SU línea, con el lote ya CONFIRMADO a ciegas por otra persona, por el monto, la
-- operación y la fecha de la línea, para la familia del alumno del código (o la que aprobó otra persona si la línea
-- quedó en excepción).
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
    IF NEW.origen = 'REEMPLAZO' AND NOT EXISTS (SELECT 1 FROM pago r JOIN anulacion_pago n ON n.pago_id = r.id
            WHERE r.id = NEW.reemplaza_pago_id AND n.tipo = 'CORRECCION'
            AND r.estado = 'ANULADO' AND r.caja_diaria_id = NEW.caja_diaria_id AND r.medio = NEW.medio
            AND r.total = NEW.total) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el reemplazo debe ser del pago anulado (misma caja, medio y total)';
    END IF;
    IF NEW.origen = 'PASARELA' AND NOT EXISTS (SELECT 1 FROM orden_pago o WHERE o.id = NEW.orden_pago_id
            AND o.operacion = NEW.numero_operacion AND o.monto_confirmado = NEW.total AND o.moneda_confirmada = 'PEN'
            AND o.medio_confirmado = NEW.medio
            AND ((o.estado IN ('CREADA', 'VENCIDA') AND o.familia_id = NEW.familia_id AND o.monto = NEW.total)
                OR (o.estado = 'POR_REVISAR' AND EXISTS (SELECT 1 FROM solicitud_cambio s
                    WHERE s.tipo = 'APLICAR_INGRESO' AND s.entidad = 'orden_pago' AND s.entidad_id = o.id
                    AND s.estado = 'APROBADA')))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: pago en línea sin la confirmación de la pasarela por ese monto';
    END IF;
    IF NEW.origen = 'RECAUDACION' AND NOT EXISTS (SELECT 1 FROM linea_recaudacion l
            JOIN lote_recaudacion t ON t.id = l.lote_id
            WHERE l.id = NEW.linea_recaudacion_id AND t.estado IN ('CONFIRMADO', 'APLICADO') AND l.monto = NEW.total
            AND l.moneda = 'PEN' AND l.numero_operacion = NEW.numero_operacion AND l.fecha_pago = NEW.fecha
            AND ((l.estado = 'PENDIENTE' AND EXISTS (SELECT 1 FROM alumno a WHERE a.id = l.alumno_id
                    AND a.familia_id = NEW.familia_id))
                OR (l.estado = 'EXCEPCION' AND EXISTS (SELECT 1 FROM solicitud_cambio s
                    WHERE s.tipo = 'APLICAR_INGRESO' AND s.entidad = 'linea_recaudacion' AND s.entidad_id = l.id
                    AND s.estado = 'APROBADA')))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: pago de recaudación sin su línea confirmada por ese monto';
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

-- Un descuento nace SOLICITADO; resuelto no vuelve a cambiar (ni su estado ni quién y cuándo lo resolvió).
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
    IF OLD.estado <> 'SOLICITADO' AND (NOT (NEW.estado <=> OLD.estado) OR NOT (NEW.resuelto_por <=> OLD.resuelto_por)
            OR NOT (NEW.resuelto_en <=> OLD.resuelto_en)) THEN
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

-- Un cierre revisado no cambia: ni su estado ni quién, cuándo y con qué comentario lo revisó (M1).
DROP TRIGGER IF EXISTS trg_cierre_caja_revisado$$
CREATE TRIGGER trg_cierre_caja_revisado BEFORE UPDATE ON cierre_caja FOR EACH ROW
BEGIN
    IF OLD.estado <> 'POR_REVISAR' AND (NOT (NEW.estado <=> OLD.estado) OR NOT (NEW.revisado_por <=> OLD.revisado_por)
            OR NOT (NEW.revisado_en <=> OLD.revisado_en)
            OR NOT (NEW.comentario_revision <=> OLD.comentario_revision)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un cierre revisado no cambia';
    END IF;
END$$

-- (Reemplaza la versión de las correcciones del sprint 3.) Verifica alguien que no cobró ni depositó. MANUAL: lo
-- escrito a ciegas coincide con el pago o el depósito. AUTOMATICA: sale de una partida CONFIRMADA sobre un extracto
-- CONFIRMADO que cubre ese pago (directo, por su liquidación o por su lote) o ese depósito, y la inserta solo
-- sistema.conciliacion (tanda 3: más estricto que el diseño). Correcciones del sprint 4 (S4-C1): en un pago o depósito
-- directo, lo «visto en el banco» es el monto del MOVIMIENTO y debe ser el del pago o depósito.
DROP TRIGGER IF EXISTS trg_verificacion_bancaria_registro$$
CREATE TRIGGER trg_verificacion_bancaria_registro BEFORE INSERT ON verificacion_bancaria FOR EACH ROW
BEGIN
    IF NEW.pago_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.id = NEW.pago_id
            AND p.medio <> 'EFECTIVO' AND p.estado = 'VIGENTE' AND p.cajero <> NEW.creado_por
            AND p.creado_por <> NEW.creado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: verifica un pago digital vigente alguien que no lo cobró';
    END IF;
    IF NEW.deposito_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM deposito_caja x JOIN caja_diaria d
            ON d.id = x.caja_diaria_id WHERE x.id = NEW.deposito_id AND x.creado_por <> NEW.creado_por
            AND d.cajero <> NEW.creado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: verifica un depósito alguien que no lo hizo';
    END IF;
    IF NEW.origen = 'AUTOMATICA' THEN
        IF NOT (NEW.creado_por <=> 'sistema.conciliacion') OR NOT EXISTS (SELECT 1 FROM partida_conciliacion pc JOIN movimiento_bancario m ON m.id = pc.movimiento_id
                JOIN extracto_bancario e ON e.id = m.extracto_id
                WHERE pc.id = NEW.partida_id AND pc.estado = 'CONFIRMADA' AND e.estado = 'CONFIRMADO'
                AND m.fecha <=> NEW.banco_fecha
                AND ((pc.objeto_tipo = 'PAGO' AND pc.pago_id <=> NEW.pago_id AND m.monto <=> NEW.banco_monto
                        AND pc.monto_objeto <=> m.monto AND EXISTS (SELECT 1 FROM pago p2 WHERE p2.id = NEW.pago_id
                            AND p2.total <=> m.monto))
                    OR (pc.objeto_tipo = 'DEPOSITO' AND pc.deposito_id <=> NEW.deposito_id AND m.monto <=> NEW.banco_monto
                        AND pc.monto_objeto <=> m.monto AND EXISTS (SELECT 1 FROM deposito_caja x2
                            WHERE x2.id = NEW.deposito_id AND x2.monto <=> m.monto))
                    OR (pc.objeto_tipo = 'LIQUIDACION' AND EXISTS (SELECT 1 FROM liquidacion_linea l
                        WHERE l.liquidacion_id = pc.liquidacion_id AND l.pago_id <=> NEW.pago_id AND l.tipo = 'CARGO'
                        AND l.bruto <=> NEW.banco_monto))
                    OR (pc.objeto_tipo = 'LOTE_RECAUDACION' AND EXISTS (SELECT 1 FROM pago p
                        JOIN linea_recaudacion l ON l.id = p.linea_recaudacion_id WHERE p.id <=> NEW.pago_id
                        AND l.lote_id = pc.lote_recaudacion_id AND p.total <=> NEW.banco_monto)))) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: verificación automática sin partida confirmada que la cubra';
        END IF;
    ELSE
        IF NEW.resultado = 'ENCONTRADO' AND NEW.pago_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pago p
                WHERE p.id = NEW.pago_id AND p.numero_operacion = NEW.banco_operacion AND p.total = NEW.banco_monto
                AND NEW.banco_fecha BETWEEN p.fecha AND DATE_ADD(p.fecha, INTERVAL 3 DAY)) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: lo visto en el banco no coincide con el pago';
        END IF;
        IF NEW.resultado = 'ENCONTRADO' AND NEW.deposito_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM deposito_caja x
                WHERE x.id = NEW.deposito_id AND x.numero_operacion = NEW.banco_operacion AND x.monto = NEW.banco_monto
                AND x.fecha_deposito = NEW.banco_fecha) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: lo visto en el banco no coincide con el depósito';
        END IF;
    END IF;
END$$

-- Correcciones del sprint 3 (A1 y A2). El reembolso es de una DEVOLUCIÓN, por su monto y por el medio del pago, y no
-- lo registra la cajera del pago.
DROP TRIGGER IF EXISTS trg_reembolso_registro$$
CREATE TRIGGER trg_reembolso_registro BEFORE INSERT ON reembolso FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM anulacion_pago n JOIN pago p ON p.id = n.pago_id WHERE n.id = NEW.anulacion_pago_id
            AND n.tipo = 'DEVOLUCION' AND n.monto = NEW.monto AND p.medio = NEW.medio AND n.cajero_pago = NEW.cajero_pago
            AND p.cajero <> NEW.creado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el reembolso no corresponde a la devolución o lo registra la cajera';
    END IF;
END$$

-- M1. Una solicitud resuelta no cambia: ni su estado ni quién, cuándo y con qué comentario la resolvió.
DROP TRIGGER IF EXISTS trg_solicitud_cambio_resuelta$$
CREATE TRIGGER trg_solicitud_cambio_resuelta BEFORE UPDATE ON solicitud_cambio FOR EACH ROW
BEGIN
    IF OLD.estado <> 'PENDIENTE' AND (NOT (NEW.estado <=> OLD.estado) OR NOT (NEW.resuelto_por <=> OLD.resuelto_por)
            OR NOT (NEW.resuelto_en <=> OLD.resuelto_en) OR NOT (NEW.comentario <=> OLD.comentario)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una solicitud resuelta no cambia';
    END IF;
END$$

-- (Reemplaza la versión de las correcciones del sprint 3.) Outbox del OSE: los intentos avanzan de uno en uno; ACEPTADO
-- u OBSERVADO exigen hash, respuesta, envío y aceptación; un resultado definitivo (ACEPTADO, OBSERVADO, RECHAZADO) ya
-- no cambia en nada.
DROP TRIGGER IF EXISTS trg_comprobante_envio$$
CREATE TRIGGER trg_comprobante_envio BEFORE UPDATE ON comprobante FOR EACH ROW
BEGIN
    IF OLD.estado_envio IN ('ACEPTADO', 'OBSERVADO', 'RECHAZADO') AND (NOT (NEW.estado_envio <=> OLD.estado_envio)
            OR NOT (NEW.codigo_hash <=> OLD.codigo_hash) OR NOT (NEW.respuesta <=> OLD.respuesta)
            OR NOT (NEW.enviado_en <=> OLD.enviado_en) OR NOT (NEW.enlace_pdf <=> OLD.enlace_pdf)
            OR NOT (NEW.intentos <=> OLD.intentos) OR NOT (NEW.codigo_respuesta <=> OLD.codigo_respuesta)
            OR NOT (NEW.aceptado_en <=> OLD.aceptado_en) OR NOT (NEW.proximo_intento_en <=> OLD.proximo_intento_en)
            OR NOT (NEW.ultimo_error <=> OLD.ultimo_error)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el envío ya resuelto de un comprobante no cambia';
    END IF;
    IF NOT (NEW.intentos <=> OLD.intentos) AND NOT (NEW.intentos <=> OLD.intentos + 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los intentos de envío avanzan de uno en uno';
    END IF;
    IF NEW.estado_envio IN ('ACEPTADO', 'OBSERVADO') AND NOT (NEW.estado_envio <=> OLD.estado_envio)
            AND (NEW.codigo_hash IS NULL OR NEW.respuesta IS NULL OR NEW.enviado_en IS NULL OR NEW.aceptado_en IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: ACEPTADO exige el hash, la respuesta y el envío';
    END IF;
END$$

-- B2. Un apoderado nace sin datos de facturación; el RUC y la razón social solo cambian con SU solicitud
-- DATOS_FACTURACION aprobada (enlazada por id, una sola vez).
DROP TRIGGER IF EXISTS trg_apoderado_nace$$
CREATE TRIGGER trg_apoderado_nace BEFORE INSERT ON apoderado FOR EACH ROW
BEGIN
    IF NEW.ruc IS NOT NULL OR NEW.razon_social IS NOT NULL OR NEW.facturacion_solicitud_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el RUC del apoderado se registra con una solicitud aprobada';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_apoderado_facturacion$$
CREATE TRIGGER trg_apoderado_facturacion BEFORE UPDATE ON apoderado FOR EACH ROW
BEGIN
    IF (NOT (NEW.ruc <=> OLD.ruc) OR NOT (NEW.razon_social <=> OLD.razon_social)
            OR NOT (NEW.facturacion_solicitud_id <=> OLD.facturacion_solicitud_id))
            AND ((NEW.facturacion_solicitud_id <=> OLD.facturacion_solicitud_id)
                OR NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.id = NEW.facturacion_solicitud_id
                    AND s.tipo = 'DATOS_FACTURACION' AND s.entidad = 'apoderado' AND s.entidad_id = NEW.id
                    AND s.estado = 'APROBADA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el RUC del apoderado solo cambia con su solicitud aprobada';
    END IF;
END$$

-- ===================== Sprint 4 · tanda 1 (V13): pagos en línea y outbox del OSE =====================
-- En la tanda 1, trg_pago_registro iba en su versión REDUCIDA (sin la rama RECAUDACION): un trigger que nombra una tabla
-- inexistente hace fallar con 1146 todo INSERT o UPDATE sobre su tabla. Con V14 (tanda 2) pasa a su versión final.

-- La orden nace CREADA, sin enlace ni confirmación. La pasarela SIMULADA solo existe en una base que el DBA habilitó
-- (configuracion_bd, sin GRANT para cc_app): en producción esa fila no existe y la orden simulada se rechaza.
DROP TRIGGER IF EXISTS trg_orden_pago_nace$$
CREATE TRIGGER trg_orden_pago_nace BEFORE INSERT ON orden_pago FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'CREADA') OR NEW.proveedor_orden_id IS NOT NULL OR NEW.cargo_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una orden de pago nace CREADA, sin enlace ni confirmación';
    END IF;
    IF NEW.proveedor = 'SIMULADA' AND NOT EXISTS (SELECT 1 FROM configuracion_bd c
            WHERE c.clave = 'pasarela_simulada' AND c.valor = 'PERMITIDA') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: esta base no admite la pasarela simulada';
    END IF;
END$$

-- Las cuotas de la orden: de SU familia, por pagar, y solo antes de enviarla a la pasarela.
DROP TRIGGER IF EXISTS trg_orden_pago_cuota_registro$$
CREATE TRIGGER trg_orden_pago_cuota_registro BEFORE INSERT ON orden_pago_cuota FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM orden_pago o JOIN cuota c ON c.id = NEW.cuota_id JOIN alumno a ON a.id = c.alumno_id
            WHERE o.id = NEW.orden_pago_id AND o.estado = 'CREADA' AND o.proveedor_orden_id IS NULL
            AND a.familia_id = o.familia_id AND c.colegio_id = o.colegio_id AND c.estado IN ('PENDIENTE', 'PARCIAL')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la orden solo lleva cuotas por pagar de su familia';
    END IF;
END$$

-- Enlace con la pasarela una sola vez (y con las cuotas completas); confirmación una sola vez; transiciones válidas;
-- PAGADA/APLICADA exigen su pago vigente y DEVUELTA, la devolución aprobada.
DROP TRIGGER IF EXISTS trg_orden_pago_estado$$
CREATE TRIGGER trg_orden_pago_estado BEFORE UPDATE ON orden_pago FOR EACH ROW
BEGIN
    IF NOT (NEW.proveedor_orden_id <=> OLD.proveedor_orden_id) AND (OLD.proveedor_orden_id IS NOT NULL
            OR NOT (NEW.estado <=> 'CREADA') OR NOT (NEW.monto <=> (SELECT COALESCE(SUM(x.monto), 0.00)
                FROM orden_pago_cuota x WHERE x.orden_pago_id = NEW.id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el enlace con la pasarela se registra una vez y con sus cuotas';
    END IF;
    IF OLD.cargo_id IS NOT NULL AND (NOT (NEW.cargo_id <=> OLD.cargo_id) OR NOT (NEW.operacion <=> OLD.operacion)
            OR NOT (NEW.monto_confirmado <=> OLD.monto_confirmado) OR NOT (NEW.moneda_confirmada <=> OLD.moneda_confirmada)
            OR NOT (NEW.medio_confirmado <=> OLD.medio_confirmado) OR NOT (NEW.confirmado_en <=> OLD.confirmado_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la confirmación de la pasarela no cambia';
    END IF;
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (
            (OLD.estado = 'CREADA' AND NEW.estado IN ('PAGADA', 'POR_REVISAR', 'VENCIDA', 'RECHAZADA'))
            OR (OLD.estado = 'VENCIDA' AND NEW.estado IN ('PAGADA', 'POR_REVISAR'))
            OR (OLD.estado = 'POR_REVISAR' AND NEW.estado IN ('APLICADA', 'DEVUELTA'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado de la orden no permitido';
    END IF;
    IF NEW.estado IN ('PAGADA', 'APLICADA') AND NOT (NEW.estado <=> OLD.estado)
            AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.orden_pago_id = NEW.id AND p.estado = 'VIGENTE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una orden pagada necesita su pago registrado';
    END IF;
    IF NEW.estado = 'DEVUELTA' AND NOT (NEW.estado <=> OLD.estado) AND NOT EXISTS (SELECT 1 FROM solicitud_cambio s
            WHERE s.tipo = 'DEVOLVER_INGRESO' AND s.entidad = 'orden_pago' AND s.entidad_id = NEW.id
            AND s.estado = 'APROBADA' AND s.resuelto_por <> NEW.devuelto_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la devolución necesita su aprobación';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 4 · tanda 2 (V14): recaudación bancaria =====================
DELIMITER $$

-- El lote nace CARGADO, sin confirmar, sin aplicar y sin intentos.
DROP TRIGGER IF EXISTS trg_lote_recaudacion_nace$$
CREATE TRIGGER trg_lote_recaudacion_nace BEFORE INSERT ON lote_recaudacion FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'CARGADO') OR NOT (NEW.intentos_confirmacion <=> 0) OR NOT (NEW.lineas_aplicadas <=> 0)
            OR NOT (NEW.lineas_excepcion <=> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un lote de recaudación nace CARGADO y sin aplicar';
    END IF;
END$$

-- CARGADO → CONFIRMADO | RECHAZADO | DESCARTADO; CONFIRMADO → APLICADO. Los intentos de confirmación a ciegas solo
-- suben de uno en uno mientras está CARGADO. APLICADO: las líneas suman lo declarado y ninguna quedó PENDIENTE.
DROP TRIGGER IF EXISTS trg_lote_recaudacion_estado$$
CREATE TRIGGER trg_lote_recaudacion_estado BEFORE UPDATE ON lote_recaudacion FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> OLD.estado) AND NOT ((OLD.estado = 'CARGADO'
            AND NEW.estado IN ('CONFIRMADO', 'RECHAZADO', 'DESCARTADO'))
            OR (OLD.estado = 'CONFIRMADO' AND NEW.estado = 'APLICADO')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado del lote no permitido';
    END IF;
    IF NOT (NEW.intentos_confirmacion <=> OLD.intentos_confirmacion) AND (OLD.estado <> 'CARGADO'
            OR NOT (NEW.intentos_confirmacion <=> OLD.intentos_confirmacion + 1)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los intentos de confirmación no se reescriben';
    END IF;
    IF OLD.estado IN ('CONFIRMADO', 'APLICADO') AND (NOT (NEW.confirmado_por <=> OLD.confirmado_por)
            OR NOT (NEW.confirmado_en <=> OLD.confirmado_en) OR NOT (NEW.total_ciego <=> OLD.total_ciego)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la confirmación del lote no cambia';
    END IF;
    IF NEW.estado = 'APLICADO' AND OLD.estado <> 'APLICADO' AND (
            EXISTS (SELECT 1 FROM linea_recaudacion l WHERE l.lote_id = NEW.id AND l.estado = 'PENDIENTE')
            OR NOT (NEW.lineas <=> (SELECT COUNT(*) FROM linea_recaudacion l WHERE l.lote_id = NEW.id))
            OR NOT (NEW.total <=> (SELECT SUM(l.monto) FROM linea_recaudacion l WHERE l.lote_id = NEW.id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el lote se aplica completo y con sus líneas';
    END IF;
END$$

-- Las líneas entran solo con el lote CARGADO, como PENDIENTE y dentro de sus fechas.
DROP TRIGGER IF EXISTS trg_linea_recaudacion_registro$$
CREATE TRIGGER trg_linea_recaudacion_registro BEFORE INSERT ON linea_recaudacion FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'PENDIENTE') OR NOT EXISTS (SELECT 1 FROM lote_recaudacion t WHERE t.id = NEW.lote_id
            AND t.estado = 'CARGADO' AND NEW.fecha_pago BETWEEN t.desde AND t.hasta) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la línea entra PENDIENTE a un lote CARGADO';
    END IF;
END$$

-- PENDIENTE → APLICADA (con su pago) | EXCEPCION; EXCEPCION → APLICADA_REVISION (con su pago) | DEVUELTA (con la
-- devolución aprobada por otra persona). Nada más cambia.
DROP TRIGGER IF EXISTS trg_linea_recaudacion_estado$$
CREATE TRIGGER trg_linea_recaudacion_estado BEFORE UPDATE ON linea_recaudacion FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> OLD.estado) AND NOT ((OLD.estado = 'PENDIENTE' AND NEW.estado IN ('APLICADA', 'EXCEPCION'))
            OR (OLD.estado = 'EXCEPCION' AND NEW.estado IN ('APLICADA_REVISION', 'DEVUELTA'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado de la línea no permitido';
    END IF;
    IF NEW.estado IN ('APLICADA', 'APLICADA_REVISION') AND NOT (NEW.estado <=> OLD.estado)
            AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.linea_recaudacion_id = NEW.id AND p.estado = 'VIGENTE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una línea aplicada necesita su pago';
    END IF;
    IF NEW.estado = 'DEVUELTA' AND NOT (NEW.estado <=> OLD.estado) AND NOT EXISTS (SELECT 1 FROM solicitud_cambio s
            WHERE s.tipo = 'DEVOLVER_INGRESO' AND s.entidad = 'linea_recaudacion' AND s.entidad_id = NEW.id
            AND s.estado = 'APROBADA' AND s.resuelto_por <> NEW.devuelto_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la devolución necesita su aprobación';
    END IF;
    IF OLD.estado <> 'PENDIENTE' AND NOT (NEW.motivo_excepcion <=> OLD.motivo_excepcion) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el motivo de la excepción no cambia';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 4 · tanda 3 (V15): extracto y conciliación automática =====================
DELIMITER $$

-- Continuidad: el extracto n+1 de una cuenta empieza el día siguiente al fin del n, con su saldo final, y el n sigue
-- vigente. Nace CARGADO, sin confirmar y sin saldo ciego.
DROP TRIGGER IF EXISTS trg_extracto_bancario_nace$$
CREATE TRIGGER trg_extracto_bancario_nace BEFORE INSERT ON extracto_bancario FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'CARGADO') OR NOT (NEW.intentos_confirmacion <=> 0) OR NEW.saldo_final_ciego IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un extracto nace CARGADO y sin confirmar';
    END IF;
    IF NEW.secuencia > 1 AND NOT EXISTS (SELECT 1 FROM extracto_bancario a WHERE a.id = NEW.anterior_id
            AND a.cuenta_id = NEW.cuenta_id AND a.estado IN ('CARGADO', 'CONFIRMADO') AND a.secuencia = NEW.secuencia - 1
            AND a.saldo_final = NEW.saldo_inicial AND NEW.desde = a.hasta + INTERVAL 1 DAY) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el extracto no continúa al anterior (fechas o saldo)';
    END IF;
END$$

-- CARGADO → CONFIRMADO | RECHAZADO | DESCARTADO. Confirmar exige el anterior ya confirmado, los movimientos completos
-- y el saldo final escrito a ciegas (en este extracto o en uno posterior de la cadena). No se descarta un extracto que
-- ya tiene uno siguiente vigente. Los intentos solo suben de uno en uno.
DROP TRIGGER IF EXISTS trg_extracto_bancario_estado$$
CREATE TRIGGER trg_extracto_bancario_estado BEFORE UPDATE ON extracto_bancario FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (OLD.estado = 'CARGADO'
            AND NEW.estado IN ('CONFIRMADO', 'RECHAZADO', 'DESCARTADO')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado del extracto no permitido';
    END IF;
    IF NOT (NEW.intentos_confirmacion <=> OLD.intentos_confirmacion) AND (OLD.estado <> 'CARGADO'
            OR NOT (NEW.intentos_confirmacion <=> OLD.intentos_confirmacion + 1)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los intentos de confirmación no se reescriben';
    END IF;
    IF NOT (NEW.saldo_final_ciego <=> OLD.saldo_final_ciego) AND (OLD.saldo_final_ciego IS NOT NULL
            OR OLD.estado <> 'CARGADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el saldo escrito a ciegas no se reescribe';
    END IF;
    IF NEW.estado = 'CONFIRMADO' AND OLD.estado = 'CARGADO' AND (
            (NEW.anterior_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM extracto_bancario a WHERE a.id = NEW.anterior_id
                AND a.estado = 'CONFIRMADO'))
            OR NOT (NEW.movimientos <=> (SELECT COUNT(*) FROM movimiento_bancario m WHERE m.extracto_id = NEW.id))
            OR NOT (NEW.total_abonos <=> (SELECT COALESCE(SUM(m.monto), 0.00) FROM movimiento_bancario m
                WHERE m.extracto_id = NEW.id AND m.tipo = 'ABONO'))
            OR NOT (NEW.total_cargos <=> (SELECT COALESCE(SUM(m.monto), 0.00) FROM movimiento_bancario m
                WHERE m.extracto_id = NEW.id AND m.tipo = 'CARGO'))
            OR NOT ((NEW.confirmacion_extracto_id <=> NEW.id AND NEW.saldo_final_ciego <=> NEW.saldo_final)
                OR EXISTS (SELECT 1 FROM extracto_bancario c WHERE c.id = NEW.confirmacion_extracto_id
                    AND c.cuenta_id = NEW.cuenta_id AND c.secuencia > NEW.secuencia AND c.estado = 'CARGADO'
                    AND c.saldo_final_ciego = c.saldo_final))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el extracto se confirma completo, en orden y con el saldo a ciegas';
    END IF;
    IF NEW.estado IN ('RECHAZADO', 'DESCARTADO') AND OLD.estado = 'CARGADO' AND EXISTS (SELECT 1
            FROM extracto_bancario s WHERE s.anterior_id = NEW.id AND s.estado IN ('CARGADO', 'CONFIRMADO')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: primero se rechaza el extracto siguiente';
    END IF;
END$$

-- Los movimientos entran con el extracto CARGADO y dentro de sus fechas.
DROP TRIGGER IF EXISTS trg_movimiento_bancario_registro$$
CREATE TRIGGER trg_movimiento_bancario_registro BEFORE INSERT ON movimiento_bancario FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM extracto_bancario e WHERE e.id = NEW.extracto_id AND e.estado = 'CARGADO'
            AND NEW.fecha BETWEEN e.desde AND e.hasta) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el movimiento entra a un extracto CARGADO y en sus fechas';
    END IF;
END$$

-- La partida nace PROPUESTA, sobre un movimiento de un extracto vigente, con los montos reales del movimiento y del
-- objeto (abono para lo que entra; cargo para un reembolso). EXACTA exige además la misma operación canónica.
DROP TRIGGER IF EXISTS trg_partida_conciliacion_registro$$
CREATE TRIGGER trg_partida_conciliacion_registro BEFORE INSERT ON partida_conciliacion FOR EACH ROW
BEGIN
    DECLARE tipo_mov VARCHAR(10) DEFAULT (SELECT m.tipo FROM movimiento_bancario m JOIN extracto_bancario e
        ON e.id = m.extracto_id WHERE m.id = NEW.movimiento_id AND e.estado IN ('CARGADO', 'CONFIRMADO')
        AND m.monto = NEW.monto_movimiento);
    IF NOT (NEW.estado <=> 'PROPUESTA') OR tipo_mov IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la partida nace PROPUESTA sobre un movimiento vigente';
    END IF;
    IF NEW.objeto_tipo <> 'EXPLICACION' AND NOT (NEW.objeto_vigente <=> CONCAT(NEW.objeto_tipo, ':',
            COALESCE(NEW.pago_id, NEW.deposito_id, NEW.liquidacion_id, NEW.lote_recaudacion_id, NEW.reembolso_id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la clave del objeto de la partida no corresponde';
    END IF;
    IF NOT ((NEW.objeto_tipo = 'EXPLICACION')
            OR (NEW.objeto_tipo = 'PAGO' AND tipo_mov = 'ABONO' AND EXISTS (SELECT 1 FROM pago p WHERE p.id = NEW.pago_id
                AND p.estado = 'VIGENTE' AND p.medio <> 'EFECTIVO' AND p.total = NEW.monto_objeto
                AND (NEW.regla <> 'EXACTA' OR p.numero_operacion = (SELECT m.numero_operacion FROM movimiento_bancario m
                    WHERE m.id = NEW.movimiento_id))))
            OR (NEW.objeto_tipo = 'DEPOSITO' AND tipo_mov = 'ABONO' AND EXISTS (SELECT 1 FROM deposito_caja x
                WHERE x.id = NEW.deposito_id AND x.monto = NEW.monto_objeto
                AND (NEW.regla <> 'EXACTA' OR x.numero_operacion = (SELECT m.numero_operacion FROM movimiento_bancario m
                    WHERE m.id = NEW.movimiento_id))))
            OR (NEW.objeto_tipo = 'LIQUIDACION' AND tipo_mov = 'ABONO' AND EXISTS (SELECT 1 FROM liquidacion_pasarela l
                WHERE l.id = NEW.liquidacion_id AND l.total_neto = NEW.monto_objeto))
            OR (NEW.objeto_tipo = 'LOTE_RECAUDACION' AND tipo_mov = 'ABONO' AND EXISTS (SELECT 1 FROM lote_recaudacion t
                WHERE t.id = NEW.lote_recaudacion_id AND t.estado IN ('CONFIRMADO', 'APLICADO') AND t.total = NEW.monto_objeto))
            OR (NEW.objeto_tipo = 'REEMBOLSO' AND tipo_mov = 'CARGO' AND EXISTS (SELECT 1 FROM reembolso r
                WHERE r.id = NEW.reembolso_id AND r.monto = NEW.monto_objeto AND r.medio <> 'EFECTIVO'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la partida no corresponde al movimiento ni a su objeto';
    END IF;
END$$

-- PROPUESTA → CONFIRMADA | DESCARTADA, una vez. Confirmar exige el extracto CONFIRMADO; si la confirma una persona
-- (SUGERIDA, MANUAL o EXPLICADA), no puede ser quien cobró, registró o depositó lo emparejado, ni quien subió el lote de
-- recaudación (también si el objeto es un pago de ese lote: S4-B2 y QA-S4-6). Correcciones del sprint 4: la MANUAL
-- exige SU solicitud PARTIDA_MANUAL aprobada por quien confirma, que no la pidió (S4-C1); las claves vigentes solo
-- valen la de su propio movimiento y objeto, y una partida resuelta ya no las cambia (S4-B1).
DROP TRIGGER IF EXISTS trg_partida_conciliacion_estado$$
CREATE TRIGGER trg_partida_conciliacion_estado BEFORE UPDATE ON partida_conciliacion FOR EACH ROW
BEGIN
    IF OLD.estado <> 'PROPUESTA' AND (NOT (NEW.estado <=> OLD.estado) OR NOT (NEW.resuelto_por <=> OLD.resuelto_por)
            OR NOT (NEW.resuelto_en <=> OLD.resuelto_en) OR NOT (NEW.nota <=> OLD.nota)
            OR NOT (NEW.categoria <=> OLD.categoria) OR NOT (NEW.movimiento_vigente <=> OLD.movimiento_vigente)
            OR NOT (NEW.objeto_vigente <=> OLD.objeto_vigente)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una partida resuelta no cambia';
    END IF;
    IF NOT (NEW.movimiento_vigente IS NULL OR NEW.movimiento_vigente <=> NEW.movimiento_id)
            OR NOT (NEW.objeto_vigente IS NULL OR (NEW.objeto_tipo <> 'EXPLICACION' AND NEW.objeto_vigente <=> CONCAT(
                NEW.objeto_tipo, ':', COALESCE(NEW.pago_id, NEW.deposito_id, NEW.liquidacion_id, NEW.lote_recaudacion_id,
                    NEW.reembolso_id)))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la clave vigente de la partida no corresponde';
    END IF;
    IF NEW.estado = 'CONFIRMADA' AND OLD.estado = 'PROPUESTA' AND (NOT EXISTS (SELECT 1 FROM movimiento_bancario m
            JOIN extracto_bancario e ON e.id = m.extracto_id WHERE m.id = NEW.movimiento_id AND e.estado = 'CONFIRMADO')
            OR (NEW.regla <> 'EXACTA' AND (
                EXISTS (SELECT 1 FROM pago p WHERE p.id = NEW.pago_id
                    AND (p.cajero = NEW.resuelto_por OR p.creado_por = NEW.resuelto_por))
                OR EXISTS (SELECT 1 FROM pago p JOIN linea_recaudacion l ON l.id = p.linea_recaudacion_id
                    JOIN lote_recaudacion t ON t.id = l.lote_id WHERE p.id = NEW.pago_id AND t.creado_por = NEW.resuelto_por)
                OR EXISTS (SELECT 1 FROM deposito_caja x JOIN caja_diaria d ON d.id = x.caja_diaria_id
                    WHERE x.id = NEW.deposito_id AND (x.creado_por = NEW.resuelto_por OR d.cajero = NEW.resuelto_por))
                OR EXISTS (SELECT 1 FROM reembolso r WHERE r.id = NEW.reembolso_id AND r.creado_por = NEW.resuelto_por)
                OR EXISTS (SELECT 1 FROM lote_recaudacion t WHERE t.id = NEW.lote_recaudacion_id
                    AND t.creado_por = NEW.resuelto_por)))
            OR (NEW.regla = 'MANUAL' AND NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.tipo = 'PARTIDA_MANUAL'
                AND s.entidad = 'partida_conciliacion' AND s.entidad_id = NEW.id AND s.estado = 'APROBADA'
                AND s.resuelto_por = NEW.resuelto_por AND s.solicitado_por <> NEW.resuelto_por))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la partida se confirma con el extracto confirmado y por otra persona';
    END IF;
END$$

DELIMITER ;
