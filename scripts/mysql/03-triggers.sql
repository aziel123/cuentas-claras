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

-- Correcciones del sprint 5 (S5-A1 y S5-M5): forma canónica de un celular o un correo para COMPARAR contactos, igual que
-- ContactoNormal.de en Java. Correo: minúsculas, sin lo que va desde el primer «+» de la parte local y, en gmail.com y
-- googlemail.com, sin puntos (googlemail.com es gmail.com). Celular: solo dígitos; uno peruano de 9 dígitos gana el 51.
-- No lee tablas (NO SQL); la usan trg_mensaje_nace y las alertas no la necesitan (las calcula la aplicación).
DROP FUNCTION IF EXISTS cc_contacto_normal$$
CREATE FUNCTION cc_contacto_normal(c VARCHAR(150)) RETURNS VARCHAR(150) DETERMINISTIC NO SQL
BEGIN
    DECLARE v VARCHAR(150) DEFAULT LOWER(TRIM(c));
    DECLARE dominio VARCHAR(150);
    DECLARE localpart VARCHAR(150);
    DECLARE digitos VARCHAR(150);
    IF v IS NULL OR v = '' THEN
        RETURN '';
    END IF;
    IF LOCATE('@', v) > 1 THEN
        SET dominio = SUBSTRING_INDEX(v, '@', -1);
        SET localpart = SUBSTRING_INDEX(LEFT(v, CHAR_LENGTH(v) - CHAR_LENGTH(dominio) - 1), '+', 1);
        IF dominio IN ('gmail.com', 'googlemail.com') THEN
            RETURN CONCAT(REPLACE(localpart, '.', ''), '@gmail.com');
        END IF;
        RETURN CONCAT(localpart, '@', dominio);
    END IF;
    SET digitos = REGEXP_REPLACE(v, '[^0-9]', '');
    IF REGEXP_LIKE(digitos, '^9[0-9]{8}$') THEN
        RETURN CONCAT('51', digitos);
    END IF;
    RETURN digitos;
END$$

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

