-- Sprint 5 · tanda 3: recordatorios (preferencia del apoderado), feriados extra del colegio, semilla secreta del muestreo
-- de caja (pendiente del sprint 3) y cierre bancario mensual a ciegas (riesgos residuales del sprint 4).

-- Recordatorios de vencimiento: el apoderado puede apagarlos desde el portal. Los avisos de pago, anulación y descuento NO
-- se apagan (control antifraude).
ALTER TABLE apoderado ADD COLUMN recordatorios_activos BOOLEAN NOT NULL DEFAULT TRUE;

-- Días no laborables EXTRA (los feriados nacionales están en el código y no se editan). Solo fechas futuras (trigger).
-- vigente: TRUE o NULL (anulado), para un único feriado vigente por fecha.
CREATE TABLE feriado (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id        BIGINT        NOT NULL,
    fecha             DATE          NOT NULL,
    descripcion       VARCHAR(80)   NOT NULL,
    vigente           BOOLEAN,
    anulado_por       VARCHAR(60),
    anulado_en        DATETIME(6),
    motivo_anulacion  VARCHAR(500),
    creado_en         DATETIME(6)   NOT NULL,
    creado_por        VARCHAR(60)   NOT NULL,
    actualizado_en    DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_feriado_vigente UNIQUE (colegio_id, fecha, vigente),
    CONSTRAINT fk_feriado_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_feriado_estado CHECK ((vigente = TRUE AND anulado_por IS NULL AND anulado_en IS NULL
            AND motivo_anulacion IS NULL)
        OR (vigente IS NULL AND anulado_por IS NOT NULL AND anulado_en IS NOT NULL AND motivo_anulacion IS NOT NULL
            AND anulado_por NOT LIKE 'sistema%'))
);

-- Semilla secreta del muestreo diario (SecureRandom). SOLO INSERCIÓN. Ámbito CAJA: las 3 verificaciones que ve Promotoría.
CREATE TABLE semilla_muestreo (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    ambito          VARCHAR(20)   NOT NULL,
    fecha           DATE          NOT NULL,
    semilla         BIGINT        NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_semilla_muestreo UNIQUE (colegio_id, ambito, fecha),
    CONSTRAINT fk_semilla_muestreo_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_semilla_muestreo_ambito CHECK (ambito IN ('CAJA'))
);

-- Cierre bancario mensual a ciegas. Los totales calculados los fija el sistema al crearlo (trigger: suma de los
-- movimientos de los extractos CONFIRMADOS que cubren todo el mes, y el saldo al cierre del último día). Quien no subió ni
-- confirmó extractos del mes escribe a ciegas los tres números del estado de cuenta oficial. DECIMAL(14,2) como los
-- saldos (hallazgo 12 del sprint 4).
CREATE TABLE cierre_mensual_banco (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    cuenta_id       BIGINT         NOT NULL,
    anio            INT            NOT NULL,
    mes             INT            NOT NULL,
    total_abonos    DECIMAL(14,2)  NOT NULL,
    total_cargos    DECIMAL(14,2)  NOT NULL,
    saldo_final     DECIMAL(14,2)  NOT NULL,
    estado          VARCHAR(20)    NOT NULL,
    intentos        INT            NOT NULL DEFAULT 0,
    abonos_ciego    DECIMAL(14,2),
    cargos_ciego    DECIMAL(14,2),
    saldo_ciego     DECIMAL(14,2),
    registrado_por  VARCHAR(60),
    registrado_en   DATETIME(6),
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_cierre_mensual UNIQUE (colegio_id, cuenta_id, anio, mes),
    CONSTRAINT fk_cierre_mensual_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cierre_mensual_cuenta FOREIGN KEY (cuenta_id, colegio_id) REFERENCES cuenta_bancaria (id, colegio_id),
    CONSTRAINT ck_cierre_mensual_periodo CHECK (mes BETWEEN 1 AND 12 AND anio BETWEEN 2026 AND 2100),
    CONSTRAINT ck_cierre_mensual_montos CHECK (total_abonos >= 0 AND total_cargos >= 0),
    CONSTRAINT ck_cierre_mensual_estado CHECK (estado IN ('ABIERTO', 'CUADRADO', 'DISCREPANCIA') AND intentos >= 0),
    CONSTRAINT ck_cierre_mensual_cuadrado CHECK (estado <> 'CUADRADO'
        OR (abonos_ciego IS NOT NULL AND abonos_ciego = total_abonos AND cargos_ciego IS NOT NULL
            AND cargos_ciego = total_cargos AND saldo_ciego IS NOT NULL AND saldo_ciego = saldo_final
            AND registrado_por IS NOT NULL AND registrado_en IS NOT NULL)),
    CONSTRAINT ck_cierre_mensual_discrepancia CHECK (estado <> 'DISCREPANCIA'
        OR (intentos >= 1 AND registrado_por IS NOT NULL AND registrado_en IS NOT NULL)),
    CONSTRAINT ck_cierre_mensual_registro CHECK (registrado_por IS NULL OR registrado_por NOT LIKE 'sistema%'),
    CONSTRAINT ck_cierre_mensual_actor CHECK (creado_por = 'sistema.conciliacion')
);
CREATE INDEX ix_cierre_mensual_estado ON cierre_mensual_banco (colegio_id, estado);
