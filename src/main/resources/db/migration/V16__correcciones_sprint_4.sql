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

-- S4-A3 y QA-S4-1/2/3. Contracargo: el apoderado ya recuperó su dinero por su banco, así que el pago se anula SIN
-- reembolso (tipo nuevo CONTRACARGO). La orden guarda cuándo llegó (una vez: por el aviso o por la liquidación); con
-- contracargo, un ingreso por revisar ya no se aplica ni se devuelve (trg_orden_pago_estado).
ALTER TABLE anulacion_pago DROP CONSTRAINT ck_anulacion_pago_tipo;
ALTER TABLE anulacion_pago ADD CONSTRAINT ck_anulacion_pago_tipo CHECK (tipo IN ('DEVOLUCION', 'CORRECCION',
    'CONTRACARGO'));
ALTER TABLE orden_pago ADD COLUMN contracargo_en DATETIME(6);
ALTER TABLE orden_pago ADD COLUMN contracargo_origen VARCHAR(20);
ALTER TABLE orden_pago ADD CONSTRAINT ck_orden_pago_contracargo CHECK (
    (contracargo_en IS NULL AND contracargo_origen IS NULL)
    OR (contracargo_en IS NOT NULL AND contracargo_origen IN ('AVISO', 'LIQUIDACION') AND cargo_id IS NOT NULL));

-- S4-A3. La devolución de un pago en línea solo sale por la API de la pasarela (vuelve al mismo medio de origen): se
-- registra aquí, con el id del cargo y el del reembolso que devuelve la pasarela, nunca en «reembolso» (su trigger
-- rechaza los pagos de la pasarela). SOLO INSERCIÓN; una por anulación.
CREATE TABLE reembolso_pasarela (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT         NOT NULL,
    anulacion_pago_id  BIGINT         NOT NULL,
    pago_id            BIGINT         NOT NULL,
    cargo_id           VARCHAR(80)    NOT NULL,
    reembolso_id       VARCHAR(80)    NOT NULL,
    monto              DECIMAL(10,2)  NOT NULL,
    fecha              DATE           NOT NULL,
    creado_en          DATETIME(6)    NOT NULL,
    creado_por         VARCHAR(60)    NOT NULL,
    actualizado_en     DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_reembolso_pasarela_anulacion UNIQUE (colegio_id, anulacion_pago_id),
    CONSTRAINT uk_reembolso_pasarela_reembolso UNIQUE (colegio_id, reembolso_id),
    CONSTRAINT fk_reembolso_pasarela_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_reembolso_pasarela_anulacion FOREIGN KEY (anulacion_pago_id, colegio_id)
        REFERENCES anulacion_pago (id, colegio_id),
    CONSTRAINT fk_reembolso_pasarela_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT ck_reembolso_pasarela_monto CHECK (monto > 0)
);

-- S4-A4. La devolución de una línea de recaudación va a una cuenta de destino que se pide al solicitarla (banco, cuenta
-- y titular), la ejecuta alguien que no la pidió ni la aprobó (trigger) y es un objeto de la conciliación: un cargo con
-- su operación y su monto debe aparecer en el extracto (si no, alerta CRÍTICA).
ALTER TABLE linea_recaudacion ADD COLUMN devolucion_banco VARCHAR(20);
ALTER TABLE linea_recaudacion ADD COLUMN devolucion_cuenta VARCHAR(30);
ALTER TABLE linea_recaudacion ADD COLUMN devolucion_titular VARCHAR(120);
ALTER TABLE linea_recaudacion ADD CONSTRAINT ck_linea_recaudacion_destino CHECK (estado <> 'DEVUELTA'
    OR (devolucion_banco IS NOT NULL AND devolucion_cuenta IS NOT NULL AND devolucion_titular IS NOT NULL));
ALTER TABLE partida_conciliacion ADD COLUMN linea_recaudacion_id BIGINT;
ALTER TABLE partida_conciliacion ADD CONSTRAINT fk_partida_conciliacion_linea FOREIGN KEY (linea_recaudacion_id,
    colegio_id) REFERENCES linea_recaudacion (id, colegio_id);
ALTER TABLE partida_conciliacion DROP CONSTRAINT ck_partida_conciliacion_objeto;
ALTER TABLE partida_conciliacion ADD CONSTRAINT ck_partida_conciliacion_objeto CHECK (
    (objeto_tipo = 'PAGO' AND pago_id IS NOT NULL AND deposito_id IS NULL AND liquidacion_id IS NULL
        AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL AND linea_recaudacion_id IS NULL)
    OR (objeto_tipo = 'DEPOSITO' AND deposito_id IS NOT NULL AND pago_id IS NULL AND liquidacion_id IS NULL
        AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL AND linea_recaudacion_id IS NULL)
    OR (objeto_tipo = 'LIQUIDACION' AND liquidacion_id IS NOT NULL AND pago_id IS NULL AND deposito_id IS NULL
        AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL AND linea_recaudacion_id IS NULL)
    OR (objeto_tipo = 'LOTE_RECAUDACION' AND lote_recaudacion_id IS NOT NULL AND pago_id IS NULL
        AND deposito_id IS NULL AND liquidacion_id IS NULL AND reembolso_id IS NULL AND linea_recaudacion_id IS NULL)
    OR (objeto_tipo = 'REEMBOLSO' AND reembolso_id IS NOT NULL AND pago_id IS NULL AND deposito_id IS NULL
        AND liquidacion_id IS NULL AND lote_recaudacion_id IS NULL AND linea_recaudacion_id IS NULL)
    OR (objeto_tipo = 'LINEA_RECAUDACION' AND linea_recaudacion_id IS NOT NULL AND pago_id IS NULL
        AND deposito_id IS NULL AND liquidacion_id IS NULL AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL)
    OR (objeto_tipo = 'EXPLICACION' AND regla = 'EXPLICADA' AND pago_id IS NULL AND deposito_id IS NULL
        AND liquidacion_id IS NULL AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL
        AND linea_recaudacion_id IS NULL AND categoria IS NOT NULL AND nota IS NOT NULL
        AND monto_objeto = monto_movimiento));