-- La anulación corresponde al pago vigente, a su cajero y a una nota de crédito que anula SU comprobante. Correcciones
-- del sprint 4 (S4-A3): una anulación por CONTRACARGO (sin reembolso) solo es de un pago en línea cuya orden registró
-- el contracargo de la pasarela.
DROP TRIGGER IF EXISTS trg_anulacion_pago_registro$$
CREATE TRIGGER trg_anulacion_pago_registro BEFORE INSERT ON anulacion_pago FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pago p JOIN comprobante n ON n.id = NEW.nota_credito_id
            WHERE p.id = NEW.pago_id AND p.estado = 'VIGENTE' AND p.cajero = NEW.cajero_pago AND p.total = NEW.monto
            AND n.tipo = 'NOTA_CREDITO' AND n.modifica_id = p.comprobante_id AND n.total = p.total) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: anulación que no corresponde al pago o a su nota de crédito';
    END IF;
    IF NEW.tipo = 'CONTRACARGO' AND NOT EXISTS (SELECT 1 FROM pago p JOIN orden_pago o ON o.id = p.orden_pago_id
            WHERE p.id = NEW.pago_id AND p.origen = 'PASARELA' AND o.contracargo_en IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: solo un pago en línea con contracargo se anula por contracargo';
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
-- lo registra la cajera del pago. Correcciones del sprint 4 (S4-A3): nunca de un pago de la pasarela (ese solo se
-- devuelve por su API: reembolso_pasarela); un contracargo no es una devolución.
DROP TRIGGER IF EXISTS trg_reembolso_registro$$
CREATE TRIGGER trg_reembolso_registro BEFORE INSERT ON reembolso FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM anulacion_pago n JOIN pago p ON p.id = n.pago_id WHERE n.id = NEW.anulacion_pago_id
            AND n.tipo = 'DEVOLUCION' AND n.monto = NEW.monto AND p.medio = NEW.medio AND n.cajero_pago = NEW.cajero_pago
            AND p.cajero <> NEW.creado_por AND p.origen <> 'PASARELA') THEN
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
-- Sprint 5 · tanda 1 (V17, hallazgo 4): además nace sin contacto «aprobado»; celular y correo solo cambian con SU
-- solicitud CAMBIO_CONTACTO_APODERADO aprobada, una por cambio.
DROP TRIGGER IF EXISTS trg_apoderado_nace$$
CREATE TRIGGER trg_apoderado_nace BEFORE INSERT ON apoderado FOR EACH ROW
BEGIN
    IF NEW.ruc IS NOT NULL OR NEW.razon_social IS NOT NULL OR NEW.facturacion_solicitud_id IS NOT NULL
            OR NEW.contacto_solicitud_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el RUC y el cambio de contacto se registran con una solicitud aprobada';
    END IF;
    -- Correcciones del sprint 5 (S5-A1 y S5-M1): nace sin contactos verificados ni aprobados.
    IF NEW.telefono_verificado IS NOT NULL OR NEW.correo_verificado IS NOT NULL
            OR NEW.contacto_aprobado_telefono IS NOT NULL OR NEW.contacto_aprobado_correo IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el apoderado nace con sus contactos sin verificar ni aprobar';
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
    IF (NOT (NEW.telefono_whatsapp <=> OLD.telefono_whatsapp) OR NOT (NEW.correo <=> OLD.correo)
            OR NOT (NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id))
            AND ((NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id)
                OR NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.id = NEW.contacto_solicitud_id
                    AND s.tipo = 'CAMBIO_CONTACTO_APODERADO' AND s.entidad = 'apoderado' AND s.entidad_id = NEW.id
                    AND s.estado = 'APROBADA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el contacto del apoderado solo cambia con su solicitud aprobada';
    END IF;
    -- S5-M1: el contacto aprobado lo fija una solicitud aprobada nueva y es el contacto registrado de ese canal.
    IF (NOT (NEW.contacto_aprobado_telefono <=> OLD.contacto_aprobado_telefono)
                AND ((NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id)
                    OR NOT (NEW.contacto_aprobado_telefono <=> NEW.telefono_whatsapp)))
            OR (NOT (NEW.contacto_aprobado_correo <=> OLD.contacto_aprobado_correo)
                AND ((NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id)
                    OR NOT (NEW.contacto_aprobado_correo <=> NEW.correo))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el contacto aprobado sale de su solicitud aprobada';
    END IF;
    -- S5-A1: un contacto queda verificado solo con SU verificación usada (el enlace que recibió ese contacto).
    IF NOT (NEW.telefono_verificado <=> OLD.telefono_verificado) AND (NOT (NEW.telefono_verificado <=> NEW.telefono_whatsapp)
            OR NOT EXISTS (SELECT 1 FROM verificacion_contacto v WHERE v.apoderado_id = NEW.id
                AND v.colegio_id = NEW.colegio_id AND v.canal = 'WHATSAPP' AND v.contacto = NEW.telefono_verificado
                AND v.verificado_en IS NOT NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el celular se verifica solo con su enlace';
    END IF;
    IF NOT (NEW.correo_verificado <=> OLD.correo_verificado) AND (NOT (NEW.correo_verificado <=> NEW.correo)
            OR NOT EXISTS (SELECT 1 FROM verificacion_contacto v WHERE v.apoderado_id = NEW.id
                AND v.colegio_id = NEW.colegio_id AND v.canal = 'CORREO' AND v.contacto = NEW.correo_verificado
                AND v.verificado_en IS NOT NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el correo se verifica solo con su enlace';
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
    -- Correcciones del sprint 4 (S4-A3): el contracargo se registra una vez y no cambia; con contracargo, un ingreso por
    -- revisar ya no se aplica ni se devuelve (el banco ya devolvió el dinero al apoderado).
    IF OLD.contracargo_en IS NOT NULL AND (NOT (NEW.contracargo_en <=> OLD.contracargo_en)
            OR NOT (NEW.contracargo_origen <=> OLD.contracargo_origen)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el contracargo de la orden no cambia';
    END IF;
    IF NEW.contracargo_en IS NOT NULL AND OLD.estado = 'POR_REVISAR' AND NEW.estado IN ('APLICADA', 'DEVUELTA') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un ingreso con contracargo no se aplica ni se devuelve';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 4 · tanda 2 (V14): recaudación bancaria =====================
DELIMITER $$

-- El lote nace CARGADO, sin confirmar, sin aplicar y sin intentos, con su muestra fija (correcciones del sprint 4,
-- S4-A1).
DROP TRIGGER IF EXISTS trg_lote_recaudacion_nace$$
CREATE TRIGGER trg_lote_recaudacion_nace BEFORE INSERT ON lote_recaudacion FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'CARGADO') OR NOT (NEW.intentos_confirmacion <=> 0) OR NOT (NEW.lineas_aplicadas <=> 0)
            OR NOT (NEW.lineas_excepcion <=> 0) OR NEW.muestra IS NULL THEN
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
-- devolución aprobada por otra persona y ejecutada por alguien que no la pidió ni la aprobó: correcciones del sprint 4,
-- S4-A4). Nada más cambia; la cuenta de destino se escribe al devolverla y ya no cambia.
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
            AND s.estado = 'APROBADA' AND s.resuelto_por <> NEW.devuelto_por AND s.solicitado_por <> NEW.devuelto_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la devolución necesita su aprobación';
    END IF;
    IF OLD.estado <> 'PENDIENTE' AND NOT (NEW.motivo_excepcion <=> OLD.motivo_excepcion) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el motivo de la excepción no cambia';
    END IF;
    IF OLD.estado = 'DEVUELTA' AND (NOT (NEW.devolucion_banco <=> OLD.devolucion_banco)
            OR NOT (NEW.devolucion_cuenta <=> OLD.devolucion_cuenta) OR NOT (NEW.devolucion_titular <=> OLD.devolucion_titular)
            OR NOT (NEW.devolucion_operacion <=> OLD.devolucion_operacion)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la devolución de la línea no cambia';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 4 · tanda 3 (V15): extracto y conciliación automática =====================
DELIMITER $$

-- Continuidad: el extracto n+1 de una cuenta empieza el día siguiente al fin del n, con su saldo final, y el n sigue
-- vigente. Nace CARGADO, sin confirmar y sin saldo ciego, con su muestra fija y su semilla secreta (correcciones del
-- sprint 4, S4-A1 y S4-A2).
DROP TRIGGER IF EXISTS trg_extracto_bancario_nace$$
CREATE TRIGGER trg_extracto_bancario_nace BEFORE INSERT ON extracto_bancario FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'CARGADO') OR NOT (NEW.intentos_confirmacion <=> 0) OR NEW.saldo_final_ciego IS NOT NULL
            OR NEW.muestra IS NULL OR NEW.semilla_muestreo IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un extracto nace CARGADO y sin confirmar';
    END IF;
    IF NEW.secuencia > 1 AND NOT EXISTS (SELECT 1 FROM extracto_bancario a WHERE a.id = NEW.anterior_id
            AND a.cuenta_id = NEW.cuenta_id AND a.estado IN ('CARGADO', 'CONFIRMADO') AND a.secuencia = NEW.secuencia - 1
            AND a.saldo_final = NEW.saldo_inicial AND NEW.desde = a.hasta + INTERVAL 1 DAY) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el extracto no continúa al anterior (fechas o saldo)';
    END IF;
END$$

-- CARGADO → CONFIRMADO | RECHAZADO | DESCARTADO. Confirmar exige el anterior ya confirmado, los movimientos completos
-- y el saldo final de ESTE extracto escrito a ciegas (correcciones del sprint 4, S4-A2: cada extracto con su propio
-- saldo; ya no se sella una cadena con el saldo del último). No se descarta un extracto que ya tiene uno siguiente
-- vigente. Los intentos solo suben de uno en uno.
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
            OR NOT (NEW.confirmacion_extracto_id <=> NEW.id AND NEW.saldo_final_ciego <=> NEW.saldo_final)) THEN
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
-- objeto (abono para lo que entra; cargo para un reembolso o, desde las correcciones del sprint 4 (S4-A4), para la
-- devolución de una línea de recaudación). EXACTA exige además la misma operación canónica.
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
            COALESCE(NEW.pago_id, NEW.deposito_id, NEW.liquidacion_id, NEW.lote_recaudacion_id, NEW.reembolso_id,
            NEW.linea_recaudacion_id))) THEN
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
                WHERE r.id = NEW.reembolso_id AND r.monto = NEW.monto_objeto AND r.medio <> 'EFECTIVO'))
            OR (NEW.objeto_tipo = 'LINEA_RECAUDACION' AND tipo_mov = 'CARGO' AND EXISTS (SELECT 1 FROM linea_recaudacion l
                WHERE l.id = NEW.linea_recaudacion_id AND l.estado = 'DEVUELTA' AND l.monto = NEW.monto_objeto
                AND (NEW.regla <> 'EXACTA' OR l.devolucion_operacion = (SELECT m.numero_operacion
                    FROM movimiento_bancario m WHERE m.id = NEW.movimiento_id))))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la partida no corresponde al movimiento ni a su objeto';
    END IF;
END$$

-- PROPUESTA → CONFIRMADA | DESCARTADA, una vez. Confirmar exige el extracto CONFIRMADO; si la confirma una persona
-- (SUGERIDA, MANUAL o EXPLICADA), no puede ser quien cobró, registró o depositó lo emparejado, ni quien subió el lote de
-- recaudación (también si el objeto es un pago de ese lote: S4-B2 y QA-S4-6), y un CARGO no lo explica quien subió su
-- extracto (S4-A2). Correcciones del sprint 4: la MANUAL
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
                    NEW.reembolso_id, NEW.linea_recaudacion_id)))) THEN
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
                    AND t.creado_por = NEW.resuelto_por)
                OR EXISTS (SELECT 1 FROM linea_recaudacion l JOIN lote_recaudacion t ON t.id = l.lote_id
                    WHERE l.id = NEW.linea_recaudacion_id AND (l.devuelto_por = NEW.resuelto_por
                        OR t.creado_por = NEW.resuelto_por))
                OR (NEW.regla = 'EXPLICADA' AND EXISTS (SELECT 1 FROM movimiento_bancario m JOIN extracto_bancario e
                    ON e.id = m.extracto_id WHERE m.id = NEW.movimiento_id AND m.tipo = 'CARGO'
                    AND e.creado_por = NEW.resuelto_por))))
            OR (NEW.regla = 'MANUAL' AND NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.tipo = 'PARTIDA_MANUAL'
                AND s.entidad = 'partida_conciliacion' AND s.entidad_id = NEW.id AND s.estado = 'APROBADA'
                AND s.resuelto_por = NEW.resuelto_por AND s.solicitado_por <> NEW.resuelto_por))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la partida se confirma con el extracto confirmado y por otra persona';
    END IF;
END$$

