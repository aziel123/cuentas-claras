-- Bitácora de auditoría: SOLO INSERCIÓN. En producción el usuario de la aplicación
-- tiene únicamente SELECT e INSERT sobre esta tabla (ver docs/operacion/mysql-usuarios.md).
CREATE TABLE evento_auditoria (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    secuencia       BIGINT        NOT NULL,
    colegio_id      BIGINT,
    ocurrido_en     DATETIME(6)   NOT NULL,
    usuario_id      BIGINT,
    nombre_usuario  VARCHAR(60)   NOT NULL,
    roles           VARCHAR(120),
    accion          VARCHAR(40)   NOT NULL,
    entidad         VARCHAR(40),
    entidad_id      VARCHAR(40),
    valor_anterior  VARCHAR(2000),
    valor_nuevo     VARCHAR(2000),
    detalle         VARCHAR(500),
    ip              VARCHAR(45),
    hash            VARCHAR(64)   NOT NULL,
    CONSTRAINT uk_evento_auditoria_secuencia UNIQUE (secuencia),
    CONSTRAINT fk_evento_auditoria_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id)
);
CREATE INDEX ix_evento_auditoria_colegio_fecha ON evento_auditoria (colegio_id, ocurrido_en);
CREATE INDEX ix_evento_auditoria_entidad ON evento_auditoria (colegio_id, entidad, entidad_id);

-- Eslabón actual de la cadena de hashes. Una sola fila; se bloquea (SELECT ... FOR UPDATE)
-- para asignar la secuencia y encadenar el hash de cada evento con el anterior.
CREATE TABLE auditoria_cadena (
    id                BIGINT      NOT NULL PRIMARY KEY,
    ultima_secuencia  BIGINT      NOT NULL,
    ultimo_hash       VARCHAR(64) NOT NULL,
    CONSTRAINT ck_auditoria_cadena_unica CHECK (id = 1)
);
INSERT INTO auditoria_cadena (id, ultima_secuencia, ultimo_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
