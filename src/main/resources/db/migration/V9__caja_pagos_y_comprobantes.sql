-- Sprint 3, tanda 1: comprobantes con numeración sin huecos, caja diaria por cajero, pagos y su libro de aplicaciones.
-- Nada se borra (sin DELETE para cc_app). comprobante_linea y aplicacion_pago son de SOLO INSERCIÓN; en serie, comprobante,
-- caja_diaria, pago y cuota el UPDATE es por columna (scripts/mysql/02-permisos-tablas.sql) y los triggers de
-- scripts/mysql/03-triggers.sql vigilan lo que el GRANT no distingue (correlativo, monto pagado = libro, caja cerrada).

-- Serie de comprobantes (B001, F001, BC01, FC01) con su último número. Se bloquea (SELECT ... FOR UPDATE) para emitir:
-- el número y el comprobante se guardan en la misma transacción, así un error no deja huecos.
CREATE TABLE serie_comprobante (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT       NOT NULL,
    tipo            VARCHAR(20)  NOT NULL,
    serie           VARCHAR(4)   NOT NULL,
    proveedor       VARCHAR(20)  NOT NULL,
    ultimo_numero   INT          NOT NULL DEFAULT 0,
    creado_en       DATETIME(6)  NOT NULL,
    creado_por      VARCHAR(60)  NOT NULL,
    actualizado_en  DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_serie_comprobante_serie UNIQUE (colegio_id, serie),
    CONSTRAINT uk_serie_comprobante_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_serie_comprobante_clave UNIQUE (id, colegio_id, tipo, serie),
    CONSTRAINT fk_serie_comprobante_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_serie_comprobante_tipo CHECK (tipo IN ('BOLETA', 'FACTURA', 'NOTA_CREDITO')),
    CONSTRAINT ck_serie_comprobante_formato CHECK (CHAR_LENGTH(serie) = 4
        AND ((tipo = 'BOLETA' AND serie LIKE 'B%') OR (tipo = 'FACTURA' AND serie LIKE 'F%')
            OR (tipo = 'NOTA_CREDITO' AND (serie LIKE 'B%' OR serie LIKE 'F%')))),
    CONSTRAINT ck_serie_comprobante_proveedor CHECK (proveedor IN ('SIMULADO', 'NUBEFACT')),
    CONSTRAINT ck_serie_comprobante_numero CHECK (ultimo_numero BETWEEN 0 AND 99999999)
);

