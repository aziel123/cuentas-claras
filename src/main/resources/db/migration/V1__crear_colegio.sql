-- Colegios clientes de la plataforma (multi-colegio).
CREATE TABLE colegio (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(150) NOT NULL,
    ruc       VARCHAR(11),
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO colegio (nombre) VALUES ('Colegio Virgen María');
