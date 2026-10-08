-- Correcciones del sprint 5 tras la auditoría antifraude y QA (docs/arquitectura/sprint-5-correcciones.md).
-- V17, V18 y V19 no se editan.

-- S5-A1: cada contacto nuevo o cambiado se VERIFICA con un enlace de un solo uso enviado a ese contacto antes de recibir
-- avisos o enlaces. El apoderado guarda el último celular y el último correo verificados: un contacto está verificado
-- solo si es IGUAL al registrado (cambiarlo lo deja pendiente otra vez). Los contactos anteriores a V20 quedan
-- verificados (ya recibían avisos); lo nuevo, no.
-- S5-M1: la aprobación de un contacto que también es del personal vale solo para ESE contacto (y ese canal).
ALTER TABLE apoderado ADD COLUMN telefono_verificado VARCHAR(16);
ALTER TABLE apoderado ADD COLUMN correo_verificado VARCHAR(150);
ALTER TABLE apoderado ADD COLUMN contacto_aprobado_telefono VARCHAR(16);
ALTER TABLE apoderado ADD COLUMN contacto_aprobado_correo VARCHAR(150);
UPDATE apoderado SET telefono_verificado = telefono_whatsapp, correo_verificado = correo;

-- El enlace de verificación nace con su mensaje VERIFICACION_CONTACTO (lo genera el proceso de envío; solo se guarda el
-- SHA-256 del token). Se usa una vez, con el número de documento del apoderado.
CREATE TABLE verificacion_contacto (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    apoderado_id    BIGINT        NOT NULL,
    canal           VARCHAR(10)   NOT NULL,
    contacto        VARCHAR(150)  NOT NULL,
    hash_token      VARCHAR(64)   NOT NULL,
    mensaje_id      BIGINT        NOT NULL,
    vence_en        DATETIME(6)   NOT NULL,
    verificado_en   DATETIME(6),
    verificado_ip   VARCHAR(45),
    anulado_en      DATETIME(6),
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_verificacion_contacto_hash UNIQUE (hash_token),
    CONSTRAINT uk_verificacion_contacto_mensaje UNIQUE (mensaje_id),
    CONSTRAINT fk_verificacion_contacto_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_verificacion_contacto_apoderado FOREIGN KEY (apoderado_id, colegio_id)
        REFERENCES apoderado (id, colegio_id),
    CONSTRAINT fk_verificacion_contacto_mensaje FOREIGN KEY (mensaje_id, colegio_id) REFERENCES mensaje (id, colegio_id),
    CONSTRAINT ck_verificacion_contacto_canal CHECK (canal IN ('WHATSAPP', 'CORREO')),
    CONSTRAINT ck_verificacion_contacto_uso CHECK (NOT (verificado_en IS NOT NULL AND anulado_en IS NOT NULL)
        AND (verificado_en IS NULL OR verificado_ip IS NOT NULL)),
    CONSTRAINT ck_verificacion_contacto_actor CHECK (creado_por = 'sistema.mensajeria')
);
CREATE INDEX ix_verificacion_contacto_apoderado ON verificacion_contacto (colegio_id, apoderado_id);

-- Tipos de mensaje nuevos: la verificación del contacto, el aviso a los demás apoderados (S5-A1) y el aviso a
-- Promotoría de un día no laborable propuesto (S5-M3). El token de verificación tampoco va en los parámetros.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO'));
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_sin_token;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_sin_token CHECK (parametros NOT LIKE '%/activar/%'
    AND parametros NOT LIKE '%/verificar/%');

-- S5-M3: un día no laborable extra lo propone una persona y lo aprueba OTRA (Promotoría o Dirección). Mientras está
-- pendiente no cuenta en el calendario. Los anteriores a V20 quedan como aprobados (pendiente = FALSE).
ALTER TABLE feriado ADD COLUMN pendiente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE feriado ADD COLUMN aprobado_por VARCHAR(60);
ALTER TABLE feriado ADD COLUMN aprobado_en DATETIME(6);
ALTER TABLE feriado ADD CONSTRAINT ck_feriado_aprobacion CHECK (
    (pendiente = TRUE AND aprobado_por IS NULL AND aprobado_en IS NULL)
    OR (pendiente = FALSE AND aprobado_por IS NULL AND aprobado_en IS NULL)
    OR (pendiente = FALSE AND aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL AND aprobado_por <> creado_por
        AND aprobado_por NOT LIKE 'sistema%'));

-- S5-M4: huellas por hora en horario de caja (lunes a sábado, de 08:00 a 19:00): un recorte del mismo día deja de
-- coincidir con la huella de la hora. SOLO INSERCIÓN; la secuencia nunca retrocede (trigger).
CREATE TABLE huella_hora (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    momento         DATETIME(6)   NOT NULL,
    secuencia       BIGINT        NOT NULL,
    codigo          VARCHAR(16)   NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_huella_hora_momento UNIQUE (colegio_id, momento),
    CONSTRAINT fk_huella_hora_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_huella_hora_codigo CHECK (REGEXP_LIKE(codigo, '^[0-9a-f]{16}$', 'c') AND secuencia >= 1),
    CONSTRAINT ck_huella_hora_actor CHECK (creado_por = 'sistema.auditoria')
);
CREATE INDEX ix_huella_hora_secuencia ON huella_hora (colegio_id, secuencia);