-- Comprobante emitido (boleta, factura o nota de crédito). Sus datos tributarios no cambian; solo su estado de envío al
-- OSE. Una nota de crédito anula por completo UN comprobante (uk_comprobante_modifica).
CREATE TABLE comprobante (
    id                         BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id                 BIGINT         NOT NULL,
    serie_id                   BIGINT         NOT NULL,
    tipo                       VARCHAR(20)    NOT NULL,
    serie                      VARCHAR(4)     NOT NULL,
    numero                     INT            NOT NULL,
    fecha_emision              DATE           NOT NULL,
    receptor_tipo_documento    VARCHAR(20)    NOT NULL,
    receptor_numero_documento  VARCHAR(12)    NOT NULL,
    receptor_nombre            VARCHAR(150)   NOT NULL,
    moneda                     VARCHAR(3)     NOT NULL,
    total                      DECIMAL(10,2)  NOT NULL,
    afectacion_igv             VARCHAR(20)    NOT NULL,
    modifica_id                BIGINT,
    motivo_nota                VARCHAR(250),
    proveedor                  VARCHAR(20)    NOT NULL,
    estado_envio               VARCHAR(20)    NOT NULL,
    intentos                   INT            NOT NULL DEFAULT 0,
    enviado_en                 DATETIME(6),
    respuesta                  VARCHAR(500),
    codigo_hash                VARCHAR(100),
    enlace_pdf                 VARCHAR(300),
    creado_en                  DATETIME(6)    NOT NULL,
    creado_por                 VARCHAR(60)    NOT NULL,
    actualizado_en             DATETIME(6)    NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_comprobante_numero UNIQUE (colegio_id, serie, numero),
    CONSTRAINT uk_comprobante_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_comprobante_modifica UNIQUE (colegio_id, modifica_id),
    CONSTRAINT fk_comprobante_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_comprobante_serie FOREIGN KEY (serie_id, colegio_id, tipo, serie)
        REFERENCES serie_comprobante (id, colegio_id, tipo, serie),
    CONSTRAINT fk_comprobante_modifica FOREIGN KEY (modifica_id, colegio_id) REFERENCES comprobante (id, colegio_id),
    CONSTRAINT ck_comprobante_numero CHECK (numero BETWEEN 1 AND 99999999),
    CONSTRAINT ck_comprobante_total CHECK (total > 0),
    CONSTRAINT ck_comprobante_moneda CHECK (moneda = 'PEN'),
    CONSTRAINT ck_comprobante_afectacion CHECK (afectacion_igv IN ('INAFECTO', 'EXONERADO')),
    CONSTRAINT ck_comprobante_receptor CHECK (receptor_tipo_documento IN ('DNI', 'CE', 'PASAPORTE', 'RUC')
        AND (receptor_tipo_documento <> 'RUC' OR CHAR_LENGTH(receptor_numero_documento) = 11)
        AND (receptor_tipo_documento <> 'DNI' OR CHAR_LENGTH(receptor_numero_documento) = 8)),
    CONSTRAINT ck_comprobante_tipo CHECK ((tipo = 'FACTURA' AND receptor_tipo_documento = 'RUC' AND modifica_id IS NULL
            AND motivo_nota IS NULL)
        OR (tipo = 'BOLETA' AND receptor_tipo_documento <> 'RUC' AND modifica_id IS NULL AND motivo_nota IS NULL)
        OR (tipo = 'NOTA_CREDITO' AND modifica_id IS NOT NULL AND motivo_nota IS NOT NULL)),
    CONSTRAINT ck_comprobante_proveedor CHECK (proveedor IN ('SIMULADO', 'NUBEFACT')),
    CONSTRAINT ck_comprobante_envio CHECK (estado_envio IN ('PENDIENTE', 'ACEPTADO', 'OBSERVADO', 'RECHAZADO')
        AND intentos >= 0)
);
CREATE INDEX ix_comprobante_fecha ON comprobante (colegio_id, fecha_emision);
CREATE INDEX ix_comprobante_envio ON comprobante (colegio_id, estado_envio);

-- Líneas del comprobante (una por cuota pagada). Solo inserción.
CREATE TABLE comprobante_linea (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    comprobante_id  BIGINT         NOT NULL,
    orden           INT            NOT NULL,
    descripcion     VARCHAR(250)   NOT NULL,
    monto           DECIMAL(10,2)  NOT NULL,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_comprobante_linea_orden UNIQUE (comprobante_id, orden),
    CONSTRAINT fk_comprobante_linea_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_comprobante_linea_comprobante FOREIGN KEY (comprobante_id, colegio_id)
        REFERENCES comprobante (id, colegio_id),
    CONSTRAINT ck_comprobante_linea_monto CHECK (monto > 0 AND orden >= 1)
);

-- Caja de un cajero en un día (hora de Lima). Se abre con el primer pago; la abre y la cierra su cajero.
-- "cierres" cuenta los cierres hechos: cada cierre es una fila de cierre_caja con numero = cierres.
CREATE TABLE caja_diaria (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    cajero          VARCHAR(60)    NOT NULL,
    fecha           DATE           NOT NULL,
    fondo_fijo      DECIMAL(10,2)  NOT NULL,
    estado          VARCHAR(20)    NOT NULL,
    cierres         INT            NOT NULL DEFAULT 0,
    conteos         INT            NOT NULL DEFAULT 0,
    primer_conteo   DECIMAL(10,2),
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_caja_diaria_cajero_fecha UNIQUE (colegio_id, cajero, fecha),
    CONSTRAINT uk_caja_diaria_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_caja_diaria_id_cajero_fecha UNIQUE (id, cajero, fecha),
    CONSTRAINT fk_caja_diaria_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_caja_diaria_estado CHECK (estado IN ('ABIERTA', 'CERRADA')),
    CONSTRAINT ck_caja_diaria_cajero CHECK (cajero = creado_por),
    CONSTRAINT ck_caja_diaria_montos CHECK (fondo_fijo >= 0 AND cierres >= 0 AND conteos >= 0),
    CONSTRAINT ck_caja_diaria_conteo CHECK ((conteos = 0 AND primer_conteo IS NULL)
        OR (conteos > 0 AND primer_conteo IS NOT NULL AND primer_conteo >= 0 AND MOD(primer_conteo * 10, 1) = 0)),
    CONSTRAINT ck_caja_diaria_cierres CHECK (estado = 'ABIERTA' OR cierres >= 1)
);

