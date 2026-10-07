-- Sprint 5 · tanda 1: mensajes a las familias y al personal (WhatsApp con correo de respaldo) en un outbox que se llena
-- en la MISMA transacción del pago, la anulación o el descuento; enlace de activación enviado directo al titular; huella
-- diaria de la bitácora. Nada se borra. huella_bitacora es de SOLO INSERCIÓN. Los triggers de scripts/mysql/03-triggers.sql
-- vigilan el destino (siempre el contacto registrado), los estados y la mensajería simulada.

-- Celular del personal: recibe su enlace de activación (y Promotoría, la huella diaria). Obligatorio desde este sprint
-- para crear o restablecer el acceso (validación; las cuentas existentes lo completan en su próximo ingreso).
ALTER TABLE usuario ADD COLUMN telefono_whatsapp VARCHAR(16);
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_telefono CHECK (telefono_whatsapp IS NULL
    OR REGEXP_LIKE(telefono_whatsapp, '^[+]?[0-9]{9,15}$'));
-- Desviación del diseño: FK compuesta (usuario_id, colegio_id) para que un mensaje no apunte a un usuario de otro colegio.
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_id_colegio UNIQUE (id, colegio_id);

-- El celular y el correo del apoderado solo cambian con SU solicitud CAMBIO_CONTACTO_APODERADO aprobada (trigger), una
-- vez por solicitud. Mismo patrón que facturacion_solicitud_id (B2 del sprint 3).
ALTER TABLE apoderado ADD COLUMN contacto_solicitud_id BIGINT;
ALTER TABLE apoderado ADD CONSTRAINT uk_apoderado_contacto_solicitud UNIQUE (colegio_id, contacto_solicitud_id);
ALTER TABLE apoderado ADD CONSTRAINT fk_apoderado_contacto_solicitud FOREIGN KEY (contacto_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);

-- Mensaje (outbox). destinatario: un apoderado (con su familia, para el historial del portal), un usuario del personal
-- o EXTERNO (solo el correo que el DBA dejó en configuracion_bd). destino: copia del contacto registrado al crearlo; no
-- cambia. parametros: los valores de la plantilla, armados por el sistema; nunca el token de activación ni el DNI.
CREATE TABLE mensaje (
    id                    BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT         NOT NULL,
    clave                 VARCHAR(120)   NOT NULL,
    tipo                  VARCHAR(30)    NOT NULL,
    canal                 VARCHAR(10)    NOT NULL,
    destinatario_tipo     VARCHAR(10)    NOT NULL,
    apoderado_id          BIGINT,
    familia_id            BIGINT,
    usuario_id            BIGINT,
    destino               VARCHAR(150)   NOT NULL,
    plantilla             VARCHAR(60)    NOT NULL,
    parametros            VARCHAR(1000)  NOT NULL,
    entidad               VARCHAR(40),
    entidad_id            BIGINT,
    respaldo_de_id        BIGINT,
    estado                VARCHAR(20)    NOT NULL,
    proveedor             VARCHAR(20),
    proveedor_mensaje_id  VARCHAR(120),
    intentos              INT            NOT NULL DEFAULT 0,
    proximo_intento_en    DATETIME(6),
    enviado_en            DATETIME(6),
    entregado_en          DATETIME(6),
    leido_en              DATETIME(6),
    ultimo_error          VARCHAR(250),
    creado_en             DATETIME(6)    NOT NULL,
    creado_por            VARCHAR(60)    NOT NULL,
    actualizado_en        DATETIME(6)    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_mensaje_clave UNIQUE (colegio_id, clave),
    CONSTRAINT uk_mensaje_proveedor UNIQUE (proveedor, proveedor_mensaje_id),
    CONSTRAINT uk_mensaje_respaldo UNIQUE (colegio_id, respaldo_de_id),
    CONSTRAINT uk_mensaje_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_mensaje_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_mensaje_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    -- El apoderado es de ESA familia (uk_apoderado_id_familia de V5).
    CONSTRAINT fk_mensaje_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT fk_mensaje_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_mensaje_respaldo FOREIGN KEY (respaldo_de_id, colegio_id) REFERENCES mensaje (id, colegio_id),
    CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO', 'DESCUENTO_APROBADO',
        'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO', 'CUOTA_VENCIDA',
        'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO')),
    CONSTRAINT ck_mensaje_canal CHECK (canal IN ('WHATSAPP', 'CORREO')),
    CONSTRAINT ck_mensaje_destinatario CHECK (
        (destinatario_tipo = 'APODERADO' AND apoderado_id IS NOT NULL AND familia_id IS NOT NULL AND usuario_id IS NULL)
        OR (destinatario_tipo = 'USUARIO' AND usuario_id IS NOT NULL AND apoderado_id IS NULL AND familia_id IS NULL)
        OR (destinatario_tipo = 'EXTERNO' AND usuario_id IS NULL AND apoderado_id IS NULL AND familia_id IS NULL
            AND tipo = 'HUELLA_BITACORA' AND canal = 'CORREO')),
    CONSTRAINT ck_mensaje_destino CHECK (
        (canal = 'WHATSAPP' AND REGEXP_LIKE(destino, '^[+]?[0-9]{9,15}$'))
        OR (canal = 'CORREO' AND destino LIKE '%_@_%._%')),
    CONSTRAINT ck_mensaje_sin_token CHECK (parametros NOT LIKE '%/activar/%'),
    CONSTRAINT ck_mensaje_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'ENTREGADO', 'LEIDO', 'FALLIDO')
        AND intentos >= 0),
    CONSTRAINT ck_mensaje_proveedor CHECK (proveedor IS NULL
        OR proveedor IN ('SIMULADO', 'WHATSAPP_CLOUD', 'SMTP')),
    CONSTRAINT ck_mensaje_envio CHECK (
        (estado = 'PENDIENTE' AND proveedor_mensaje_id IS NULL AND enviado_en IS NULL)
        OR (estado IN ('ENVIADO', 'ENTREGADO', 'LEIDO') AND proveedor IS NOT NULL AND proveedor_mensaje_id IS NOT NULL
            AND enviado_en IS NOT NULL)
        OR (estado = 'FALLIDO' AND ultimo_error IS NOT NULL)),
    CONSTRAINT ck_mensaje_respaldo CHECK (respaldo_de_id IS NULL OR canal = 'CORREO')
);
CREATE INDEX ix_mensaje_outbox ON mensaje (estado, proximo_intento_en);
CREATE INDEX ix_mensaje_familia ON mensaje (colegio_id, familia_id, creado_en);
CREATE INDEX ix_mensaje_entidad ON mensaje (colegio_id, entidad, entidad_id);

