-- Correcciones del sprint 8 (docs/arquitectura/sprint-8-correcciones.md): auditoría S8-A1, S8-A2, S8-M5 y S8-B4, y QA
-- QA-S8-5. Compatible con MySQL 8 y con H2 en modo MySQL. Nada se borra.

-- S8-M5. Quién cambió la ficha del colegio por última vez: trg_colegio_cambio exige su firma (colegio:{id}:FICHA:{version}).
-- El operador, solo en preparación y mientras el titular no active su cuenta; después, la Promotoría del colegio.
ALTER TABLE colegio ADD COLUMN actualizado_por VARCHAR(60);

-- S8-A1. Cambiar el contacto del titular antes de que active su cuenta lo PROPONE un operador y lo APLICA OTRO operador,
-- cada uno con la firma de su sesión. Al aplicarlo se avisa al contacto anterior, al correo institucional y al correo
-- externo de control en la misma transacción (si no hay a quién avisar, no se guarda). Lo escribe solo cc_sistema.
CREATE TABLE cambio_contacto_titular (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT        NOT NULL,
    usuario_id         BIGINT        NOT NULL,   -- el titular (la cuenta de la primera Promotoría)
    telefono_anterior  VARCHAR(16),
    correo_anterior    VARCHAR(150),
    telefono_nuevo     VARCHAR(16),
    correo_nuevo       VARCHAR(150),
    motivo             VARCHAR(500)  NOT NULL,
    estado             VARCHAR(12)   NOT NULL,
    pendiente          BOOLEAN,                  -- TRUE solo el PROPUESTO; si no, NULL (uno pendiente por titular)
    propuesto_por      VARCHAR(60)   NOT NULL,   -- el operador que lo propone
    operador_id        BIGINT        NOT NULL,
    plataforma_id      BIGINT        NOT NULL,
    resuelto_por       VARCHAR(60),              -- el OTRO operador que lo aplica (o quien lo rechaza)
    resuelto_en        DATETIME(6),
    creado_en          DATETIME(6)   NOT NULL,
    creado_por         VARCHAR(60)   NOT NULL,
    actualizado_en     DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_cambio_contacto_titular_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_cambio_contacto_titular_pendiente UNIQUE (usuario_id, pendiente),
    CONSTRAINT fk_cambio_contacto_titular_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cambio_contacto_titular_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_cambio_contacto_titular_operador FOREIGN KEY (operador_id, plataforma_id)
        REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_cambio_contacto_titular_estado CHECK (estado IN ('PROPUESTO', 'APLICADO', 'RECHAZADO')
        AND ((estado = 'PROPUESTO' AND pendiente AND resuelto_por IS NULL AND resuelto_en IS NULL)
            OR (estado <> 'PROPUESTO' AND pendiente IS NULL AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL))),
    -- Quien aplica no es quien propone (dos operadores).
    CONSTRAINT ck_cambio_contacto_titular_otro CHECK (estado <> 'APLICADO'
        OR (resuelto_por IS NOT NULL AND resuelto_por <> propuesto_por)),
    CONSTRAINT ck_cambio_contacto_titular_nuevo CHECK (telefono_nuevo IS NOT NULL OR correo_nuevo IS NOT NULL),
    CONSTRAINT ck_cambio_contacto_titular_actor CHECK (creado_por = propuesto_por AND propuesto_por NOT LIKE 'sistema%'),
    CONSTRAINT ck_cambio_contacto_titular_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) >= 10)
);

