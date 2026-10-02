-- Usuarios del sistema y sus roles. Los usuarios nunca se borran: se desactivan.
CREATE TABLE usuario (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id          BIGINT       NOT NULL,
    nombre_usuario      VARCHAR(60)  NOT NULL,
    nombre_completo     VARCHAR(150) NOT NULL,
    correo              VARCHAR(150),
    clave_hash          VARCHAR(100) NOT NULL,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    debe_cambiar_clave  BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos   INT          NOT NULL DEFAULT 0,
    bloqueado_hasta     DATETIME(6),
    ultimo_ingreso_en   DATETIME(6),
    clave_cambiada_en   DATETIME(6),
    desactivado_en      DATETIME(6),
    desactivado_por     VARCHAR(60),
    creado_en           DATETIME(6)  NOT NULL,
    creado_por          VARCHAR(60)  NOT NULL,
    actualizado_en      DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_usuario_nombre_usuario UNIQUE (nombre_usuario),
    CONSTRAINT fk_usuario_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_usuario_intentos CHECK (intentos_fallidos >= 0)
);

CREATE TABLE usuario_rol (
    usuario_id BIGINT      NOT NULL,
    rol        VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, rol),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_usuario_rol_rol CHECK (rol IN ('PROMOTOR', 'DIRECTOR', 'ADMINISTRACION', 'CAJA', 'DOCENTE', 'APODERADO'))
);