-- S4-M2 y A2: el enlace nace CON su mensaje al titular (trigger) y lo genera el proceso de envío; nadie lo ve en pantalla.
-- PERSONAL: alta o restablecimiento de una cuenta del personal (reemplaza la clave temporal visible).
ALTER TABLE enlace_activacion ADD COLUMN mensaje_id BIGINT;
ALTER TABLE enlace_activacion ADD COLUMN proposito VARCHAR(20) NOT NULL DEFAULT 'APODERADO';
ALTER TABLE enlace_activacion ADD CONSTRAINT uk_enlace_activacion_mensaje UNIQUE (mensaje_id);
ALTER TABLE enlace_activacion ADD CONSTRAINT fk_enlace_activacion_mensaje FOREIGN KEY (mensaje_id, colegio_id)
    REFERENCES mensaje (id, colegio_id);
ALTER TABLE enlace_activacion ADD CONSTRAINT ck_enlace_activacion_proposito CHECK (proposito IN ('APODERADO', 'PERSONAL'));

-- Huella diaria de la bitácora (último evento del colegio hasta las 23:59:59 del día). SOLO INSERCIÓN. Debe coincidir con
-- la bitácora (trigger). Lo que vale es la copia que llega al celular de Promotoría: esta tabla es el registro de envío
-- y la base de la reverificación diaria.
CREATE TABLE huella_bitacora (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    fecha            DATE          NOT NULL,
    secuencia        BIGINT        NOT NULL,
    codigo           VARCHAR(16)   NOT NULL,
    eventos_del_dia  INT           NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_huella_bitacora_fecha UNIQUE (colegio_id, fecha),
    CONSTRAINT fk_huella_bitacora_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_huella_bitacora_codigo CHECK (REGEXP_LIKE(codigo, '^[0-9a-f]{16}$', 'c') AND secuencia >= 1
        AND eventos_del_dia >= 0),
    CONSTRAINT ck_huella_bitacora_actor CHECK (creado_por = 'sistema.auditoria')
);
