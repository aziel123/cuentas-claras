-- Sprint 4 · tanda 1: pagos en línea con pasarela (orden, cuotas de la orden y avisos de la pasarela), cajas de canal
-- para los pagos que entran solos, cuenta del apoderado y envío de comprobantes al OSE (outbox con reintentos).
-- Nada se borra. orden_pago_cuota es de SOLO INSERCIÓN. Los triggers de scripts/mysql/03-triggers.sql vigilan lo que el
-- GRANT por columna no distingue (estados, confirmación única de la pasarela, pasarela simulada).

-- Guarda de la BASE (no de la aplicación): solo el DBA la escribe; cc_app no tiene INSERT ni UPDATE (solo el SELECT
-- general). En producción NO existe la fila 'pasarela_simulada': trg_orden_pago_nace rechaza toda orden SIMULADA.
-- En la base del piloto (otra base, otro despliegue) el DBA inserta ('pasarela_simulada', 'PERMITIDA').
CREATE TABLE configuracion_bd (
    clave      VARCHAR(40)   NOT NULL PRIMARY KEY,
    valor      VARCHAR(100)  NOT NULL,
    creado_en  DATETIME(6)   NOT NULL
);

-- Los nombres «sistema...» quedan reservados para los actores de sistema (sistema.pasarela, sistema.recaudacion,
-- sistema.conciliacion, sistema.ose). Ninguna persona puede llamarse así y firmar como ellos.
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_nombre_reservado CHECK (nombre_usuario NOT LIKE 'sistema%');

