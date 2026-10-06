-- Sprint 4 · tanda 3: extracto bancario encadenado (continuidad de saldos), movimientos de solo inserción,
-- conciliación automática (partidas) y liquidaciones de la pasarela. La verificación bancaria del sprint 3 gana el
-- origen AUTOMATICA: la inserta el sistema cuando una partida queda CONFIRMADA sobre un extracto CONFIRMADO.

-- Cuenta del colegio cuyo extracto se concilia (la registra Promotoría). El número no cambia: se desactiva.
CREATE TABLE cuenta_bancaria (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    banco           VARCHAR(20)    NOT NULL,
    numero          VARCHAR(30)    NOT NULL,
    moneda          VARCHAR(3)     NOT NULL,
    alias           VARCHAR(60)    NOT NULL,
    activa          BOOLEAN        NOT NULL DEFAULT TRUE,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_cuenta_bancaria_numero UNIQUE (colegio_id, banco, numero),
    CONSTRAINT uk_cuenta_bancaria_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_cuenta_bancaria_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_cuenta_bancaria_banco CHECK (banco IN ('BCP', 'INTERBANK', 'BBVA', 'SCOTIABANK', 'OTRO')),
    CONSTRAINT ck_cuenta_bancaria_moneda CHECK (moneda = 'PEN')
);

-- Extracto: un tramo de días COMPLETOS de una cuenta. Encadenado: secuencia n+1 empieza el día siguiente al fin de n y
-- con su saldo final (trigger). saldo_final = saldo_inicial + abonos - cargos (CHECK). secuencia_vigente impide dos
-- extractos vigentes con la misma posición (NULL al rechazarlo o descartarlo). Lo confirma OTRA persona escribiendo a
-- ciegas el saldo final que ve en el banco; confirmacion_extracto_id: el extracto cuyo saldo se escribió (el mismo o
-- uno posterior de la cadena: confirmar el lunes sella el fin de semana).
CREATE TABLE extracto_bancario (
    id                        BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id                BIGINT         NOT NULL,
    cuenta_id                 BIGINT         NOT NULL,
    secuencia                 INT            NOT NULL,
    secuencia_vigente         INT,
    anterior_id               BIGINT,
    archivo_id                BIGINT         NOT NULL,
    archivo_sha256            VARCHAR(64)    NOT NULL,
    formato                   VARCHAR(30)    NOT NULL,
    desde                     DATE           NOT NULL,
    hasta                     DATE           NOT NULL,
    saldo_inicial             DECIMAL(14,2)  NOT NULL,
    total_abonos              DECIMAL(14,2)  NOT NULL,
    total_cargos              DECIMAL(14,2)  NOT NULL,
    saldo_final               DECIMAL(14,2)  NOT NULL,
    movimientos               INT            NOT NULL,
    estado                    VARCHAR(20)    NOT NULL,
    intentos_confirmacion     INT            NOT NULL DEFAULT 0,
    saldo_final_ciego         DECIMAL(14,2),
    confirmacion_extracto_id  BIGINT,
    confirmado_por            VARCHAR(60),
    confirmado_en             DATETIME(6),
    rechazado_por             VARCHAR(60),
    rechazado_en              DATETIME(6),
    motivo_rechazo            VARCHAR(500),
    creado_en                 DATETIME(6)    NOT NULL,
    creado_por                VARCHAR(60)    NOT NULL,
    actualizado_en            DATETIME(6)    NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_extracto_bancario_vigente UNIQUE (colegio_id, cuenta_id, secuencia_vigente),
    CONSTRAINT uk_extracto_bancario_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_extracto_bancario_id_cuenta UNIQUE (id, cuenta_id),
    CONSTRAINT fk_extracto_bancario_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_extracto_bancario_cuenta FOREIGN KEY (cuenta_id, colegio_id) REFERENCES cuenta_bancaria (id, colegio_id),
    CONSTRAINT fk_extracto_bancario_archivo FOREIGN KEY (archivo_id, colegio_id, archivo_sha256)
        REFERENCES archivo_cargado (id, colegio_id, sha256),
    CONSTRAINT fk_extracto_bancario_anterior FOREIGN KEY (anterior_id, cuenta_id) REFERENCES extracto_bancario (id, cuenta_id),
    CONSTRAINT fk_extracto_bancario_confirmacion FOREIGN KEY (confirmacion_extracto_id, cuenta_id)
        REFERENCES extracto_bancario (id, cuenta_id),
    CONSTRAINT ck_extracto_bancario_saldos CHECK (saldo_final = saldo_inicial + total_abonos - total_cargos
        AND total_abonos >= 0 AND total_cargos >= 0 AND movimientos >= 0 AND desde <= hasta
        AND intentos_confirmacion >= 0),
    CONSTRAINT ck_extracto_bancario_cadena CHECK ((secuencia = 1 AND anterior_id IS NULL)
        OR (secuencia > 1 AND anterior_id IS NOT NULL)),
    CONSTRAINT ck_extracto_bancario_estado CHECK (estado IN ('CARGADO', 'CONFIRMADO', 'RECHAZADO', 'DESCARTADO')),
    CONSTRAINT ck_extracto_bancario_vigente CHECK (
        (estado IN ('CARGADO', 'CONFIRMADO') AND secuencia_vigente IS NOT NULL AND secuencia_vigente = secuencia)
        OR (estado IN ('RECHAZADO', 'DESCARTADO') AND secuencia_vigente IS NULL)),
    CONSTRAINT ck_extracto_bancario_ciego CHECK (saldo_final_ciego IS NULL OR saldo_final_ciego = saldo_final),
    CONSTRAINT ck_extracto_bancario_confirmacion CHECK (
        (estado = 'CONFIRMADO' AND confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL
            AND confirmado_por <> creado_por AND confirmacion_extracto_id IS NOT NULL)
        OR (estado <> 'CONFIRMADO' AND confirmado_por IS NULL AND confirmado_en IS NULL
            AND confirmacion_extracto_id IS NULL)),
    CONSTRAINT ck_extracto_bancario_rechazo CHECK (
        (estado IN ('RECHAZADO', 'DESCARTADO') AND rechazado_por IS NOT NULL AND rechazado_en IS NOT NULL
            AND motivo_rechazo IS NOT NULL)
        OR (estado IN ('CARGADO', 'CONFIRMADO') AND rechazado_por IS NULL AND rechazado_en IS NULL))
);
CREATE INDEX ix_extracto_bancario_estado ON extracto_bancario (colegio_id, cuenta_id, estado);

