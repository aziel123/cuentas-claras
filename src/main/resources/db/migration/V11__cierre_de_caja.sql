-- Sprint 3, tanda 3: cierre de caja (conteo a ciegas, diferencia y revisión), depósito del efectivo y verificación
-- bancaria de pagos digitales y depósitos por Administración. cierre_caja: el conteo no cambia, solo se revisa.
-- deposito_caja y verificacion_bancaria: SOLO INSERCIÓN.

-- Cierre de una caja. numero = caja_diaria.cierres (1 el primero; 2 tras una reapertura aprobada...).
-- esperado = fondo_fijo + efectivo_cobrado (pagos en efectivo VIGENTES de la caja); lo recalcula el trigger en MySQL.
-- primer_conteo: lo que contó a ciegas; contado: el conteo final (igual al primero si no hubo reconteo).
CREATE TABLE cierre_caja (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT         NOT NULL,
    caja_diaria_id       BIGINT         NOT NULL,
    numero               INT            NOT NULL,
    fondo_fijo           DECIMAL(10,2)  NOT NULL,
    efectivo_cobrado     DECIMAL(10,2)  NOT NULL,
    esperado             DECIMAL(10,2)  NOT NULL,
    primer_conteo        DECIMAL(10,2)  NOT NULL,
    contado              DECIMAL(10,2)  NOT NULL,
    diferencia           DECIMAL(10,2)  NOT NULL,
    denominaciones       VARCHAR(300),
    explicacion          VARCHAR(500),
    pagos_efectivo       INT            NOT NULL,
    pagos_digitales      INT            NOT NULL,
    total_digital        DECIMAL(10,2)  NOT NULL,
    estado               VARCHAR(20)    NOT NULL,
    revisado_por         VARCHAR(60),
    revisado_en          DATETIME(6),
    comentario_revision  VARCHAR(500),
    creado_en            DATETIME(6)    NOT NULL,
    creado_por           VARCHAR(60)    NOT NULL,
    actualizado_en       DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_cierre_caja_numero UNIQUE (colegio_id, caja_diaria_id, numero),
    CONSTRAINT uk_cierre_caja_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_cierre_caja_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cierre_caja_caja FOREIGN KEY (caja_diaria_id, colegio_id) REFERENCES caja_diaria (id, colegio_id),
    CONSTRAINT ck_cierre_caja_montos CHECK (numero >= 1 AND fondo_fijo >= 0 AND efectivo_cobrado >= 0
        AND esperado = fondo_fijo + efectivo_cobrado AND primer_conteo >= 0 AND contado >= 0
        AND diferencia = contado - esperado AND pagos_efectivo >= 0 AND pagos_digitales >= 0 AND total_digital >= 0),
    CONSTRAINT ck_cierre_caja_decimos CHECK (MOD(primer_conteo * 10, 1) = 0 AND MOD(contado * 10, 1) = 0),
    CONSTRAINT ck_cierre_caja_explicacion CHECK (diferencia = 0 OR explicacion IS NOT NULL),
    CONSTRAINT ck_cierre_caja_estado CHECK ((estado = 'POR_REVISAR' AND revisado_por IS NULL AND revisado_en IS NULL)
        OR (estado IN ('APROBADO', 'OBSERVADO') AND revisado_por IS NOT NULL AND revisado_en IS NOT NULL
            AND revisado_por <> creado_por)),
    CONSTRAINT ck_cierre_caja_observado CHECK (estado <> 'OBSERVADO' OR comentario_revision IS NOT NULL)
);
CREATE INDEX ix_cierre_caja_estado ON cierre_caja (colegio_id, estado);

-- Depósito en el banco del efectivo de una caja (una por caja en el MVP).
CREATE TABLE deposito_caja (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id        BIGINT         NOT NULL,
    caja_diaria_id    BIGINT         NOT NULL,
    cuenta            VARCHAR(60)    NOT NULL,
    numero_operacion  VARCHAR(30)    NOT NULL,
    fecha_deposito    DATE           NOT NULL,
    monto             DECIMAL(10,2)  NOT NULL,
    esperado          DECIMAL(10,2)  NOT NULL,
    explicacion       VARCHAR(500),
    creado_en         DATETIME(6)    NOT NULL,
    creado_por        VARCHAR(60)    NOT NULL,
    actualizado_en    DATETIME(6)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_deposito_caja_caja UNIQUE (colegio_id, caja_diaria_id),
    CONSTRAINT uk_deposito_caja_operacion UNIQUE (colegio_id, cuenta, numero_operacion),
    CONSTRAINT uk_deposito_caja_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_deposito_caja_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_deposito_caja_caja FOREIGN KEY (caja_diaria_id, colegio_id) REFERENCES caja_diaria (id, colegio_id),
    CONSTRAINT ck_deposito_caja_monto CHECK (monto > 0 AND esperado >= 0 AND MOD(monto * 10, 1) = 0),
    CONSTRAINT ck_deposito_caja_explicacion CHECK (monto = esperado OR explicacion IS NOT NULL)
);

-- Verificación contra el estado de cuenta del banco (o de Yape/Plin) de un pago digital o de un depósito.
-- La hace Administración; nunca quien cobró o depositó (trigger en MySQL). Una por pago o depósito.
CREATE TABLE verificacion_bancaria (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    pago_id         BIGINT,
    deposito_id     BIGINT,
    resultado       VARCHAR(20)    NOT NULL,
    nota            VARCHAR(500),
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_verificacion_bancaria_pago UNIQUE (colegio_id, pago_id),
    CONSTRAINT uk_verificacion_bancaria_deposito UNIQUE (colegio_id, deposito_id),
    CONSTRAINT fk_verificacion_bancaria_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_verificacion_bancaria_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT fk_verificacion_bancaria_deposito FOREIGN KEY (deposito_id, colegio_id)
        REFERENCES deposito_caja (id, colegio_id),
    CONSTRAINT ck_verificacion_bancaria_objeto CHECK ((pago_id IS NOT NULL AND deposito_id IS NULL)
        OR (pago_id IS NULL AND deposito_id IS NOT NULL)),
    CONSTRAINT ck_verificacion_bancaria_resultado CHECK (resultado IN ('ENCONTRADO', 'NO_ENCONTRADO')
        AND (resultado = 'ENCONTRADO' OR nota IS NOT NULL))
);