-- S8-A2. En un colegio EN PRODUCCIÓN el correo externo de control (el testigo independiente que recibe la huella y el
-- resumen diario con montos) lo PROPONE el operador y lo APRUEBA una Promotoría activa del colegio con su firma. Al
-- proponerlo se avisa al correo anterior y a toda la Promotoría, antes de que surta efecto. Lo escribe solo cc_sistema.
CREATE TABLE cambio_correo_control (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT        NOT NULL,
    correo_anterior    VARCHAR(100),
    correo_nuevo       VARCHAR(100)  NOT NULL,
    motivo             VARCHAR(500)  NOT NULL,
    estado             VARCHAR(12)   NOT NULL,
    pendiente          BOOLEAN,                  -- TRUE solo el PROPUESTO; si no, NULL (uno pendiente por colegio)
    propuesto_por      VARCHAR(60)   NOT NULL,   -- el operador
    operador_id        BIGINT        NOT NULL,
    plataforma_id      BIGINT        NOT NULL,
    resuelto_por       VARCHAR(60),              -- la Promotoría que aprueba o rechaza
    resuelto_en        DATETIME(6),
    creado_en          DATETIME(6)   NOT NULL,
    creado_por         VARCHAR(60)   NOT NULL,
    actualizado_en     DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_cambio_correo_control_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_cambio_correo_control_pendiente UNIQUE (colegio_id, pendiente),
    CONSTRAINT fk_cambio_correo_control_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cambio_correo_control_operador FOREIGN KEY (operador_id, plataforma_id)
        REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_cambio_correo_control_estado CHECK (estado IN ('PROPUESTO', 'APROBADO', 'RECHAZADO')
        AND ((estado = 'PROPUESTO' AND pendiente AND resuelto_por IS NULL AND resuelto_en IS NULL)
            OR (estado <> 'PROPUESTO' AND pendiente IS NULL AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL))),
    CONSTRAINT ck_cambio_correo_control_otro CHECK (resuelto_por IS NULL OR resuelto_por <> propuesto_por),
    CONSTRAINT ck_cambio_correo_control_correo CHECK (correo_nuevo LIKE '%@%'
        AND (correo_anterior IS NULL OR correo_anterior <> correo_nuevo)),
    CONSTRAINT ck_cambio_correo_control_actor CHECK (creado_por = propuesto_por AND propuesto_por NOT LIKE 'sistema%'),
    CONSTRAINT ck_cambio_correo_control_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) >= 10)
);

-- Avisos nuevos (S8-A1, S8-A2 y S8-M5): AVISO_ALTA (enlace del titular reenviado, su contacto corregido, la ficha corregida
-- por el operador) y CORREO_CONTROL (cambio del correo externo de control propuesto o aplicado). Los dos van también al
-- correo institucional del colegio y al correo externo de control (EXTERNO; trg_mensaje_nace exige que sean esos).
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL'));
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_destinatario;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_destinatario CHECK (
    (destinatario_tipo = 'APODERADO' AND apoderado_id IS NOT NULL AND familia_id IS NOT NULL AND usuario_id IS NULL)
    OR (destinatario_tipo = 'USUARIO' AND usuario_id IS NOT NULL AND apoderado_id IS NULL AND familia_id IS NULL)
    OR (destinatario_tipo = 'EXTERNO' AND usuario_id IS NULL AND apoderado_id IS NULL AND familia_id IS NULL
        AND tipo IN ('HUELLA_BITACORA', 'RESUMEN_DIARIO', 'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA',
            'CORREO_CONTROL') AND canal = 'CORREO'));

-- S8-B4 y QA-S8-5. Cada comprobante guarda la foto de su emisor al emitirse (nombre, razón social, RUC, dirección y el logo
-- vigente): la reimpresión muestra ESE emisor aunque después cambie la marca del colegio. No cambian (sin GRANT de UPDATE).
ALTER TABLE colegio_logo ADD CONSTRAINT uk_colegio_logo_id_colegio UNIQUE (id, colegio_id);
ALTER TABLE comprobante ADD COLUMN emisor_nombre VARCHAR(150);
ALTER TABLE comprobante ADD COLUMN emisor_razon_social VARCHAR(150);
ALTER TABLE comprobante ADD COLUMN emisor_ruc VARCHAR(11);
ALTER TABLE comprobante ADD COLUMN emisor_direccion VARCHAR(200);
ALTER TABLE comprobante ADD COLUMN emisor_logo_id BIGINT;
ALTER TABLE comprobante ADD CONSTRAINT fk_comprobante_emisor_logo FOREIGN KEY (emisor_logo_id, colegio_id)
    REFERENCES colegio_logo (id, colegio_id);
-- Los comprobantes anteriores toman la ficha y el logo vigentes al migrar (la mejor foto que existe de su emisor).
UPDATE comprobante SET
    emisor_nombre = (SELECT c.nombre FROM colegio c WHERE c.id = comprobante.colegio_id),
    emisor_razon_social = (SELECT c.razon_social FROM colegio c WHERE c.id = comprobante.colegio_id),
    emisor_ruc = (SELECT c.ruc FROM colegio c WHERE c.id = comprobante.colegio_id),
    emisor_direccion = (SELECT c.direccion FROM colegio c WHERE c.id = comprobante.colegio_id),
    emisor_logo_id = (SELECT l.id FROM colegio_logo l WHERE l.colegio_id = comprobante.colegio_id AND l.vigente);