-- Pago: dinero recibido de UNA familia. Libro de solo inserción salvo la anulación (estado y operacion_vigente).
-- operacion_vigente: el número de operación de un pago digital mientras está vigente (NULL al anularse); su UNIQUE
-- impide registrar dos veces el mismo Yape, Plin, transferencia o voucher.
-- (caja_diaria_id, cajero, fecha) apunta a la caja de ESE cajero en ESE día: la base rechaza mezclar cajas.
CREATE TABLE pago (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id          BIGINT         NOT NULL,
    familia_id          BIGINT         NOT NULL,
    caja_diaria_id      BIGINT         NOT NULL,
    cajero              VARCHAR(60)    NOT NULL,
    fecha               DATE           NOT NULL,
    comprobante_id      BIGINT         NOT NULL,
    medio               VARCHAR(20)    NOT NULL,
    numero_operacion    VARCHAR(30),
    operacion_vigente   VARCHAR(30),
    total               DECIMAL(10,2)  NOT NULL,
    recibido            DECIMAL(10,2),
    vuelto              DECIMAL(10,2),
    a_cuenta            BOOLEAN        NOT NULL DEFAULT FALSE,
    origen              VARCHAR(20)    NOT NULL,
    reemplaza_pago_id   BIGINT,
    clave_idempotencia  VARCHAR(36)    NOT NULL,
    estado              VARCHAR(20)    NOT NULL,
    creado_en           DATETIME(6)    NOT NULL,
    creado_por          VARCHAR(60)    NOT NULL,
    actualizado_en      DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_pago_idempotencia UNIQUE (colegio_id, clave_idempotencia),
    CONSTRAINT uk_pago_operacion UNIQUE (colegio_id, medio, operacion_vigente),
    CONSTRAINT uk_pago_comprobante UNIQUE (comprobante_id),
    CONSTRAINT uk_pago_reemplaza UNIQUE (reemplaza_pago_id),
    CONSTRAINT uk_pago_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_pago_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_pago_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_pago_caja FOREIGN KEY (caja_diaria_id, colegio_id) REFERENCES caja_diaria (id, colegio_id),
    CONSTRAINT fk_pago_caja_cajero FOREIGN KEY (caja_diaria_id, cajero, fecha) REFERENCES caja_diaria (id, cajero, fecha),
    CONSTRAINT fk_pago_comprobante FOREIGN KEY (comprobante_id, colegio_id) REFERENCES comprobante (id, colegio_id),
    CONSTRAINT fk_pago_reemplaza FOREIGN KEY (reemplaza_pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT ck_pago_medio CHECK (medio IN ('EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA')),
    CONSTRAINT ck_pago_estado CHECK (estado IN ('VIGENTE', 'ANULADO')),
    CONSTRAINT ck_pago_total CHECK (total > 0 AND total <= 99999.99),
    CONSTRAINT ck_pago_efectivo CHECK ((medio = 'EFECTIVO' AND numero_operacion IS NULL AND operacion_vigente IS NULL
            AND recibido IS NOT NULL AND vuelto IS NOT NULL AND vuelto >= 0 AND vuelto < 200
            AND recibido = total + vuelto)
        OR (medio <> 'EFECTIVO' AND numero_operacion IS NOT NULL AND recibido IS NULL AND vuelto IS NULL
            AND ((estado = 'VIGENTE' AND operacion_vigente IS NOT NULL AND operacion_vigente = numero_operacion)
                OR (estado = 'ANULADO' AND operacion_vigente IS NULL)))),
    -- Sin monedas de 1 ni 5 céntimos (BCRP, 2019): en efectivo todo es múltiplo de S/ 0.10.
    CONSTRAINT ck_pago_efectivo_decimos CHECK (medio <> 'EFECTIVO'
        OR (MOD(total * 10, 1) = 0 AND MOD(recibido * 10, 1) = 0)),
    CONSTRAINT ck_pago_origen CHECK ((origen = 'CAJA' AND reemplaza_pago_id IS NULL AND creado_por = cajero)
        OR (origen = 'REEMPLAZO' AND reemplaza_pago_id IS NOT NULL AND creado_por <> cajero))
);
CREATE INDEX ix_pago_caja ON pago (colegio_id, caja_diaria_id, medio, estado);
CREATE INDEX ix_pago_familia ON pago (colegio_id, familia_id, fecha);
CREATE INDEX ix_pago_fecha ON pago (colegio_id, fecha);

-- Libro de aplicaciones: cuánto de cada pago se aplicó a cada cuota. SOLO INSERCIÓN. Al anular un pago se agregan
-- REVERSIONES (monto negativo) que apuntan a la aplicación original del mismo pago y la misma cuota.
-- cuota.monto_pagado = suma de las filas de la cuota (lo verifica el trigger trg_cuota_libro en MySQL).
CREATE TABLE aplicacion_pago (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    pago_id         BIGINT         NOT NULL,
    cuota_id        BIGINT         NOT NULL,
    tipo            VARCHAR(20)    NOT NULL,
    monto           DECIMAL(10,2)  NOT NULL,
    revierte_id     BIGINT,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_aplicacion_pago_unica UNIQUE (colegio_id, pago_id, cuota_id, tipo),
    CONSTRAINT uk_aplicacion_pago_id_pago_cuota UNIQUE (id, pago_id, cuota_id),
    CONSTRAINT fk_aplicacion_pago_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_aplicacion_pago_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT fk_aplicacion_pago_cuota FOREIGN KEY (cuota_id, colegio_id) REFERENCES cuota (id, colegio_id),
    CONSTRAINT fk_aplicacion_pago_revierte FOREIGN KEY (revierte_id, pago_id, cuota_id)
        REFERENCES aplicacion_pago (id, pago_id, cuota_id),
    CONSTRAINT ck_aplicacion_pago_tipo CHECK ((tipo = 'APLICACION' AND revierte_id IS NULL AND monto > 0)
        OR (tipo = 'REVERSION' AND revierte_id IS NOT NULL AND monto < 0))
);
CREATE INDEX ix_aplicacion_pago_cuota ON aplicacion_pago (cuota_id, colegio_id);

-- Cuota: el descuento aprobado también es un libro (ajuste_cuota, tanda 2). Saldo = monto - descuento - pagado.
-- EXONERADA: un descuento o beca del 100 % (saldo 0 sin pagos). Se reemplazan los CHECK de V7.
ALTER TABLE cuota ADD COLUMN monto_descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE cuota DROP CONSTRAINT ck_cuota_estado;
ALTER TABLE cuota ADD CONSTRAINT ck_cuota_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'PAGADA', 'EXONERADA',
    'ANULADA'));
ALTER TABLE cuota DROP CONSTRAINT ck_cuota_montos;
ALTER TABLE cuota ADD CONSTRAINT ck_cuota_montos CHECK (monto > 0 AND monto_pagado >= 0 AND monto_descuento >= 0
    AND monto_pagado + monto_descuento <= monto);
ALTER TABLE cuota DROP CONSTRAINT ck_cuota_estado_pago;
ALTER TABLE cuota ADD CONSTRAINT ck_cuota_estado_pago CHECK (
    (estado = 'PENDIENTE' AND monto_pagado = 0 AND monto_descuento < monto)
    OR (estado = 'PARCIAL' AND monto_pagado > 0 AND monto_pagado + monto_descuento < monto)
    OR (estado = 'PAGADA' AND monto_pagado > 0 AND monto_pagado + monto_descuento = monto)
    OR (estado = 'EXONERADA' AND monto_pagado = 0 AND monto_descuento = monto)
    OR (estado = 'ANULADA' AND monto_pagado = 0));
