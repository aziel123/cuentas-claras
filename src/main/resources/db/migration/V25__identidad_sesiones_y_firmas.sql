-- Sprint 7 · tanda 2 (docs/arquitectura/sprint-7-endurecimiento.md, sección 5). Sesiones de las personas (las abre y
-- las cierra SOLO cc_sistema), firma de cada aprobación (en MySQL, trg_firma_operacion_nace valida el secreto de la
-- sesión, lo deja en NULL y pone la hora de la base), solicitud de roles enlazada en el usuario, hora de la base en la
-- llamada de control y correo externo de la huella por colegio. Nada se borra.

-- Una sesión por ingreso. Guarda SOLO el SHA-256 del secreto (el secreto vive en la memoria del servidor, en la sesión
-- HTTP). Se cierra una sola vez y con motivo.
CREATE TABLE sesion_usuario (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    hash_token      VARCHAR(64)   NOT NULL,
    ip              VARCHAR(45),
    abierta_en      DATETIME(6)   NOT NULL,
    vence_en        DATETIME(6)   NOT NULL,
    cerrada_en      DATETIME(6),
    motivo_cierre   VARCHAR(20),
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_sesion_usuario_token UNIQUE (hash_token),
    CONSTRAINT uk_sesion_usuario_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_sesion_usuario_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_sesion_usuario_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_sesion_usuario_token CHECK (REGEXP_LIKE(hash_token, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_sesion_usuario_vigencia CHECK (vence_en > abierta_en),
    CONSTRAINT ck_sesion_usuario_cierre CHECK ((cerrada_en IS NULL AND motivo_cierre IS NULL)
        OR (cerrada_en IS NOT NULL AND motivo_cierre IS NOT NULL
            AND motivo_cierre IN ('SALIO', 'VENCIO', 'OTRA_SESION', 'CUENTA_CAMBIADA', 'REINICIO')))
);
CREATE INDEX ix_sesion_usuario_abiertas ON sesion_usuario (colegio_id, usuario_id, cerrada_en);

-- La firma de una aprobación: la clave canónica de la operación (seguridad.service.sesion.ClaveFirma) y el secreto de
-- la sesión de quien resuelve. Una firma por colegio y clave (no se reusa). Solo inserción.
CREATE TABLE firma_operacion (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    sesion_id       BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    clave           VARCHAR(120)  NOT NULL,
    token           VARCHAR(64),
    firmada_bd      DATETIME(6),
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_firma_operacion UNIQUE (colegio_id, clave),
    CONSTRAINT fk_firma_operacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_firma_operacion_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT fk_firma_operacion_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_firma_operacion_clave CHECK (REGEXP_LIKE(clave, '^[a-z_]+:[A-Za-z0-9:_-]+$', 'c'))
);
CREATE INDEX ix_firma_operacion_usuario ON firma_operacion (colegio_id, usuario_id, firmada_bd);

-- H1 y H2: PROMOTOR y DIRECTOR solo con SU solicitud CAMBIO_ROLES aprobada, usada una vez (como contacto_solicitud_id).
ALTER TABLE usuario ADD COLUMN roles_solicitud_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_roles_solicitud UNIQUE (colegio_id, roles_solicitud_id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_roles_solicitud FOREIGN KEY (roles_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);

-- Residual del sprint 6: «una hora después» con la hora de la base (la pone trg_llamada_control_registro), no con
-- creado_en (lo escribe la aplicación).
ALTER TABLE llamada_control ADD COLUMN registrada_bd DATETIME(6);

-- Residual del sprint 6 (H5): la huella al correo externo de SU colegio. Se copia la fila vieja de configuracion_bd solo
-- si hay un único colegio; con varios, el DBA escribe una por cada uno (docs/operacion/mysql-usuarios.md).
ALTER TABLE configuracion_colegio DROP CONSTRAINT ck_configuracion_colegio_clave;
ALTER TABLE configuracion_colegio ADD CONSTRAINT ck_configuracion_colegio_clave
    CHECK (clave IN ('resumen_correo_externo', 'huella_correo_externo'));
INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en)
SELECT c.id, 'huella_correo_externo', b.valor, b.creado_en
  FROM configuracion_bd b CROSS JOIN colegio c
 WHERE b.clave = 'huella_correo_externo' AND (SELECT COUNT(*) FROM colegio) = 1;