-- Cuenta en línea del apoderado: enlazada a SU registro de apoderado (y por él, a su familia). Una cuenta por apoderado.
ALTER TABLE usuario ADD COLUMN apoderado_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_apoderado UNIQUE (apoderado_id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_apoderado FOREIGN KEY (apoderado_id, colegio_id)
    REFERENCES apoderado (id, colegio_id);

-- Caja de canal: los pagos que entran solos (pasarela; recaudación en la tanda 2) van a una caja por canal y por día,
-- con un actor de sistema como «cajero». Nunca recibe efectivo, nunca se cierra y no tiene fondo fijo (CHECK).
-- Reemplaza ck_caja_diaria_cajero (cajero = creado_por), que sigue valiendo para la ventanilla.
ALTER TABLE caja_diaria ADD COLUMN canal VARCHAR(20) NOT NULL DEFAULT 'VENTANILLA';
ALTER TABLE caja_diaria DROP CONSTRAINT ck_caja_diaria_cajero;
ALTER TABLE caja_diaria ADD CONSTRAINT ck_caja_diaria_canal CHECK (
    (canal = 'VENTANILLA' AND cajero = creado_por AND cajero NOT LIKE 'sistema%')
    OR (canal = 'PASARELA' AND cajero = 'sistema.pasarela' AND fondo_fijo = 0 AND estado = 'ABIERTA'
        AND cierres = 0 AND conteos = 0)
    OR (canal = 'RECAUDACION' AND cajero = 'sistema.recaudacion' AND fondo_fijo = 0 AND estado = 'ABIERTA'
        AND cierres = 0 AND conteos = 0));

-- Orden de pago en línea: la crea el apoderado eligiendo cuotas de SU familia; el monto lo calcula el servidor.
-- referencia: UUID público (URL de retorno y metadato en la pasarela). La confirmación (cargo, operación canónica,
-- monto, moneda y medio) la escribe el sistema UNA vez, con lo que respondió la CONSULTA a la pasarela (nunca con lo
-- que dice el aviso). POR_REVISAR: hubo dinero pero no se pudo aplicar (cuota ya pagada, monto distinto...).
CREATE TABLE orden_pago (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT         NOT NULL,
    referencia            VARCHAR(36)    NOT NULL,
    familia_id            BIGINT         NOT NULL,
    apoderado_id          BIGINT         NOT NULL,
    proveedor             VARCHAR(20)    NOT NULL,
    monto                 DECIMAL(10,2)  NOT NULL,
    moneda                VARCHAR(3)     NOT NULL,
    comprobante_tipo      VARCHAR(20)    NOT NULL,
    clave_idempotencia    VARCHAR(36)    NOT NULL,
    vence_en              DATETIME(6)    NOT NULL,
    estado                VARCHAR(20)    NOT NULL,
    proveedor_orden_id    VARCHAR(80),
    enlace_pago           VARCHAR(500),
    cargo_id              VARCHAR(80),
    operacion             VARCHAR(30),
    monto_confirmado      DECIMAL(10,2),
    moneda_confirmada     VARCHAR(3),
    medio_confirmado      VARCHAR(20),
    confirmado_en         DATETIME(6),
    tardia                BOOLEAN        NOT NULL DEFAULT FALSE,
    motivo_revision       VARCHAR(30),
    detalle_revision      VARCHAR(500),
    devolucion_operacion  VARCHAR(80),
    devuelto_por          VARCHAR(60),
    devuelto_en           DATETIME(6),
    creado_en             DATETIME(6)    NOT NULL,
    creado_por            VARCHAR(60)    NOT NULL,
    actualizado_en        DATETIME(6)    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_orden_pago_referencia UNIQUE (referencia),
    CONSTRAINT uk_orden_pago_idempotencia UNIQUE (colegio_id, clave_idempotencia),
    CONSTRAINT uk_orden_pago_proveedor UNIQUE (colegio_id, proveedor, proveedor_orden_id),
    CONSTRAINT uk_orden_pago_cargo UNIQUE (colegio_id, proveedor, cargo_id),
    CONSTRAINT uk_orden_pago_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_orden_pago_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_orden_pago_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    -- El apoderado que paga es de ESA familia (uk_apoderado_id_familia de V5).
    CONSTRAINT fk_orden_pago_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT ck_orden_pago_proveedor CHECK (proveedor IN ('SIMULADA', 'CULQI', 'IZIPAY', 'NIUBIZ')),
    CONSTRAINT ck_orden_pago_monto CHECK (monto > 0 AND monto <= 99999.99 AND moneda = 'PEN'
        AND (monto_confirmado IS NULL OR monto_confirmado > 0)),
    CONSTRAINT ck_orden_pago_comprobante CHECK (comprobante_tipo IN ('BOLETA', 'FACTURA')),
    CONSTRAINT ck_orden_pago_estado CHECK (estado IN ('CREADA', 'PAGADA', 'POR_REVISAR', 'VENCIDA', 'RECHAZADA',
        'APLICADA', 'DEVUELTA')),
    CONSTRAINT ck_orden_pago_enlace CHECK ((proveedor_orden_id IS NULL AND enlace_pago IS NULL)
        OR (proveedor_orden_id IS NOT NULL AND enlace_pago IS NOT NULL)),
    CONSTRAINT ck_orden_pago_operacion CHECK (operacion IS NULL
        OR REGEXP_LIKE(operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c')),
    CONSTRAINT ck_orden_pago_medio CHECK (medio_confirmado IS NULL OR medio_confirmado IN ('YAPE', 'PLIN', 'TARJETA')),
    -- La confirmación va completa o no va. Con dinero (PAGADA, POR_REVISAR, APLICADA, DEVUELTA) es obligatoria.
    CONSTRAINT ck_orden_pago_confirmacion CHECK (
        (cargo_id IS NULL AND operacion IS NULL AND monto_confirmado IS NULL AND moneda_confirmada IS NULL
            AND medio_confirmado IS NULL AND confirmado_en IS NULL AND estado IN ('CREADA', 'VENCIDA', 'RECHAZADA'))
        OR (cargo_id IS NOT NULL AND operacion IS NOT NULL AND monto_confirmado IS NOT NULL
            AND moneda_confirmada IS NOT NULL AND medio_confirmado IS NOT NULL AND confirmado_en IS NOT NULL
            AND estado <> 'RECHAZADA')),
    CONSTRAINT ck_orden_pago_revision CHECK (
        (estado IN ('POR_REVISAR', 'APLICADA', 'DEVUELTA') AND motivo_revision IS NOT NULL)
        OR (estado NOT IN ('POR_REVISAR', 'APLICADA', 'DEVUELTA') AND motivo_revision IS NULL)),
    CONSTRAINT ck_orden_pago_devolucion CHECK (
        (estado = 'DEVUELTA' AND devolucion_operacion IS NOT NULL AND devuelto_por IS NOT NULL AND devuelto_en IS NOT NULL)
        OR (estado <> 'DEVUELTA' AND devolucion_operacion IS NULL AND devuelto_por IS NULL AND devuelto_en IS NULL))
);
CREATE INDEX ix_orden_pago_estado ON orden_pago (colegio_id, estado, vence_en);
CREATE INDEX ix_orden_pago_familia ON orden_pago (colegio_id, familia_id, estado);

-- Cuotas de la orden con el saldo que tenían al crearla. SOLO INSERCIÓN. Σ monto = orden_pago.monto (trigger).
CREATE TABLE orden_pago_cuota (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    orden_pago_id   BIGINT         NOT NULL,
    cuota_id        BIGINT         NOT NULL,
    monto           DECIMAL(10,2)  NOT NULL,
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_orden_pago_cuota UNIQUE (orden_pago_id, cuota_id),
    CONSTRAINT fk_orden_pago_cuota_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_orden_pago_cuota_orden FOREIGN KEY (orden_pago_id, colegio_id) REFERENCES orden_pago (id, colegio_id),
    CONSTRAINT fk_orden_pago_cuota_cuota FOREIGN KEY (cuota_id, colegio_id) REFERENCES cuota (id, colegio_id),
    CONSTRAINT ck_orden_pago_cuota_monto CHECK (monto > 0)
);
CREATE INDEX ix_orden_pago_cuota_cuota ON orden_pago_cuota (cuota_id, colegio_id);

-- Aviso (webhook) recibido de la pasarela, ya con la firma o la autenticación válida. Bandeja de entrada idempotente:
-- el mismo evento no se procesa dos veces (UNIQUE). No se guarda el cuerpo (puede traer datos del pagador): solo su
-- SHA-256 y lo necesario para procesarlo.
CREATE TABLE evento_pasarela (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    proveedor       VARCHAR(20)    NOT NULL,
    evento_id       VARCHAR(100)   NOT NULL,
    tipo            VARCHAR(60)    NOT NULL,
    orden_pago_id   BIGINT,
    cuerpo_sha256   VARCHAR(64)    NOT NULL,
    estado          VARCHAR(20)    NOT NULL,
    intentos        INT            NOT NULL DEFAULT 0,
    resultado       VARCHAR(250),
    procesado_en    DATETIME(6),
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_evento_pasarela UNIQUE (colegio_id, proveedor, evento_id),
    CONSTRAINT fk_evento_pasarela_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_evento_pasarela_orden FOREIGN KEY (orden_pago_id, colegio_id) REFERENCES orden_pago (id, colegio_id),
    CONSTRAINT ck_evento_pasarela_estado CHECK (estado IN ('RECIBIDO', 'PROCESADO', 'IGNORADO', 'ERROR')
        AND intentos >= 0 AND (estado = 'RECIBIDO' OR procesado_en IS NOT NULL))
);
CREATE INDEX ix_evento_pasarela_estado ON evento_pasarela (colegio_id, estado);

-- Pago en línea: nace de su orden (una por orden), en la caja del canal PASARELA, con el medio que confirmó la pasarela.
ALTER TABLE pago ADD COLUMN orden_pago_id BIGINT;
ALTER TABLE pago ADD CONSTRAINT uk_pago_orden UNIQUE (orden_pago_id);
ALTER TABLE pago ADD CONSTRAINT fk_pago_orden FOREIGN KEY (orden_pago_id, colegio_id) REFERENCES orden_pago (id, colegio_id);
ALTER TABLE pago DROP CONSTRAINT ck_pago_origen;
ALTER TABLE pago ADD CONSTRAINT ck_pago_origen CHECK (
    (origen = 'CAJA' AND reemplaza_pago_id IS NULL AND orden_pago_id IS NULL AND creado_por = cajero
        AND cajero NOT LIKE 'sistema%')
    OR (origen = 'REEMPLAZO' AND reemplaza_pago_id IS NOT NULL AND orden_pago_id IS NULL AND creado_por <> cajero)
    OR (origen = 'PASARELA' AND reemplaza_pago_id IS NULL AND orden_pago_id IS NOT NULL AND creado_por = cajero
        AND cajero = 'sistema.pasarela' AND medio IN ('YAPE', 'PLIN', 'TARJETA')));

-- Comprobante: envío al OSE con outbox. ENVIADO = el OSE lo recibió y falta su respuesta definitiva (se consulta).
-- RECHAZADO: SUNAT/OSE no lo acepta; su número queda usado y se REEMITE con un número nuevo (reemplaza_id).
ALTER TABLE comprobante DROP CONSTRAINT ck_comprobante_envio;
ALTER TABLE comprobante ADD CONSTRAINT ck_comprobante_envio CHECK (estado_envio IN ('PENDIENTE', 'ENVIADO', 'ACEPTADO',
    'OBSERVADO', 'RECHAZADO') AND intentos >= 0);
ALTER TABLE comprobante ADD COLUMN proximo_intento_en DATETIME(6);
ALTER TABLE comprobante ADD COLUMN ultimo_error VARCHAR(250);
ALTER TABLE comprobante ADD COLUMN codigo_respuesta VARCHAR(10);
ALTER TABLE comprobante ADD COLUMN aceptado_en DATETIME(6);
ALTER TABLE comprobante ADD COLUMN reemplaza_id BIGINT;
-- Los ACEPTADO del piloto (simulados) toman como fecha de aceptación la de su envío.
UPDATE comprobante SET aceptado_en = enviado_en WHERE estado_envio IN ('ACEPTADO', 'OBSERVADO');
ALTER TABLE comprobante ADD CONSTRAINT uk_comprobante_reemplaza UNIQUE (colegio_id, reemplaza_id);
ALTER TABLE comprobante ADD CONSTRAINT fk_comprobante_reemplaza FOREIGN KEY (reemplaza_id, colegio_id)
    REFERENCES comprobante (id, colegio_id);
ALTER TABLE comprobante ADD CONSTRAINT ck_comprobante_resultado CHECK (
    estado_envio = 'PENDIENTE'
    OR (estado_envio = 'ENVIADO' AND enviado_en IS NOT NULL)
    OR (estado_envio IN ('ACEPTADO', 'OBSERVADO') AND codigo_hash IS NOT NULL AND respuesta IS NOT NULL
        AND enviado_en IS NOT NULL AND aceptado_en IS NOT NULL)
    OR (estado_envio = 'RECHAZADO' AND respuesta IS NOT NULL AND enviado_en IS NOT NULL));
-- Un comprobante es del proveedor de SU serie: una serie del simulado no emite comprobantes «reales» ni al revés.
ALTER TABLE serie_comprobante ADD CONSTRAINT uk_serie_comprobante_proveedor UNIQUE (id, colegio_id, tipo, serie, proveedor);
ALTER TABLE comprobante ADD CONSTRAINT fk_comprobante_serie_proveedor FOREIGN KEY (serie_id, colegio_id, tipo, serie,
    proveedor) REFERENCES serie_comprobante (id, colegio_id, tipo, serie, proveedor);
CREATE INDEX ix_comprobante_outbox ON comprobante (estado_envio, proximo_intento_en);
