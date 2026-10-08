-- Sprint 4 · tanda 2: recaudación bancaria por código de alumno. El archivo del banco se guarda tal cual (evidencia),
-- se registra en un lote CARGADO por Administración y lo confirma OTRA persona escribiendo a ciegas el total que ve en
-- el banco. Recién entonces el sistema (sistema.recaudacion) registra los pagos. archivo_cargado: SOLO INSERCIÓN.

-- Archivo original subido (recaudación, extracto o liquidación de la pasarela), con su SHA-256. Hasta 2 MB.
CREATE TABLE archivo_cargado (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    tipo            VARCHAR(20)    NOT NULL,
    nombre          VARCHAR(150)   NOT NULL,
    sha256          VARCHAR(64)    NOT NULL,
    bytes           INT            NOT NULL,
    contenido       LONGBLOB       NOT NULL,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_archivo_cargado_sha UNIQUE (colegio_id, tipo, sha256),
    CONSTRAINT uk_archivo_cargado_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_archivo_cargado_id_sha UNIQUE (id, colegio_id, sha256),
    CONSTRAINT fk_archivo_cargado_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_archivo_cargado_tipo CHECK (tipo IN ('RECAUDACION', 'EXTRACTO', 'LIQUIDACION')),
    CONSTRAINT ck_archivo_cargado_tamano CHECK (bytes > 0 AND bytes <= 2097152 AND CHAR_LENGTH(sha256) = 64)
);

-- Lote: un archivo de recaudación del banco. sha_vigente impide cargar dos veces el mismo archivo mientras el lote esté
-- vigente (NULL al rechazarlo o descartarlo). total_banco: el total que declara el propio archivo (pie), si lo trae.
-- Confirmar exige otra persona y el total escrito a ciegas igual al del archivo (CHECK).
CREATE TABLE lote_recaudacion (
    id                     BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id             BIGINT         NOT NULL,
    archivo_id             BIGINT         NOT NULL,
    archivo_sha256         VARCHAR(64)    NOT NULL,
    sha_vigente            VARCHAR(64),
    banco                  VARCHAR(20)    NOT NULL,
    formato                VARCHAR(30)    NOT NULL,
    fecha_proceso          DATE           NOT NULL,
    desde                  DATE           NOT NULL,
    hasta                  DATE           NOT NULL,
    lineas                 INT            NOT NULL,
    total                  DECIMAL(12,2)  NOT NULL,
    total_banco            DECIMAL(12,2),
    estado                 VARCHAR(20)    NOT NULL,
    intentos_confirmacion  INT            NOT NULL DEFAULT 0,
    total_ciego            DECIMAL(12,2),
    confirmado_por         VARCHAR(60),
    confirmado_en          DATETIME(6),
    aplicado_en            DATETIME(6),
    lineas_aplicadas       INT            NOT NULL DEFAULT 0,
    lineas_excepcion       INT            NOT NULL DEFAULT 0,
    monto_aplicado         DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    monto_excepcion        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    rechazado_por          VARCHAR(60),
    rechazado_en           DATETIME(6),
    motivo_rechazo         VARCHAR(500),
    creado_en              DATETIME(6)    NOT NULL,
    creado_por             VARCHAR(60)    NOT NULL,
    actualizado_en         DATETIME(6)    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_lote_recaudacion_vigente UNIQUE (colegio_id, sha_vigente),
    CONSTRAINT uk_lote_recaudacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_lote_recaudacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_lote_recaudacion_archivo FOREIGN KEY (archivo_id, colegio_id, archivo_sha256)
        REFERENCES archivo_cargado (id, colegio_id, sha256),
    CONSTRAINT ck_lote_recaudacion_banco CHECK (banco IN ('GENERICO', 'BCP', 'INTERBANK', 'BBVA', 'SCOTIABANK')),
    CONSTRAINT ck_lote_recaudacion_estado CHECK (estado IN ('CARGADO', 'CONFIRMADO', 'APLICADO', 'RECHAZADO', 'DESCARTADO')),
    CONSTRAINT ck_lote_recaudacion_montos CHECK (lineas > 0 AND total > 0 AND desde <= hasta
        AND (total_banco IS NULL OR total_banco = total) AND intentos_confirmacion >= 0
        AND lineas_aplicadas >= 0 AND lineas_excepcion >= 0 AND monto_aplicado >= 0 AND monto_excepcion >= 0),
    CONSTRAINT ck_lote_recaudacion_vigente CHECK (
        (estado IN ('CARGADO', 'CONFIRMADO', 'APLICADO') AND sha_vigente IS NOT NULL AND sha_vigente = archivo_sha256)
        OR (estado IN ('RECHAZADO', 'DESCARTADO') AND sha_vigente IS NULL)),
    CONSTRAINT ck_lote_recaudacion_confirmacion CHECK (
        (estado IN ('CONFIRMADO', 'APLICADO') AND confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL
            AND confirmado_por <> creado_por AND total_ciego IS NOT NULL AND total_ciego = total)
        OR (estado IN ('CARGADO', 'RECHAZADO', 'DESCARTADO') AND confirmado_por IS NULL AND confirmado_en IS NULL
            AND total_ciego IS NULL)),
    CONSTRAINT ck_lote_recaudacion_aplicado CHECK (
        (estado = 'APLICADO' AND aplicado_en IS NOT NULL AND lineas_aplicadas + lineas_excepcion = lineas
            AND monto_aplicado + monto_excepcion = total)
        OR (estado <> 'APLICADO' AND aplicado_en IS NULL)),
    CONSTRAINT ck_lote_recaudacion_rechazo CHECK (
        (estado IN ('RECHAZADO', 'DESCARTADO') AND rechazado_por IS NOT NULL AND rechazado_en IS NOT NULL
            AND motivo_rechazo IS NOT NULL)
        OR (estado NOT IN ('RECHAZADO', 'DESCARTADO') AND rechazado_por IS NULL AND rechazado_en IS NULL))
);
CREATE INDEX ix_lote_recaudacion_estado ON lote_recaudacion (colegio_id, estado);

