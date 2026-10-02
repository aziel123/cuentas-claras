-- Sprint 2, tanda 3: planes de pensiones, saldo inicial (D7) y cuotas.
-- Tablas financieras: cc_app tiene INSERT y UPDATE, nunca DELETE. En "cuota" el UPDATE es solo por columna
-- (scripts/mysql/02-permisos-tablas.sql): el monto, la fecha, el alumno y la clave no se pueden cambiar ni por SQL.

-- Plan de pensiones de un nivel en un año. Versionado: un plan APROBADO no se edita; un cambio es una versión
-- nueva que Promotoría o Dirección aprueban. Solo una versión vigente por año y nivel (vigente = TRUE o NULL).
CREATE TABLE plan_pension (
    id                     BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id             BIGINT         NOT NULL,
    anio_escolar_id        BIGINT         NOT NULL,
    nivel                  VARCHAR(20)    NOT NULL,
    numero_version         INT            NOT NULL,
    estado                 VARCHAR(20)    NOT NULL,
    vigente                BOOLEAN,
    monto_matricula        DECIMAL(10,2)  NOT NULL,
    vencimiento_matricula  DATE           NOT NULL,
    monto_pension          DECIMAL(10,2)  NOT NULL,
    vencimientos_pension   VARCHAR(140)   NOT NULL,
    cobro_desde            DATE,
    motivo_cambio          VARCHAR(500),
    editado_por            VARCHAR(60)    NOT NULL,
    aprobado_por           VARCHAR(60),
    aprobado_en            DATETIME(6),
    cerrado_por            VARCHAR(60),
    cerrado_en             DATETIME(6),
    creado_en              DATETIME(6)    NOT NULL,
    creado_por             VARCHAR(60)    NOT NULL,
    actualizado_en         DATETIME(6)    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_pension_version UNIQUE (colegio_id, anio_escolar_id, nivel, numero_version),
    CONSTRAINT uk_plan_pension_vigente UNIQUE (colegio_id, anio_escolar_id, nivel, vigente),
    CONSTRAINT uk_plan_pension_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_plan_pension_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_plan_pension_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_plan_pension_nivel CHECK (nivel IN ('INICIAL', 'PRIMARIA', 'SECUNDARIA')),
    CONSTRAINT ck_plan_pension_estado CHECK (estado IN ('BORRADOR', 'APROBADO', 'REEMPLAZADO', 'DESCARTADO')),
    CONSTRAINT ck_plan_pension_vigente CHECK ((estado = 'APROBADO' AND vigente IS NOT NULL AND vigente = TRUE)
        OR (estado <> 'APROBADO' AND vigente IS NULL)),
    CONSTRAINT ck_plan_pension_montos CHECK (monto_pension > 0 AND monto_matricula >= 0),
    CONSTRAINT ck_plan_pension_version CHECK (numero_version >= 1),
    CONSTRAINT ck_plan_pension_motivo CHECK (numero_version = 1 OR motivo_cambio IS NOT NULL),
    CONSTRAINT ck_plan_pension_aprobacion CHECK (estado IN ('BORRADOR', 'DESCARTADO')
        OR (aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL AND aprobado_por <> creado_por
            AND aprobado_por <> editado_por))
);

-- Lote de saldo inicial (D7): deudas previas al sistema, validadas por el contador.
-- Administración lo arma y lo envía; Promotoría o Dirección (otra persona) lo confirma.
CREATE TABLE lote_saldo_inicial (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT         NOT NULL,
    anio_escolar_id       BIGINT         NOT NULL,
    fecha_corte           DATE           NOT NULL,
    documento_referencia  VARCHAR(150)   NOT NULL,
    total_declarado       DECIMAL(10,2)  NOT NULL,
    estado                VARCHAR(20)    NOT NULL,
    enviado_por           VARCHAR(60),
    enviado_en            DATETIME(6),
    confirmado_por        VARCHAR(60),
    confirmado_en         DATETIME(6),
    devuelto_por          VARCHAR(60),
    devuelto_en           DATETIME(6),
    motivo_devolucion     VARCHAR(500),
    descartado_por        VARCHAR(60),
    descartado_en         DATETIME(6),
    motivo_descarte       VARCHAR(500),
    creado_en             DATETIME(6)    NOT NULL,
    creado_por            VARCHAR(60)    NOT NULL,
    actualizado_en        DATETIME(6)    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_lote_saldo_inicial_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_lote_saldo_inicial_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_lote_saldo_inicial_anio FOREIGN KEY (anio_escolar_id, colegio_id)
        REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_lote_saldo_inicial_estado CHECK (estado IN ('BORRADOR', 'ENVIADO', 'CONFIRMADO', 'DESCARTADO')),
    CONSTRAINT ck_lote_saldo_inicial_total CHECK (total_declarado > 0),
    CONSTRAINT ck_lote_saldo_inicial_envio CHECK (estado NOT IN ('ENVIADO', 'CONFIRMADO')
        OR (enviado_por IS NOT NULL AND enviado_en IS NOT NULL)),
    CONSTRAINT ck_lote_saldo_inicial_doble_control CHECK (estado <> 'CONFIRMADO'
        OR (confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL
            AND confirmado_por <> creado_por AND confirmado_por <> enviado_por))
);

