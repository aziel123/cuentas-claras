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