-- Línea del archivo: un pago hecho en el banco con el código del alumno (y, si el banco trabaja con base de deudas, la
-- cuota exacta). Se inserta solo mientras el lote está CARGADO; luego solo cambia su estado al aplicarse.
CREATE TABLE linea_recaudacion (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT         NOT NULL,
    lote_id               BIGINT         NOT NULL,
    numero                INT            NOT NULL,
    fecha_pago            DATE           NOT NULL,
    codigo                VARCHAR(20)    NOT NULL,
    alumno_id             BIGINT,
    cuota_id              BIGINT,
    monto                 DECIMAL(10,2)  NOT NULL,
    moneda                VARCHAR(3)     NOT NULL,
    numero_operacion      VARCHAR(30)    NOT NULL,
    estado                VARCHAR(20)    NOT NULL,
    motivo_excepcion      VARCHAR(30),
    detalle               VARCHAR(250),
    devolucion_operacion  VARCHAR(30),
    devuelto_por          VARCHAR(60),
    devuelto_en           DATETIME(6),
    creado_en             DATETIME(6)    NOT NULL,
    creado_por            VARCHAR(60)    NOT NULL,
    actualizado_en        DATETIME(6)    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_linea_recaudacion_numero UNIQUE (lote_id, numero),
    CONSTRAINT uk_linea_recaudacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_linea_recaudacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_linea_recaudacion_lote FOREIGN KEY (lote_id, colegio_id) REFERENCES lote_recaudacion (id, colegio_id),
    CONSTRAINT fk_linea_recaudacion_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_linea_recaudacion_cuota FOREIGN KEY (cuota_id, colegio_id) REFERENCES cuota (id, colegio_id),
    CONSTRAINT ck_linea_recaudacion_monto CHECK (monto > 0 AND numero >= 1 AND moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_linea_recaudacion_operacion CHECK (REGEXP_LIKE(numero_operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c')),
    CONSTRAINT ck_linea_recaudacion_estado CHECK (
        (estado IN ('PENDIENTE', 'APLICADA') AND motivo_excepcion IS NULL)
        OR (estado IN ('EXCEPCION', 'APLICADA_REVISION', 'DEVUELTA') AND motivo_excepcion IS NOT NULL)),
    CONSTRAINT ck_linea_recaudacion_devolucion CHECK (
        (estado = 'DEVUELTA' AND devolucion_operacion IS NOT NULL AND devuelto_por IS NOT NULL AND devuelto_en IS NOT NULL)
        OR (estado <> 'DEVUELTA' AND devolucion_operacion IS NULL AND devuelto_por IS NULL AND devuelto_en IS NULL))
);
CREATE INDEX ix_linea_recaudacion_estado ON linea_recaudacion (colegio_id, estado);

-- Pago por recaudación: nace de SU línea (una por línea), en la caja del canal RECAUDACION de la fecha de pago.
ALTER TABLE pago ADD COLUMN linea_recaudacion_id BIGINT;
ALTER TABLE pago ADD CONSTRAINT uk_pago_linea_recaudacion UNIQUE (linea_recaudacion_id);
ALTER TABLE pago ADD CONSTRAINT fk_pago_linea_recaudacion FOREIGN KEY (linea_recaudacion_id, colegio_id)
    REFERENCES linea_recaudacion (id, colegio_id);
ALTER TABLE pago DROP CONSTRAINT ck_pago_medio;
ALTER TABLE pago ADD CONSTRAINT ck_pago_medio CHECK (medio IN ('EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA',
    'RECAUDACION_BANCARIA'));
ALTER TABLE pago DROP CONSTRAINT ck_pago_origen;
ALTER TABLE pago ADD CONSTRAINT ck_pago_origen CHECK (
    (origen = 'CAJA' AND reemplaza_pago_id IS NULL AND orden_pago_id IS NULL AND linea_recaudacion_id IS NULL
        AND creado_por = cajero AND cajero NOT LIKE 'sistema%' AND medio <> 'RECAUDACION_BANCARIA')
    OR (origen = 'REEMPLAZO' AND reemplaza_pago_id IS NOT NULL AND orden_pago_id IS NULL AND linea_recaudacion_id IS NULL
        AND creado_por <> cajero)
    OR (origen = 'PASARELA' AND reemplaza_pago_id IS NULL AND orden_pago_id IS NOT NULL AND linea_recaudacion_id IS NULL
        AND creado_por = cajero AND cajero = 'sistema.pasarela' AND medio IN ('YAPE', 'PLIN', 'TARJETA'))
    OR (origen = 'RECAUDACION' AND reemplaza_pago_id IS NULL AND orden_pago_id IS NULL
        AND linea_recaudacion_id IS NOT NULL AND creado_por = cajero AND cajero = 'sistema.recaudacion'
        AND medio = 'RECAUDACION_BANCARIA'));
-- El reembolso de una devolución usa el medio del pago (trigger): ahora también la recaudación bancaria.
ALTER TABLE reembolso DROP CONSTRAINT ck_reembolso_medio;
ALTER TABLE reembolso ADD CONSTRAINT ck_reembolso_medio CHECK ((medio = 'EFECTIVO' AND numero_operacion IS NULL
        AND recibido_por_nombre IS NOT NULL AND recibido_por_documento IS NOT NULL)
    OR (medio IN ('YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA', 'RECAUDACION_BANCARIA') AND numero_operacion IS NOT NULL));
