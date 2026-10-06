-- Sprint 4 · correcciones tras la auditoría antifraude y QA (docs/arquitectura/sprint-4-correcciones.md).
-- No se editan V13–V15: todo cambio de esquema va aquí. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL.

-- S4-C1. Una pareja que confirma una persona (SUGERIDA o MANUAL) es por el MISMO monto: solo la liquidación de la
-- pasarela admite su tolerancia configurable (la EXACTA ya exigía diferencia 0). Así nadie «tapa» un Yape inventado
-- con un abono cualquiera de otro monto. La MANUAL, además, pasa por la bandeja (trigger: solicitud PARTIDA_MANUAL
-- aprobada por otra persona antes de confirmarse).
ALTER TABLE partida_conciliacion ADD CONSTRAINT ck_partida_conciliacion_monto_exacto CHECK (
    diferencia = 0 OR objeto_tipo = 'LIQUIDACION');

-- S4-A1. Confirmación realmente ciega: la muestra que ve quien confirma se elige UNA vez al registrar (SecureRandom),
-- se guarda y no cambia (sin GRANT UPDATE: 1143); nunca cubre todo el archivo y se muestra sin montos. S4-A2: la
-- semilla del muestreo diario del extracto es secreta (antes, la fecha). Los triggers de nacimiento exigen ambas.
ALTER TABLE extracto_bancario ADD COLUMN muestra VARCHAR(100);
ALTER TABLE extracto_bancario ADD COLUMN semilla_muestreo BIGINT;
ALTER TABLE extracto_bancario ADD CONSTRAINT ck_extracto_bancario_muestra CHECK (muestra IS NULL OR muestra = ''
    OR REGEXP_LIKE(muestra, '^[1-9][0-9]*(,[1-9][0-9]*)*$'));
ALTER TABLE lote_recaudacion ADD COLUMN muestra VARCHAR(100);
ALTER TABLE lote_recaudacion ADD CONSTRAINT ck_lote_recaudacion_muestra CHECK (muestra IS NULL OR muestra = ''
    OR REGEXP_LIKE(muestra, '^[1-9][0-9]*(,[1-9][0-9]*)*$'));