CREATE TABLE linea_saldo_inicial (
    id                 BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT         NOT NULL,
    lote_id            BIGINT         NOT NULL,
    alumno_id          BIGINT         NOT NULL,
    concepto           VARCHAR(20)    NOT NULL,
    mes                INT,
    descripcion        VARCHAR(80)    NOT NULL,
    monto              DECIMAL(10,2)  NOT NULL,
    fecha_vencimiento  DATE           NOT NULL,
    quitada            BOOLEAN        NOT NULL DEFAULT FALSE,
    creado_en          DATETIME(6)    NOT NULL,
    creado_por         VARCHAR(60)    NOT NULL,
    actualizado_en     DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_linea_saldo_inicial_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_linea_saldo_inicial_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_linea_saldo_inicial_lote FOREIGN KEY (lote_id, colegio_id)
        REFERENCES lote_saldo_inicial (id, colegio_id),
    CONSTRAINT fk_linea_saldo_inicial_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT ck_linea_saldo_inicial_concepto CHECK (concepto IN ('MATRICULA', 'PENSION', 'OTRO')),
    CONSTRAINT ck_linea_saldo_inicial_mes CHECK ((concepto = 'PENSION' AND mes IS NOT NULL AND mes BETWEEN 1 AND 12)
        OR (concepto <> 'PENSION' AND mes IS NULL)),
    CONSTRAINT ck_linea_saldo_inicial_monto CHECK (monto > 0)
);
CREATE INDEX ix_linea_saldo_inicial_lote ON linea_saldo_inicial (colegio_id, lote_id);

-- Cuota: obligación de pago generada por el sistema (MATRICULA, PENSION) o confirmada como SALDO_INICIAL.
-- "clave": idempotencia de cada creación (nunca cambia). "obligacion": impide cobrar dos veces el mismo mes o la
-- misma matrícula al mismo alumno; vale NULL en las anuladas. VENCIDA no se guarda: se calcula con la fecha de Lima.
CREATE TABLE cuota (
    id                        BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id                BIGINT         NOT NULL,
    alumno_id                 BIGINT         NOT NULL,
    anio_escolar_id           BIGINT         NOT NULL,
    matricula_id              BIGINT,
    plan_pension_id           BIGINT,
    linea_saldo_inicial_id    BIGINT,
    tipo                      VARCHAR(20)    NOT NULL,
    numero                    INT,
    descripcion               VARCHAR(80)    NOT NULL,
    monto                     DECIMAL(10,2)  NOT NULL,
    monto_pagado              DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    fecha_vencimiento         DATE           NOT NULL,
    estado                    VARCHAR(20)    NOT NULL,
    clave                     VARCHAR(80)    NOT NULL,
    obligacion                VARCHAR(20),
    anulacion_motivo          VARCHAR(500),
    anulacion_solicitada_por  VARCHAR(60),
    anulacion_aprobada_por    VARCHAR(60),
    anulada_en                DATETIME(6),
    creado_en                 DATETIME(6)    NOT NULL,
    creado_por                VARCHAR(60)    NOT NULL,
    actualizado_en            DATETIME(6)    NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_cuota_clave UNIQUE (colegio_id, clave),
    CONSTRAINT uk_cuota_obligacion UNIQUE (colegio_id, alumno_id, obligacion),
    CONSTRAINT uk_cuota_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_cuota_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cuota_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_cuota_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_cuota_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_cuota_plan FOREIGN KEY (plan_pension_id, colegio_id) REFERENCES plan_pension (id, colegio_id),
    CONSTRAINT fk_cuota_linea FOREIGN KEY (linea_saldo_inicial_id, colegio_id)
        REFERENCES linea_saldo_inicial (id, colegio_id),
    CONSTRAINT ck_cuota_tipo CHECK (tipo IN ('MATRICULA', 'PENSION', 'SALDO_INICIAL')),
    CONSTRAINT ck_cuota_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'PAGADA', 'ANULADA')),
    CONSTRAINT ck_cuota_origen CHECK ((tipo IN ('MATRICULA', 'PENSION') AND matricula_id IS NOT NULL
            AND plan_pension_id IS NOT NULL AND linea_saldo_inicial_id IS NULL)
        OR (tipo = 'SALDO_INICIAL' AND linea_saldo_inicial_id IS NOT NULL AND plan_pension_id IS NULL)),
    CONSTRAINT ck_cuota_numero CHECK ((tipo = 'PENSION' AND numero IS NOT NULL AND numero BETWEEN 1 AND 12)
        OR (tipo <> 'PENSION' AND numero IS NULL)),
    CONSTRAINT ck_cuota_montos CHECK (monto > 0 AND monto_pagado >= 0 AND monto_pagado <= monto),
    CONSTRAINT ck_cuota_estado_pago CHECK ((estado = 'PENDIENTE' AND monto_pagado = 0)
        OR (estado = 'PARCIAL' AND monto_pagado > 0 AND monto_pagado < monto)
        OR (estado = 'PAGADA' AND monto_pagado = monto)
        OR (estado = 'ANULADA' AND monto_pagado = 0)),
    CONSTRAINT ck_cuota_anulacion CHECK ((estado = 'ANULADA' AND obligacion IS NULL AND anulada_en IS NOT NULL
            AND anulacion_motivo IS NOT NULL AND anulacion_solicitada_por IS NOT NULL
            AND anulacion_aprobada_por IS NOT NULL AND anulacion_aprobada_por <> anulacion_solicitada_por)
        OR (estado <> 'ANULADA' AND anulada_en IS NULL AND anulacion_aprobada_por IS NULL))
);
CREATE INDEX ix_cuota_alumno ON cuota (colegio_id, alumno_id, fecha_vencimiento);
CREATE INDEX ix_cuota_estado_vencimiento ON cuota (colegio_id, estado, fecha_vencimiento);
CREATE INDEX ix_cuota_matricula ON cuota (colegio_id, matricula_id);