-- ===================== Correcciones del sprint 4 (V16) =====================
-- S4-A3. El reembolso de un pago en línea sale solo por la API de la pasarela: de una DEVOLUCIÓN (nunca de un
-- contracargo) de un pago de la pasarela, por su monto y su mismo cargo, si la orden no tuvo contracargo; no lo ejecuta
-- quien pidió ni quien aprobó la devolución.
DROP TRIGGER IF EXISTS trg_reembolso_pasarela_registro$$
CREATE TRIGGER trg_reembolso_pasarela_registro BEFORE INSERT ON reembolso_pasarela FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM anulacion_pago n JOIN pago p ON p.id = n.pago_id JOIN orden_pago o
            ON o.id = p.orden_pago_id WHERE n.id = NEW.anulacion_pago_id AND n.tipo = 'DEVOLUCION' AND p.id = NEW.pago_id
            AND p.origen = 'PASARELA' AND n.monto = NEW.monto AND o.cargo_id = NEW.cargo_id AND o.contracargo_en IS NULL
            AND NEW.creado_por <> n.aprobado_por AND NEW.creado_por <> n.solicitado_por) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el reembolso por la pasarela no corresponde a la devolución';
    END IF;
END$$

-- ===================== Sprint 5 · tanda 1 (V17): mensajes, acceso directo y huella =====================
-- (trg_mensaje_nace en su versión del sprint 6, tanda 2: nombra resumen_diario, que nace en V21. Nunca se instala
-- sobre una base sin V21: el job mysql aplica este archivo siempre después de migrar.)
-- El mensaje nace PENDIENTE y va al contacto REGISTRADO de su destinatario. A un apoderado no se le escribe a un contacto
-- que también es del personal, salvo que ese contacto lo haya aprobado otra persona. La activación del personal no va al
-- contacto de quien la pidió. EXTERNO: solo el correo que el DBA dejó en configuracion_bd.
DROP TRIGGER IF EXISTS trg_mensaje_nace$$
CREATE TRIGGER trg_mensaje_nace BEFORE INSERT ON mensaje FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'PENDIENTE') OR NOT (NEW.intentos <=> 0) OR NEW.proveedor IS NOT NULL
            OR NEW.proveedor_mensaje_id IS NOT NULL OR NEW.enviado_en IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un mensaje nace PENDIENTE y sin envío';
    END IF;
    -- Sprint 6 (tanda 2): el resumen y las alertas a Promotoría los crea solo sistema.panel. Va primero: así el INSERT
    -- imposible del verificador (destinatario 'X') llega aquí y no al CHECK, y prod no arranca con la versión anterior.
    IF NEW.tipo IN ('RESUMEN_DIARIO', 'ALERTA_PROMOTORIA') AND NOT (NEW.creado_por <=> 'sistema.panel') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el resumen y las alertas los envía sistema.panel';
    END IF;
    IF NEW.destinatario_tipo = 'APODERADO' AND NEW.tipo <> 'CONTACTO_CAMBIADO' AND NOT EXISTS (SELECT 1 FROM apoderado a
            WHERE a.id = NEW.apoderado_id AND a.colegio_id = NEW.colegio_id
            AND ((NEW.canal = 'WHATSAPP' AND a.telefono_whatsapp = NEW.destino)
                OR (NEW.canal = 'CORREO' AND a.correo = NEW.destino))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el mensaje va al contacto registrado del apoderado';
    END IF;
    -- G6 + S5-A1 + S5-M1: un contacto del personal (comparado NORMALIZADO: alias y formatos) solo recibe si otra persona
    -- aprobó EXACTAMENTE ese contacto para ese canal.
    IF NEW.destinatario_tipo = 'APODERADO' AND EXISTS (SELECT 1 FROM usuario u WHERE u.colegio_id = NEW.colegio_id
            AND u.activo AND u.apoderado_id IS NULL
            AND (cc_contacto_normal(u.telefono_whatsapp) = cc_contacto_normal(NEW.destino)
                OR cc_contacto_normal(u.correo) = cc_contacto_normal(NEW.destino)))
            AND NOT EXISTS (SELECT 1 FROM apoderado a WHERE a.id = NEW.apoderado_id
                AND ((NEW.canal = 'WHATSAPP' AND a.contacto_aprobado_telefono <=> NEW.destino)
                    OR (NEW.canal = 'CORREO' AND a.contacto_aprobado_correo <=> NEW.destino))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: ese contacto es del personal; debe aprobarlo otra persona';
    END IF;
    -- S5-A1: un contacto recibe avisos y enlaces solo si su titular lo verificó (salvo su propia verificación y el aviso
    -- al contacto anterior).
    IF NEW.destinatario_tipo = 'APODERADO' AND NEW.tipo NOT IN ('CONTACTO_CAMBIADO', 'VERIFICACION_CONTACTO')
            AND NOT EXISTS (SELECT 1 FROM apoderado a WHERE a.id = NEW.apoderado_id
                AND ((NEW.canal = 'WHATSAPP' AND a.telefono_verificado <=> NEW.destino)
                    OR (NEW.canal = 'CORREO' AND a.correo_verificado <=> NEW.destino))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el contacto del apoderado aún no está verificado';
    END IF;
    -- Sprint 6 (tanda 2): también el aviso al contacto ANTERIOR de alguien del personal, con SU CAMBIO_CONTACTO_PERSONAL
    -- aprobada y SOLO a ese contacto anterior (el que quedó en la solicitud).
    IF NEW.tipo = 'CONTACTO_CAMBIADO' AND NOT (NEW.entidad <=> 'solicitud_cambio' AND (
            (NEW.destinatario_tipo = 'APODERADO' AND EXISTS (SELECT 1
                FROM solicitud_cambio s WHERE s.id = NEW.entidad_id AND s.tipo = 'CAMBIO_CONTACTO_APODERADO'
                AND s.entidad = 'apoderado' AND s.entidad_id = NEW.apoderado_id AND s.estado = 'APROBADA'))
            OR (NEW.destinatario_tipo = 'USUARIO' AND EXISTS (SELECT 1
                FROM solicitud_cambio s WHERE s.id = NEW.entidad_id AND s.colegio_id = NEW.colegio_id
                AND s.tipo = 'CAMBIO_CONTACTO_PERSONAL' AND s.entidad = 'usuario' AND s.entidad_id = NEW.usuario_id
                AND s.estado = 'APROBADA'
                AND ((NEW.canal = 'WHATSAPP' AND JSON_UNQUOTE(JSON_EXTRACT(s.datos, '$.telefonoAnterior')) = NEW.destino)
                    OR (NEW.canal = 'CORREO' AND JSON_UNQUOTE(JSON_EXTRACT(s.datos, '$.correoAnterior')) = NEW.destino)))))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el aviso al contacto anterior necesita el cambio aprobado';
    END IF;
    IF NEW.destinatario_tipo = 'USUARIO' AND NEW.tipo <> 'CONTACTO_CAMBIADO' AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = NEW.usuario_id
            AND u.colegio_id = NEW.colegio_id
            AND ((NEW.canal = 'WHATSAPP' AND u.telefono_whatsapp = NEW.destino)
                OR (NEW.canal = 'CORREO' AND u.correo = NEW.destino))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el mensaje va al contacto registrado del usuario';
    END IF;
    IF NEW.tipo = 'ACTIVACION_CUENTA' AND EXISTS (SELECT 1 FROM usuario c WHERE c.nombre_usuario = NEW.creado_por
            AND NOT (c.id <=> NEW.usuario_id) AND (c.telefono_whatsapp = NEW.destino OR c.correo = NEW.destino)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el enlace no va al contacto de quien lo pidió';
    END IF;
    -- El resumen es de una foto de ESTE colegio y va a una persona PROMOTOR activa (o al correo externo del DBA).
    IF NEW.tipo = 'RESUMEN_DIARIO' AND (NOT (NEW.entidad <=> 'resumen_diario') OR NOT EXISTS (SELECT 1
            FROM resumen_diario r WHERE r.id = NEW.entidad_id AND r.colegio_id = NEW.colegio_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el resumen apunta a su foto';
    END IF;
    IF NEW.tipo = 'RESUMEN_DIARIO' AND NEW.destinatario_tipo = 'USUARIO' AND NOT EXISTS (SELECT 1
            FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id
            WHERE u.id = NEW.usuario_id AND u.colegio_id = NEW.colegio_id AND u.activo AND r.rol = 'PROMOTOR') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el resumen diario es solo para Promotoría';
    END IF;
    -- Las alertas al celular van solo a Promotoría y Dirección activas (nunca a Caja ni al correo externo).
    IF NEW.tipo = 'ALERTA_PROMOTORIA' AND NOT EXISTS (SELECT 1
            FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id
            WHERE u.id = NEW.usuario_id AND u.colegio_id = NEW.colegio_id AND u.activo
            AND r.rol IN ('PROMOTOR', 'DIRECTOR')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: las alertas son solo para Promotoría y Dirección';
    END IF;
    -- (Sprint 6: reemplaza el bloque EXTERNO.) Cada tipo con SU fila del DBA.
    IF NEW.destinatario_tipo = 'EXTERNO' AND NOT EXISTS (SELECT 1 FROM configuracion_bd c
            WHERE c.clave = CASE NEW.tipo WHEN 'RESUMEN_DIARIO' THEN 'resumen_correo_externo'
                                          ELSE 'huella_correo_externo' END
              AND c.valor = NEW.destino) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el correo externo lo configura el DBA';
    END IF;
    IF NEW.respaldo_de_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mensaje o WHERE o.id = NEW.respaldo_de_id
            AND o.canal = 'WHATSAPP' AND o.estado = 'FALLIDO' AND o.tipo = NEW.tipo
            AND o.apoderado_id <=> NEW.apoderado_id AND o.usuario_id <=> NEW.usuario_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el respaldo es de un WhatsApp FALLIDO al mismo destinatario';
    END IF;
END$$

-- Transiciones; intentos de uno en uno; proveedor e id una sola vez; SIMULADO solo en una base habilitada por el DBA;
-- la activación sale solo con su enlace vigente.
DROP TRIGGER IF EXISTS trg_mensaje_envio$$
CREATE TRIGGER trg_mensaje_envio BEFORE UPDATE ON mensaje FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (
            (OLD.estado = 'PENDIENTE' AND NEW.estado IN ('ENVIADO', 'FALLIDO'))
            OR (OLD.estado = 'ENVIADO' AND NEW.estado IN ('ENTREGADO', 'LEIDO', 'FALLIDO'))
            OR (OLD.estado = 'ENTREGADO' AND NEW.estado IN ('LEIDO', 'FALLIDO'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado del mensaje no permitido';
    END IF;
    IF NOT (NEW.intentos <=> OLD.intentos) AND NOT (NEW.intentos <=> OLD.intentos + 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los intentos del mensaje suben de uno en uno';
    END IF;
    IF (OLD.proveedor IS NOT NULL AND NOT (NEW.proveedor <=> OLD.proveedor))
            OR (OLD.proveedor_mensaje_id IS NOT NULL AND NOT (NEW.proveedor_mensaje_id <=> OLD.proveedor_mensaje_id))
            OR (OLD.enviado_en IS NOT NULL AND NOT (NEW.enviado_en <=> OLD.enviado_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el envío del mensaje no se reescribe';
    END IF;
    IF NEW.proveedor = 'SIMULADO' AND NOT (NEW.proveedor <=> OLD.proveedor) AND NOT EXISTS (SELECT 1
            FROM configuracion_bd c WHERE c.clave = 'mensajeria_simulada' AND c.valor = 'PERMITIDA') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: esta base no admite la mensajería simulada';
    END IF;
    IF NEW.tipo = 'ACTIVACION_CUENTA' AND NEW.estado = 'ENVIADO' AND OLD.estado = 'PENDIENTE'
            AND NOT EXISTS (SELECT 1 FROM enlace_activacion e WHERE e.mensaje_id = NEW.id
                AND e.usado_en IS NULL AND e.anulado_en IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la activación sale con su enlace vigente';
    END IF;
    IF NEW.tipo = 'VERIFICACION_CONTACTO' AND NEW.estado = 'ENVIADO' AND OLD.estado = 'PENDIENTE'
            AND NOT EXISTS (SELECT 1 FROM verificacion_contacto v WHERE v.mensaje_id = NEW.id
                AND v.verificado_en IS NULL AND v.anulado_en IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la verificación sale con su enlace vigente';
    END IF;
END$$

-- S4-M2 + A2: el enlace nace con su mensaje de activación PENDIENTE para ESE titular, sin usar ni anular y con 72 h como
-- máximo.
DROP TRIGGER IF EXISTS trg_enlace_activacion_nace$$
CREATE TRIGGER trg_enlace_activacion_nace BEFORE INSERT ON enlace_activacion FOR EACH ROW
BEGIN
    IF NEW.usado_en IS NOT NULL OR NEW.anulado_en IS NOT NULL OR NEW.usado_ip IS NOT NULL
            OR NEW.vence_en <= NEW.creado_en OR NEW.vence_en > NEW.creado_en + INTERVAL 72 HOUR THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el enlace nace sin usar y vence en 72 h como máximo';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM mensaje m JOIN usuario u ON u.id = NEW.usuario_id
            WHERE m.id = NEW.mensaje_id AND m.colegio_id = NEW.colegio_id AND m.tipo = 'ACTIVACION_CUENTA'
            AND m.estado = 'PENDIENTE'
            AND ((NEW.proposito = 'PERSONAL' AND m.usuario_id = u.id)
                OR (NEW.proposito = 'APODERADO' AND m.apoderado_id = u.apoderado_id))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el enlace nace con su mensaje al titular';
    END IF;
END$$

-- Riesgo residual de sprint-4-correcciones: el uso y la anulación se escriben una vez; nunca se usa uno anulado o vencido.
DROP TRIGGER IF EXISTS trg_enlace_activacion_uso$$
CREATE TRIGGER trg_enlace_activacion_uso BEFORE UPDATE ON enlace_activacion FOR EACH ROW
BEGIN
    IF (OLD.usado_en IS NOT NULL OR OLD.anulado_en IS NOT NULL) AND (NOT (NEW.usado_en <=> OLD.usado_en)
            OR NOT (NEW.usado_ip <=> OLD.usado_ip) OR NOT (NEW.anulado_en <=> OLD.anulado_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un enlace usado o anulado no cambia';
    END IF;
    IF NEW.usado_en IS NOT NULL AND OLD.usado_en IS NULL
            AND (NEW.anulado_en IS NOT NULL OR NEW.usado_en > OLD.vence_en OR NEW.usado_ip IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un enlace vencido o anulado no se usa';
    END IF;
END$$

-- La huella coincide con un evento de ESE colegio en la bitácora.
DROP TRIGGER IF EXISTS trg_huella_bitacora_registro$$
CREATE TRIGGER trg_huella_bitacora_registro BEFORE INSERT ON huella_bitacora FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evento_auditoria e WHERE e.secuencia = NEW.secuencia
            AND e.colegio_id = NEW.colegio_id AND LEFT(e.hash, 16) = NEW.codigo) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la huella no coincide con la bitácora';
    END IF;
    -- S5-M4: la secuencia nunca es menor que la de una huella ya guardada (diaria o por hora) del colegio.
    IF NEW.secuencia < COALESCE((SELECT MAX(h.secuencia) FROM huella_bitacora h WHERE h.colegio_id = NEW.colegio_id), 0)
            OR NEW.secuencia < COALESCE((SELECT MAX(h.secuencia) FROM huella_hora h WHERE h.colegio_id = NEW.colegio_id), 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la huella no retrocede (la bitácora fue recortada)';
    END IF;
END$$


-- ===================== Sprint 5 · tanda 2 (V18): renovación, matrícula reservada y avisos de la familia ==============

-- La renovación nace PROPUESTA, sin respuesta, para un alumno ACTIVO de ESA familia con su matrícula de origen ACTIVA,
-- hacia un año PLANIFICADO del mismo colegio y una sección del grado propuesto.
DROP TRIGGER IF EXISTS trg_renovacion_matricula_nace$$
CREATE TRIGGER trg_renovacion_matricula_nace BEFORE INSERT ON renovacion_matricula FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> 'PROPUESTA') OR NEW.matricula_id IS NOT NULL OR NEW.respondido_por IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la renovación nace PROPUESTA y sin respuesta';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM alumno a JOIN matricula m ON m.id = NEW.matricula_origen_id
            JOIN anio_escolar d ON d.id = NEW.anio_destino_id JOIN seccion s ON s.id = NEW.seccion_destino_id
            WHERE a.id = NEW.alumno_id AND a.colegio_id = NEW.colegio_id AND a.familia_id = NEW.familia_id
            AND a.estado = 'ACTIVO' AND m.alumno_id = a.id AND m.estado = 'ACTIVA' AND d.estado = 'PLANIFICADO'
            AND d.colegio_id = NEW.colegio_id AND s.grado = NEW.grado_destino) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: renovación de un alumno activo de su familia hacia un año planificado';
    END IF;
END$$

-- Transiciones; grado y sección cambian solo en la propuesta; la respuesta se escribe una vez (en el portal, por un
-- apoderado de ESA familia); MATRICULADA apunta a la matrícula de su alumno, año y sección.
DROP TRIGGER IF EXISTS trg_renovacion_matricula_estado$$
CREATE TRIGGER trg_renovacion_matricula_estado BEFORE UPDATE ON renovacion_matricula FOR EACH ROW
BEGIN
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (
            (OLD.estado = 'PROPUESTA' AND NEW.estado IN ('CONFIRMADA', 'NO_CONTINUA', 'VENCIDA'))
            OR (OLD.estado = 'CONFIRMADA' AND NEW.estado = 'MATRICULADA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado de la renovación no permitido';
    END IF;
    IF (NOT (NEW.grado_destino <=> OLD.grado_destino) OR NOT (NEW.seccion_destino_id <=> OLD.seccion_destino_id))
            AND (NOT (OLD.estado <=> 'PROPUESTA') OR NOT EXISTS (SELECT 1 FROM seccion s
                WHERE s.id = NEW.seccion_destino_id AND s.grado = NEW.grado_destino)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el grado y la sección cambian solo en la propuesta';
    END IF;
    IF OLD.respondido_por IS NOT NULL AND (NOT (NEW.respondido_por <=> OLD.respondido_por)
            OR NOT (NEW.respondido_en <=> OLD.respondido_en) OR NOT (NEW.canal_respuesta <=> OLD.canal_respuesta)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la respuesta de la familia no cambia';
    END IF;
    IF NEW.canal_respuesta <=> 'PORTAL' AND OLD.respondido_por IS NULL AND NOT EXISTS (SELECT 1 FROM usuario u
            JOIN apoderado a ON a.id = u.apoderado_id
            WHERE u.nombre_usuario = NEW.respondido_por AND u.colegio_id = NEW.colegio_id
            AND a.familia_id = NEW.familia_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: en el portal responde un apoderado de la familia';
    END IF;
    IF NEW.estado <=> 'MATRICULADA' AND NOT (OLD.estado <=> 'MATRICULADA') AND NOT EXISTS (SELECT 1 FROM matricula m
            WHERE m.id = NEW.matricula_id AND m.alumno_id = NEW.alumno_id AND m.anio_escolar_id = NEW.anio_destino_id
            AND m.seccion_id = NEW.seccion_destino_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la renovación apunta a la matrícula de su alumno y año';
    END IF;
END$$

-- RESERVADA solo en un año PLANIFICADO; ACTIVA en el año EN_CURSO (ingreso durante el año, como hasta hoy). Un colegio
-- que todavía no tiene año EN_CURSO (empieza a usar el sistema con su primer año planificado) matricula ACTIVA en ese
-- año planificado; en cuanto existe un año en curso, toda matrícula de un año planificado nace RESERVADA.
DROP TRIGGER IF EXISTS trg_matricula_nace$$
CREATE TRIGGER trg_matricula_nace BEFORE INSERT ON matricula FOR EACH ROW
BEGIN
    IF NEW.activada_en IS NOT NULL OR NEW.activada_por IS NOT NULL OR NOT EXISTS (SELECT 1 FROM anio_escolar d
            WHERE d.id = NEW.anio_escolar_id AND d.colegio_id = NEW.colegio_id
            AND ((NEW.estado <=> 'RESERVADA' AND d.estado = 'PLANIFICADO')
                OR (NEW.estado <=> 'ACTIVA' AND d.estado = 'EN_CURSO')
                OR (NEW.estado <=> 'ACTIVA' AND d.estado = 'PLANIFICADO' AND NOT EXISTS (SELECT 1 FROM anio_escolar e
                    WHERE e.colegio_id = NEW.colegio_id AND e.estado = 'EN_CURSO')))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la matrícula del año siguiente nace RESERVADA';
    END IF;
END$$

-- El alumno y el año no cambian. RESERVADA -> ACTIVA solo con su cuota de matrícula PAGADA o EXONERADA (o con un plan
-- de matrícula 0) y solo por sistema.matricula. RESERVADA -> RETIRADA sin la matrícula pagada ni en pago parcial.
DROP TRIGGER IF EXISTS trg_matricula_estado$$
CREATE TRIGGER trg_matricula_estado BEFORE UPDATE ON matricula FOR EACH ROW
BEGIN
    IF NOT (NEW.alumno_id <=> OLD.alumno_id) OR NOT (NEW.anio_escolar_id <=> OLD.anio_escolar_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el alumno y el año de la matrícula no cambian';
    END IF;
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (
            (OLD.estado = 'RESERVADA' AND NEW.estado IN ('ACTIVA', 'RETIRADA'))
            OR (OLD.estado = 'ACTIVA' AND NEW.estado = 'RETIRADA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: cambio de estado de la matrícula no permitido';
    END IF;
    IF (OLD.activada_por IS NOT NULL AND NOT (NEW.activada_por <=> OLD.activada_por))
            OR (OLD.activada_en IS NOT NULL AND NOT (NEW.activada_en <=> OLD.activada_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la activación de la matrícula no se reescribe';
    END IF;
    IF OLD.estado = 'RESERVADA' AND NEW.estado = 'ACTIVA' AND NOT (
            EXISTS (SELECT 1 FROM cuota c WHERE c.matricula_id = NEW.id AND c.tipo = 'MATRICULA'
                AND c.estado IN ('PAGADA', 'EXONERADA'))
            OR (NOT EXISTS (SELECT 1 FROM cuota c WHERE c.matricula_id = NEW.id AND c.tipo = 'MATRICULA'
                    AND c.estado <> 'ANULADA')
                AND EXISTS (SELECT 1 FROM plan_pension p JOIN seccion s ON s.id = NEW.seccion_id
                    WHERE p.anio_escolar_id = NEW.anio_escolar_id AND p.colegio_id = NEW.colegio_id
                    AND p.estado = 'APROBADO' AND p.vigente = TRUE AND p.monto_matricula = 0
                    AND p.nivel = SUBSTRING_INDEX(s.grado, '_', 1)))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la matrícula se activa con la matrícula pagada';
    END IF;
    IF OLD.estado = 'RESERVADA' AND NEW.estado = 'ACTIVA' AND NOT (NEW.activada_por <=> 'sistema.matricula') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la matrícula la activa el sistema';
    END IF;
    IF OLD.estado = 'RESERVADA' AND NEW.estado = 'RETIRADA' AND EXISTS (SELECT 1 FROM cuota c
            WHERE c.matricula_id = NEW.id AND c.tipo = 'MATRICULA' AND c.estado IN ('PAGADA', 'PARCIAL')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: primero se anula el pago de la matrícula';
    END IF;
END$$

-- Un aviso atendido no cambia (el CHECK exige la respuesta y que lo atienda una persona).
DROP TRIGGER IF EXISTS trg_aviso_familia_estado$$
CREATE TRIGGER trg_aviso_familia_estado BEFORE UPDATE ON aviso_familia FOR EACH ROW
BEGIN
    IF OLD.estado = 'ATENDIDO' AND (NOT (NEW.estado <=> OLD.estado) OR NOT (NEW.respuesta <=> OLD.respuesta)
            OR NOT (NEW.atendido_por <=> OLD.atendido_por) OR NOT (NEW.atendido_en <=> OLD.atendido_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un aviso atendido no cambia';
    END IF;
    -- S5-M2: quien registró el pago, o pidió o aprobó la anulación o el descuento del que se queja la familia, no lo cierra.
    IF OLD.estado = 'ABIERTO' AND NEW.estado = 'ATENDIDO' AND (
            EXISTS (SELECT 1 FROM pago p WHERE p.id = OLD.pago_id AND p.colegio_id = OLD.colegio_id
                AND (p.cajero = NEW.atendido_por OR p.creado_por = NEW.atendido_por))
            OR EXISTS (SELECT 1 FROM anulacion_pago a WHERE a.pago_id = OLD.pago_id AND a.colegio_id = OLD.colegio_id
                AND (a.solicitado_por = NEW.atendido_por OR a.aprobado_por = NEW.atendido_por))
            OR EXISTS (SELECT 1 FROM descuento d WHERE OLD.cuota_id IS NOT NULL AND d.colegio_id = OLD.colegio_id
                AND d.cuotas LIKE CONCAT('%,', OLD.cuota_id, ',%')
                AND (d.creado_por = NEW.atendido_por OR d.resuelto_por <=> NEW.atendido_por))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: quien participó en lo que reclama la familia no atiende su aviso';
    END IF;
END$$

-- ===================== Sprint 5 · tanda 3 (V19): feriados y cierre mensual =====================

-- G20: solo fechas futuras en la hora de Lima (UTC-5, sin horario de verano): nadie «crea» un feriado para retrasar una
-- alerta que ya venció. Nace vigente.
DROP TRIGGER IF EXISTS trg_feriado_registro$$
CREATE TRIGGER trg_feriado_registro BEFORE INSERT ON feriado FOR EACH ROW
BEGIN
    IF NOT (NEW.fecha > DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR)) OR NOT (NEW.vigente <=> TRUE)
            OR NEW.anulado_por IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un feriado se registra solo para una fecha futura';
    END IF;
    -- S5-M3: nace PROPUESTO (lo aprueba otra persona), con 3 por mes como máximo y sin 3 días seguidos.
    IF NOT (NEW.pendiente <=> TRUE) OR NEW.aprobado_por IS NOT NULL OR NEW.aprobado_en IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un feriado nace propuesto y lo aprueba otra persona';
    END IF;
    IF (SELECT COUNT(*) FROM feriado f WHERE f.colegio_id = NEW.colegio_id AND f.vigente
            AND YEAR(f.fecha) = YEAR(NEW.fecha) AND MONTH(f.fecha) = MONTH(NEW.fecha)) >= 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: como máximo 3 días no laborables del colegio por mes';
    END IF;
    IF (SELECT COUNT(*) FROM feriado f WHERE f.colegio_id = NEW.colegio_id AND f.vigente
                AND f.fecha IN (NEW.fecha - INTERVAL 1 DAY, NEW.fecha - INTERVAL 2 DAY)) = 2
            OR (SELECT COUNT(*) FROM feriado f WHERE f.colegio_id = NEW.colegio_id AND f.vigente
                AND f.fecha IN (NEW.fecha - INTERVAL 1 DAY, NEW.fecha + INTERVAL 1 DAY)) = 2
            OR (SELECT COUNT(*) FROM feriado f WHERE f.colegio_id = NEW.colegio_id AND f.vigente
                AND f.fecha IN (NEW.fecha + INTERVAL 1 DAY, NEW.fecha + INTERVAL 2 DAY)) = 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: no más de 2 días no laborables del colegio seguidos';
    END IF;
END$$

-- Se anula una sola vez y antes de su fecha; uno anulado no cambia.
DROP TRIGGER IF EXISTS trg_feriado_anulacion$$
CREATE TRIGGER trg_feriado_anulacion BEFORE UPDATE ON feriado FOR EACH ROW
BEGIN
    IF OLD.vigente IS NULL OR (NEW.vigente IS NULL AND NOT (OLD.fecha > DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un feriado se anula una vez y antes de su fecha';
    END IF;
    -- S5-M3: se aprueba una vez, antes de su fecha y por OTRA persona; uno aprobado no vuelve a propuesto.
    IF (OLD.pendiente = FALSE AND (NOT (NEW.pendiente <=> OLD.pendiente)
                OR NOT (NEW.aprobado_por <=> OLD.aprobado_por) OR NOT (NEW.aprobado_en <=> OLD.aprobado_en)))
            OR (OLD.pendiente = TRUE AND NEW.pendiente = FALSE AND (NEW.vigente IS NULL
                OR NEW.aprobado_por IS NULL OR NEW.aprobado_por = OLD.creado_por
                OR NOT (OLD.fecha > DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR))))
            OR (OLD.pendiente = TRUE AND NEW.pendiente = TRUE AND NEW.aprobado_por IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un feriado lo aprueba otra persona, una vez y antes de su fecha';
    END IF;
END$$

-- G22: nace ABIERTO, sin números a ciegas, con los totales del mes calculados de los movimientos de los extractos
-- CONFIRMADOS que cubren TODO el mes y con el saldo al cierre de su último día. Las variables llevan prefijo «v_» para no
-- confundirse con las columnas desde y hasta del extracto.
DROP TRIGGER IF EXISTS trg_cierre_mensual_banco_nace$$
CREATE TRIGGER trg_cierre_mensual_banco_nace BEFORE INSERT ON cierre_mensual_banco FOR EACH ROW
BEGIN
    DECLARE v_desde DATE DEFAULT STR_TO_DATE(CONCAT(NEW.anio, '-', NEW.mes, '-01'), '%Y-%m-%d');
    DECLARE v_hasta DATE DEFAULT LAST_DAY(STR_TO_DATE(CONCAT(NEW.anio, '-', NEW.mes, '-01'), '%Y-%m-%d'));
    IF NOT (NEW.estado <=> 'ABIERTO') OR NOT (NEW.intentos <=> 0) OR NEW.abonos_ciego IS NOT NULL
            OR NEW.cargos_ciego IS NOT NULL OR NEW.saldo_ciego IS NOT NULL OR NEW.registrado_por IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el cierre mensual nace ABIERTO y sin números a ciegas';
    END IF;
    IF v_desde IS NULL OR NOT EXISTS (SELECT 1 FROM extracto_bancario e WHERE e.cuenta_id = NEW.cuenta_id
                AND e.colegio_id = NEW.colegio_id AND e.estado = 'CONFIRMADO' AND e.desde <= v_desde
                AND e.hasta >= v_desde)
            OR NOT EXISTS (SELECT 1 FROM extracto_bancario e WHERE e.cuenta_id = NEW.cuenta_id
                AND e.colegio_id = NEW.colegio_id AND e.estado = 'CONFIRMADO' AND e.desde <= v_hasta
                AND e.hasta >= v_hasta)
            OR NOT (NEW.total_abonos <=> (SELECT COALESCE(SUM(m.monto), 0.00) FROM movimiento_bancario m
                JOIN extracto_bancario e ON e.id = m.extracto_id WHERE m.cuenta_id = NEW.cuenta_id
                AND e.estado = 'CONFIRMADO' AND m.tipo = 'ABONO' AND m.fecha BETWEEN v_desde AND v_hasta))
            OR NOT (NEW.total_cargos <=> (SELECT COALESCE(SUM(m.monto), 0.00) FROM movimiento_bancario m
                JOIN extracto_bancario e ON e.id = m.extracto_id WHERE m.cuenta_id = NEW.cuenta_id
                AND e.estado = 'CONFIRMADO' AND m.tipo = 'CARGO' AND m.fecha BETWEEN v_desde AND v_hasta))
            OR NOT (NEW.saldo_final <=> (SELECT e.saldo_inicial + COALESCE((SELECT SUM(CASE WHEN m.tipo = 'ABONO'
                    THEN m.monto ELSE -m.monto END) FROM movimiento_bancario m WHERE m.extracto_id = e.id
                    AND m.fecha <= v_hasta), 0.00)
                FROM extracto_bancario e WHERE e.cuenta_id = NEW.cuenta_id AND e.estado = 'CONFIRMADO'
                AND e.desde <= v_hasta AND e.hasta >= v_hasta ORDER BY e.secuencia LIMIT 1)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los totales del mes salen de los extractos confirmados';
    END IF;
END$$

-- Un cierre resuelto no cambia; los intentos suben de uno en uno; lo hace una persona que no subió ni confirmó extractos
-- de ese mes; CUADRADO solo con un intento más (el que cuadró).
DROP TRIGGER IF EXISTS trg_cierre_mensual_banco_estado$$
CREATE TRIGGER trg_cierre_mensual_banco_estado BEFORE UPDATE ON cierre_mensual_banco FOR EACH ROW
BEGIN
    IF NOT (OLD.estado <=> 'ABIERTO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: un cierre mensual resuelto no cambia';
    END IF;
    IF NOT (NEW.intentos <=> OLD.intentos) AND NOT (NEW.intentos <=> OLD.intentos + 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: los intentos del cierre suben de uno en uno';
    END IF;
    IF NOT (NEW.estado <=> OLD.estado) AND NOT (NEW.intentos <=> OLD.intentos + 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el cierre se resuelve con un intento a ciegas';
    END IF;
    IF NEW.registrado_por IS NOT NULL AND EXISTS (SELECT 1 FROM extracto_bancario e WHERE e.cuenta_id = NEW.cuenta_id
            AND (e.creado_por = NEW.registrado_por OR e.confirmado_por <=> NEW.registrado_por)
            AND e.hasta >= STR_TO_DATE(CONCAT(NEW.anio, '-', NEW.mes, '-01'), '%Y-%m-%d')
            AND e.desde <= LAST_DAY(STR_TO_DATE(CONCAT(NEW.anio, '-', NEW.mes, '-01'), '%Y-%m-%d'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el cierre mensual lo hace quien no subió ni confirmó extractos del mes';
    END IF;
END$$

-- ===================== Correcciones del sprint 5 (V20) =====================

-- S5-A1: la verificación nace con su mensaje VERIFICACION_CONTACTO PENDIENTE a ESE contacto del apoderado, sin usar ni
-- anular y con 72 h como máximo.
DROP TRIGGER IF EXISTS trg_verificacion_contacto_nace$$
CREATE TRIGGER trg_verificacion_contacto_nace BEFORE INSERT ON verificacion_contacto FOR EACH ROW
BEGIN
    IF NEW.verificado_en IS NOT NULL OR NEW.anulado_en IS NOT NULL OR NEW.verificado_ip IS NOT NULL
            OR NEW.vence_en <= NEW.creado_en OR NEW.vence_en > NEW.creado_en + INTERVAL 72 HOUR THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la verificación nace sin usar y vence en 72 h como máximo';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM mensaje m WHERE m.id = NEW.mensaje_id AND m.colegio_id = NEW.colegio_id
            AND m.tipo = 'VERIFICACION_CONTACTO' AND m.estado = 'PENDIENTE' AND m.apoderado_id = NEW.apoderado_id
            AND m.canal = NEW.canal AND m.destino = NEW.contacto) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la verificación nace con su mensaje a ese contacto';
    END IF;
END$$

-- S5-A1: se usa o se anula una vez; nunca vencida ni anulada.
DROP TRIGGER IF EXISTS trg_verificacion_contacto_uso$$
CREATE TRIGGER trg_verificacion_contacto_uso BEFORE UPDATE ON verificacion_contacto FOR EACH ROW
BEGIN
    IF (OLD.verificado_en IS NOT NULL OR OLD.anulado_en IS NOT NULL) AND (NOT (NEW.verificado_en <=> OLD.verificado_en)
            OR NOT (NEW.verificado_ip <=> OLD.verificado_ip) OR NOT (NEW.anulado_en <=> OLD.anulado_en)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una verificación usada o anulada no cambia';
    END IF;
    IF NEW.verificado_en IS NOT NULL AND OLD.verificado_en IS NULL
            AND (NEW.anulado_en IS NOT NULL OR NEW.verificado_en > OLD.vence_en OR NEW.verificado_ip IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: una verificación vencida o anulada no se usa';
    END IF;
END$$

-- S5-M4: la huella de la hora coincide con un evento de ESE colegio y nunca retrocede.
DROP TRIGGER IF EXISTS trg_huella_hora_registro$$
CREATE TRIGGER trg_huella_hora_registro BEFORE INSERT ON huella_hora FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evento_auditoria e WHERE e.secuencia = NEW.secuencia
            AND e.colegio_id = NEW.colegio_id AND LEFT(e.hash, 16) = NEW.codigo) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la huella de la hora no coincide con la bitácora';
    END IF;
    IF NEW.secuencia < COALESCE((SELECT MAX(h.secuencia) FROM huella_bitacora h WHERE h.colegio_id = NEW.colegio_id), 0)
            OR NEW.secuencia < COALESCE((SELECT MAX(h.secuencia) FROM huella_hora h WHERE h.colegio_id = NEW.colegio_id), 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la huella no retrocede (la bitácora fue recortada)';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 6 · tanda 2 (V21): resumen diario y contacto del personal =====================
DELIMITER $$

-- La foto del resumen la escribe sistema.panel y cada cifra de los libros es EXACTAMENTE la suma de pago y cuota en ese
-- momento (la aplicación las calcula con JPQL en la misma transacción; con READ COMMITTED, un pago confirmado por otra
-- transacción entre esa lectura y el INSERT hace fallar la foto y el proceso la reintenta). Definiciones (sección 3.4):
-- cobrado = pagos VIGENTES por su día de caja; deuda vencida = monto - pagado - descuento de las cuotas PENDIENTE o
-- PARCIAL con vencimiento ANTERIOR a la fecha; familias morosas = familias distintas de esas cuotas.
DROP TRIGGER IF EXISTS trg_resumen_diario_registro$$
CREATE TRIGGER trg_resumen_diario_registro BEFORE INSERT ON resumen_diario FOR EACH ROW
BEGIN
    DECLARE v_total DECIMAL(12,2);
    DECLARE v_cantidad INT;
    DECLARE v_efectivo DECIMAL(12,2);
    DECLARE v_pagos_efectivo INT;
    DECLARE v_mes DECIMAL(12,2);
    DECLARE v_deuda DECIMAL(12,2);
    DECLARE v_familias INT;
    IF NOT (NEW.creado_por <=> 'sistema.panel') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el resumen diario lo genera sistema.panel';
    END IF;
    IF NEW.cortado_en IS NULL OR NEW.cortado_en < TIMESTAMP(NEW.fecha)
            OR NEW.cortado_en > TIMESTAMP(NEW.fecha + INTERVAL 1 DAY, '06:00:00') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el corte del resumen es de su día';
    END IF;
    SELECT COALESCE(SUM(p.total), 0), COUNT(*),
           COALESCE(SUM(CASE WHEN p.medio = 'EFECTIVO' THEN p.total ELSE 0 END), 0),
           COALESCE(SUM(CASE WHEN p.medio = 'EFECTIVO' THEN 1 ELSE 0 END), 0)
      INTO v_total, v_cantidad, v_efectivo, v_pagos_efectivo
      FROM pago p WHERE p.colegio_id = NEW.colegio_id AND p.fecha = NEW.fecha AND p.estado = 'VIGENTE';
    SELECT COALESCE(SUM(p.total), 0) INTO v_mes
      FROM pago p WHERE p.colegio_id = NEW.colegio_id AND p.estado = 'VIGENTE'
       AND p.fecha BETWEEN NEW.fecha - INTERVAL (DAYOFMONTH(NEW.fecha) - 1) DAY AND NEW.fecha;
    SELECT COALESCE(SUM(c.monto - c.monto_pagado - c.monto_descuento), 0), COUNT(DISTINCT a.familia_id)
      INTO v_deuda, v_familias
      FROM cuota c JOIN alumno a ON a.id = c.alumno_id
     WHERE c.colegio_id = NEW.colegio_id AND c.estado IN ('PENDIENTE', 'PARCIAL') AND c.fecha_vencimiento < NEW.fecha;
    IF NOT (NEW.cobrado_total <=> v_total AND NEW.pagos_cantidad <=> v_cantidad AND NEW.cobrado_efectivo <=> v_efectivo
            AND NEW.pagos_efectivo <=> v_pagos_efectivo AND NEW.cobrado_mes <=> v_mes AND NEW.deuda_vencida <=> v_deuda
            AND NEW.familias_morosas <=> v_familias) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: las cifras del resumen no son las de los libros';
    END IF;
    -- P18: la huella de la tarde (la de las 19:00) es una huella por hora guardada de ESTE colegio.
    IF NEW.huella_secuencia IS NOT NULL AND NOT EXISTS (SELECT 1 FROM huella_hora h
            WHERE h.colegio_id = NEW.colegio_id AND h.secuencia = NEW.huella_secuencia AND h.codigo = NEW.huella_codigo) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la huella del resumen no es una huella guardada';
    END IF;
END$$

-- Hallazgo 5 (P6). El celular y el correo del PERSONAL (apoderado_id IS NULL) cambian solo con SU
-- CAMBIO_CONTACTO_PERSONAL aprobada, enlazada en contacto_solicitud_id; uk_usuario_contacto_solicitud impide reusarla.
-- Las cuentas de apoderado no cambian de regla. Un UPDATE que no toca esas columnas (el ingreso, la clave) no se frena.
DROP TRIGGER IF EXISTS trg_usuario_contacto$$
CREATE TRIGGER trg_usuario_contacto BEFORE UPDATE ON usuario FOR EACH ROW
BEGIN
    IF OLD.apoderado_id IS NULL
            AND (NOT (NEW.telefono_whatsapp <=> OLD.telefono_whatsapp) OR NOT (NEW.correo <=> OLD.correo)
                OR NOT (NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id))
            AND ((NEW.contacto_solicitud_id <=> OLD.contacto_solicitud_id)
                OR NOT EXISTS (SELECT 1 FROM solicitud_cambio s WHERE s.id = NEW.contacto_solicitud_id
                    AND s.colegio_id = NEW.colegio_id AND s.tipo = 'CAMBIO_CONTACTO_PERSONAL' AND s.entidad = 'usuario'
                    AND s.entidad_id = NEW.id AND s.estado = 'APROBADA')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: el contacto del personal solo cambia con su solicitud aprobada';
    END IF;
END$$

DELIMITER ;

-- ===================== Sprint 6 · tanda 3 (V22): llamada de control =====================
DELIMITER $$

-- Decisión 77 (P17). La llamada de control la registra una persona ACTIVA de Promotoría o Dirección de ESE colegio, para
-- el lunes de la semana EN CURSO en la hora de Lima (UTC-5, sin horario de verano; nadie «rellena» semanas pasadas ni
-- adelanta las futuras) y sobre una familia que pagó en EFECTIVO (vigente o anulado) desde 35 días antes de ese lunes
-- hasta el domingo de esa semana. La muestra la elige la aplicación con la semilla secreta; la base no la recalcula.
DROP TRIGGER IF EXISTS trg_llamada_control_registro$$
CREATE TRIGGER trg_llamada_control_registro BEFORE INSERT ON llamada_control FOR EACH ROW
BEGIN
    DECLARE v_hoy DATE DEFAULT DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR);
    IF NOT EXISTS (SELECT 1 FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id
            WHERE u.nombre_usuario = NEW.creado_por AND u.colegio_id = NEW.colegio_id AND u.activo
              AND u.apoderado_id IS NULL AND r.rol IN ('PROMOTOR', 'DIRECTOR')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la llamada de control la hace Promotoría o Dirección';
    END IF;
    IF NEW.semana IS NULL OR NOT (NEW.semana <=> v_hoy - INTERVAL WEEKDAY(v_hoy) DAY) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la llamada de control es de la semana en curso (su lunes)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pago p WHERE p.colegio_id = NEW.colegio_id AND p.familia_id = NEW.familia_id
            AND p.medio = 'EFECTIVO' AND p.fecha BETWEEN NEW.semana - INTERVAL 35 DAY AND NEW.semana + INTERVAL 6 DAY) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cuentasclaras: la llamada es a una familia que pagó en efectivo';
    END IF;
END$$

DELIMITER ;
