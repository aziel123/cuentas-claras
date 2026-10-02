-- Sprint 3, tanda 2: anulación de pagos (aprobada por otra persona, con nota de crédito) y descuentos o becas.
-- anulacion_pago y ajuste_cuota son de SOLO INSERCIÓN. descuento: lo pedido no cambia, solo se resuelve.

-- Anulación aprobada de un pago: una por pago. Quien aprueba no es quien la pidió ni quien cobró (también en la base).
-- posterior_al_cierre: el pago era de una caja ya cerrada; ese cierre no se toca y la anulación se muestra como
-- ajuste posterior (DEVOLUCION) o se compensa con el pago de reemplazo en la misma caja (CORRECCION).
CREATE TABLE anulacion_pago (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT         NOT NULL,
    pago_id              BIGINT         NOT NULL,
    solicitud_id         BIGINT         NOT NULL,
    nota_credito_id      BIGINT         NOT NULL,
    tipo                 VARCHAR(20)    NOT NULL,
    motivo               VARCHAR(500)   NOT NULL,
    monto                DECIMAL(10,2)  NOT NULL,
    cajero_pago          VARCHAR(60)    NOT NULL,
    solicitado_por       VARCHAR(60)    NOT NULL,
    aprobado_por         VARCHAR(60)    NOT NULL,
    posterior_al_cierre  BOOLEAN        NOT NULL,
    creado_en            DATETIME(6)    NOT NULL,
    creado_por           VARCHAR(60)    NOT NULL,
    actualizado_en       DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_anulacion_pago_pago UNIQUE (colegio_id, pago_id),
    CONSTRAINT uk_anulacion_pago_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT uk_anulacion_pago_nota UNIQUE (colegio_id, nota_credito_id),
    CONSTRAINT fk_anulacion_pago_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_anulacion_pago_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT fk_anulacion_pago_solicitud FOREIGN KEY (solicitud_id, colegio_id)
        REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT fk_anulacion_pago_nota FOREIGN KEY (nota_credito_id, colegio_id) REFERENCES comprobante (id, colegio_id),
    CONSTRAINT ck_anulacion_pago_tipo CHECK (tipo IN ('DEVOLUCION', 'CORRECCION')),
    CONSTRAINT ck_anulacion_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_anulacion_pago_segregacion CHECK (aprobado_por = creado_por AND aprobado_por <> solicitado_por
        AND aprobado_por <> cajero_pago)
);

-- Descuento o beca pedido por Administración para cuotas de UN alumno; lo aprueba Promotoría o Dirección.
-- cuotas: «,12,13,14,» (las cuotas pedidas). total_estimado: lo que dejaría de cobrarse, para quien aprueba.
CREATE TABLE descuento (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT         NOT NULL,
    alumno_id        BIGINT         NOT NULL,
    anio_escolar_id  BIGINT         NOT NULL,
    tipo             VARCHAR(20)    NOT NULL,
    modalidad        VARCHAR(20)    NOT NULL,
    valor            DECIMAL(10,2)  NOT NULL,
    cuotas           VARCHAR(500)   NOT NULL,
    total_estimado   DECIMAL(10,2)  NOT NULL,
    motivo           VARCHAR(500)   NOT NULL,
    sustento         VARCHAR(200)   NOT NULL,
    estado           VARCHAR(20)    NOT NULL,
    resuelto_por     VARCHAR(60),
    resuelto_en      DATETIME(6),
    creado_en        DATETIME(6)    NOT NULL,
    creado_por       VARCHAR(60)    NOT NULL,
    actualizado_en   DATETIME(6)    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_descuento_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_descuento_id_alumno UNIQUE (id, alumno_id),
    CONSTRAINT fk_descuento_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_descuento_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_descuento_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_descuento_tipo CHECK (tipo IN ('HERMANOS', 'BECA', 'OTRO')),
    CONSTRAINT ck_descuento_valor CHECK ((modalidad = 'PORCENTAJE' AND valor > 0 AND valor <= 100)
        OR (modalidad = 'MONTO' AND valor > 0 AND valor <= 99999.99)),
    CONSTRAINT ck_descuento_total CHECK (total_estimado > 0),
    CONSTRAINT ck_descuento_cuotas CHECK (cuotas LIKE ',%,'),
    CONSTRAINT ck_descuento_estado CHECK ((estado = 'SOLICITADO' AND resuelto_por IS NULL AND resuelto_en IS NULL)
        OR (estado IN ('APROBADO', 'RECHAZADO') AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL
            AND resuelto_por <> creado_por))
);
CREATE INDEX ix_descuento_estado ON descuento (colegio_id, estado);

-- Ajuste de una cuota por un descuento aprobado. SOLO INSERCIÓN. cuota.monto_descuento = suma de sus ajustes.
-- (descuento_id, alumno_id) impide aplicar el descuento de un alumno a la cuota de otro... junto con el trigger.
CREATE TABLE ajuste_cuota (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    cuota_id        BIGINT         NOT NULL,
    descuento_id    BIGINT         NOT NULL,
    monto           DECIMAL(10,2)  NOT NULL,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_ajuste_cuota_unico UNIQUE (colegio_id, cuota_id, descuento_id),
    CONSTRAINT fk_ajuste_cuota_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_ajuste_cuota_cuota FOREIGN KEY (cuota_id, colegio_id) REFERENCES cuota (id, colegio_id),
    CONSTRAINT fk_ajuste_cuota_descuento FOREIGN KEY (descuento_id, colegio_id) REFERENCES descuento (id, colegio_id),
    CONSTRAINT ck_ajuste_cuota_monto CHECK (monto > 0)
);
CREATE INDEX ix_ajuste_cuota_cuota ON ajuste_cuota (cuota_id, colegio_id);