-- Movimiento del extracto. SOLO INSERCIÓN, solo mientras el extracto está CARGADO y dentro de sus fechas.
CREATE TABLE movimiento_bancario (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id        BIGINT         NOT NULL,
    extracto_id       BIGINT         NOT NULL,
    cuenta_id         BIGINT         NOT NULL,
    numero            INT            NOT NULL,
    fecha             DATE           NOT NULL,
    tipo              VARCHAR(10)    NOT NULL,
    monto             DECIMAL(12,2)  NOT NULL,
    saldo             DECIMAL(14,2),
    descripcion       VARCHAR(200)   NOT NULL,
    numero_operacion  VARCHAR(30),
    referencia        VARCHAR(60),
    creado_en         DATETIME(6)    NOT NULL,
    creado_por        VARCHAR(60)    NOT NULL,
    actualizado_en    DATETIME(6)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_movimiento_bancario_numero UNIQUE (extracto_id, numero),
    CONSTRAINT uk_movimiento_bancario_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_movimiento_bancario_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_movimiento_bancario_extracto FOREIGN KEY (extracto_id, cuenta_id)
        REFERENCES extracto_bancario (id, cuenta_id),
    CONSTRAINT ck_movimiento_bancario_monto CHECK (monto > 0 AND numero >= 1 AND tipo IN ('ABONO', 'CARGO')),
    CONSTRAINT ck_movimiento_bancario_operacion CHECK (numero_operacion IS NULL
        OR REGEXP_LIKE(numero_operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c'))
);
CREATE INDEX ix_movimiento_bancario_operacion ON movimiento_bancario (colegio_id, numero_operacion);
CREATE INDEX ix_movimiento_bancario_fecha ON movimiento_bancario (colegio_id, fecha, monto);

-- Liquidación de la pasarela: lo que abona al banco, NETO de comisión e IGV de la comisión. Solo inserción.
CREATE TABLE liquidacion_pasarela (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT         NOT NULL,
    proveedor          VARCHAR(20)    NOT NULL,
    referencia         VARCHAR(80)    NOT NULL,
    fecha_liquidacion  DATE           NOT NULL,
    fecha_abono        DATE           NOT NULL,
    total_bruto        DECIMAL(12,2)  NOT NULL,
    total_comision     DECIMAL(12,2)  NOT NULL,
    total_igv          DECIMAL(12,2)  NOT NULL,
    total_neto         DECIMAL(12,2)  NOT NULL,
    lineas             INT            NOT NULL,
    origen             VARCHAR(20)    NOT NULL,
    archivo_id         BIGINT,
    creado_en          DATETIME(6)    NOT NULL,
    creado_por         VARCHAR(60)    NOT NULL,
    actualizado_en     DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_liquidacion_pasarela_referencia UNIQUE (colegio_id, proveedor, referencia),
    CONSTRAINT uk_liquidacion_pasarela_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_liquidacion_pasarela_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_liquidacion_pasarela_archivo FOREIGN KEY (archivo_id, colegio_id) REFERENCES archivo_cargado (id, colegio_id),
    CONSTRAINT ck_liquidacion_pasarela_proveedor CHECK (proveedor IN ('SIMULADA', 'CULQI', 'IZIPAY', 'NIUBIZ')),
    CONSTRAINT ck_liquidacion_pasarela_totales CHECK (total_neto = total_bruto - total_comision - total_igv
        AND total_comision >= 0 AND total_igv >= 0 AND lineas > 0),
    CONSTRAINT ck_liquidacion_pasarela_origen CHECK ((origen = 'API' AND archivo_id IS NULL)
        OR (origen = 'ARCHIVO' AND archivo_id IS NOT NULL))
);

-- Línea de la liquidación: un cargo (o su reembolso o contracargo) con su comisión. Un cargo se liquida una vez.
CREATE TABLE liquidacion_linea (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT         NOT NULL,
    liquidacion_id   BIGINT         NOT NULL,
    numero           INT            NOT NULL,
    tipo             VARCHAR(20)    NOT NULL,
    operacion        VARCHAR(30)    NOT NULL,
    pago_id          BIGINT,
    bruto            DECIMAL(10,2)  NOT NULL,
    comision         DECIMAL(10,2)  NOT NULL,
    igv              DECIMAL(10,2)  NOT NULL,
    neto             DECIMAL(10,2)  NOT NULL,
    creado_en        DATETIME(6)    NOT NULL,
    creado_por       VARCHAR(60)    NOT NULL,
    actualizado_en   DATETIME(6)    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_liquidacion_linea_numero UNIQUE (liquidacion_id, numero),
    CONSTRAINT uk_liquidacion_linea_operacion UNIQUE (colegio_id, tipo, operacion),
    CONSTRAINT fk_liquidacion_linea_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_liquidacion_linea_liquidacion FOREIGN KEY (liquidacion_id, colegio_id)
        REFERENCES liquidacion_pasarela (id, colegio_id),
    CONSTRAINT fk_liquidacion_linea_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT ck_liquidacion_linea_montos CHECK (neto = bruto - comision - igv AND comision >= 0 AND igv >= 0
        AND numero >= 1 AND ((tipo = 'CARGO' AND bruto > 0) OR (tipo IN ('REEMBOLSO', 'CONTRACARGO') AND bruto < 0)
            OR tipo = 'AJUSTE')),
    CONSTRAINT ck_liquidacion_linea_operacion CHECK (REGEXP_LIKE(operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c'))
);

-- Partida: un movimiento del extracto emparejado con UNA cosa que debía verse en el banco (o explicado). 1 a 1:
-- movimiento_vigente y objeto_vigente (p. ej. «PAGO:125») son únicos mientras la partida no esté DESCARTADA.
-- EXACTA: misma operación canónica y mismo monto (la confirma el sistema al confirmarse el extracto). SUGERIDA: misma
-- fecha aproximada y monto (la confirma una persona). MANUAL: la elige una persona. EXPLICADA: abono o cargo ajeno a la
-- cobranza (intereses, transferencia propia...), con categoría y nota.
CREATE TABLE partida_conciliacion (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT         NOT NULL,
    movimiento_id        BIGINT         NOT NULL,
    movimiento_vigente   BIGINT,
    objeto_tipo          VARCHAR(20)    NOT NULL,
    pago_id              BIGINT,
    deposito_id          BIGINT,
    liquidacion_id       BIGINT,
    lote_recaudacion_id  BIGINT,
    reembolso_id         BIGINT,
    objeto_vigente       VARCHAR(40),
    regla                VARCHAR(20)    NOT NULL,
    monto_movimiento     DECIMAL(12,2)  NOT NULL,
    monto_objeto         DECIMAL(12,2)  NOT NULL,
    diferencia           DECIMAL(12,2)  NOT NULL,
    estado               VARCHAR(20)    NOT NULL,
    categoria            VARCHAR(30),
    nota                 VARCHAR(500),
    resuelto_por         VARCHAR(60),
    resuelto_en          DATETIME(6),
    creado_en            DATETIME(6)    NOT NULL,
    creado_por           VARCHAR(60)    NOT NULL,
    actualizado_en       DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_partida_conciliacion_movimiento UNIQUE (colegio_id, movimiento_vigente),
    CONSTRAINT uk_partida_conciliacion_objeto UNIQUE (colegio_id, objeto_vigente),
    CONSTRAINT uk_partida_conciliacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_partida_conciliacion_movimiento FOREIGN KEY (movimiento_id, colegio_id)
        REFERENCES movimiento_bancario (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_deposito FOREIGN KEY (deposito_id, colegio_id)
        REFERENCES deposito_caja (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_liquidacion FOREIGN KEY (liquidacion_id, colegio_id)
        REFERENCES liquidacion_pasarela (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_lote FOREIGN KEY (lote_recaudacion_id, colegio_id)
        REFERENCES lote_recaudacion (id, colegio_id),
    CONSTRAINT fk_partida_conciliacion_reembolso FOREIGN KEY (reembolso_id, colegio_id)
        REFERENCES reembolso (id, colegio_id),
    CONSTRAINT ck_partida_conciliacion_objeto CHECK (
        (objeto_tipo = 'PAGO' AND pago_id IS NOT NULL AND deposito_id IS NULL AND liquidacion_id IS NULL
            AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL)
        OR (objeto_tipo = 'DEPOSITO' AND deposito_id IS NOT NULL AND pago_id IS NULL AND liquidacion_id IS NULL
            AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL)
        OR (objeto_tipo = 'LIQUIDACION' AND liquidacion_id IS NOT NULL AND pago_id IS NULL AND deposito_id IS NULL
            AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL)
        OR (objeto_tipo = 'LOTE_RECAUDACION' AND lote_recaudacion_id IS NOT NULL AND pago_id IS NULL
            AND deposito_id IS NULL AND liquidacion_id IS NULL AND reembolso_id IS NULL)
        OR (objeto_tipo = 'REEMBOLSO' AND reembolso_id IS NOT NULL AND pago_id IS NULL AND deposito_id IS NULL
            AND liquidacion_id IS NULL AND lote_recaudacion_id IS NULL)
        OR (objeto_tipo = 'EXPLICACION' AND regla = 'EXPLICADA' AND pago_id IS NULL AND deposito_id IS NULL
            AND liquidacion_id IS NULL AND lote_recaudacion_id IS NULL AND reembolso_id IS NULL
            AND categoria IS NOT NULL AND nota IS NOT NULL AND monto_objeto = monto_movimiento)),
    CONSTRAINT ck_partida_conciliacion_regla CHECK (regla IN ('EXACTA', 'SUGERIDA', 'MANUAL', 'EXPLICADA')
        AND (regla <> 'EXPLICADA' OR objeto_tipo = 'EXPLICACION')
        AND (regla <> 'EXACTA' OR diferencia = 0)
        AND diferencia = monto_movimiento - monto_objeto AND monto_movimiento > 0 AND monto_objeto > 0),
    CONSTRAINT ck_partida_conciliacion_estado CHECK (estado IN ('PROPUESTA', 'CONFIRMADA', 'DESCARTADA')
        AND ((estado = 'PROPUESTA' AND resuelto_por IS NULL AND resuelto_en IS NULL)
            OR (estado IN ('CONFIRMADA', 'DESCARTADA') AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL))
        AND (estado <> 'CONFIRMADA' OR regla = 'EXACTA' OR resuelto_por NOT LIKE 'sistema%')),
    CONSTRAINT ck_partida_conciliacion_vigente CHECK (
        (estado IN ('PROPUESTA', 'CONFIRMADA') AND movimiento_vigente IS NOT NULL AND movimiento_vigente = movimiento_id
            AND (objeto_tipo = 'EXPLICACION' OR objeto_vigente IS NOT NULL))
        OR (estado = 'DESCARTADA' AND movimiento_vigente IS NULL AND objeto_vigente IS NULL))
);
CREATE INDEX ix_partida_conciliacion_estado ON partida_conciliacion (colegio_id, estado);

-- Verificación bancaria AUTOMATICA: la que deja una partida CONFIRMADA (sin datos escritos a mano). La MANUAL (sprint 3)
-- sigue para las excepciones y exige lo que se vio en el banco.
ALTER TABLE verificacion_bancaria ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE verificacion_bancaria ADD COLUMN partida_id BIGINT;
ALTER TABLE verificacion_bancaria ADD CONSTRAINT fk_verificacion_bancaria_partida FOREIGN KEY (partida_id, colegio_id)
    REFERENCES partida_conciliacion (id, colegio_id);
ALTER TABLE verificacion_bancaria ADD CONSTRAINT ck_verificacion_bancaria_origen CHECK (
    (origen = 'MANUAL' AND partida_id IS NULL)
    OR (origen = 'AUTOMATICA' AND partida_id IS NOT NULL AND resultado = 'ENCONTRADO'));
ALTER TABLE verificacion_bancaria DROP CONSTRAINT ck_verificacion_bancaria_evidencia;
ALTER TABLE verificacion_bancaria ADD CONSTRAINT ck_verificacion_bancaria_evidencia CHECK (resultado <> 'ENCONTRADO'
    OR (banco_fecha IS NOT NULL AND banco_monto IS NOT NULL
        AND (banco_operacion IS NOT NULL OR origen = 'AUTOMATICA')));
