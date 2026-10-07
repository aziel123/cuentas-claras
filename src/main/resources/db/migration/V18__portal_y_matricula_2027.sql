-- Sprint 5 · tanda 2: renovación de la matrícula 2027 (la familia confirma; sin confirmación no hay deuda), matrícula
-- RESERVADA hasta pagar la matrícula, y avisos de la familia («¿algo no cuadra?») que solo ven Promotoría y Dirección.
-- Nada se borra. Los triggers de scripts/mysql/03-triggers.sql (tanda 2) vigilan los estados.

-- Matrícula RESERVADA: año siguiente, solo con la cuota de matrícula; pasa a ACTIVA al pagarla (trigger). Las pensiones
-- se generan al activarse.
ALTER TABLE matricula DROP CONSTRAINT ck_matricula_estado;
ALTER TABLE matricula ADD CONSTRAINT ck_matricula_estado CHECK (estado IN ('RESERVADA', 'ACTIVA', 'RETIRADA'));
ALTER TABLE matricula DROP CONSTRAINT ck_matricula_retiro;
ALTER TABLE matricula ADD CONSTRAINT ck_matricula_retiro CHECK ((estado = 'RETIRADA' AND retirada_en IS NOT NULL)
    OR (estado IN ('RESERVADA', 'ACTIVA') AND retirada_en IS NULL));
ALTER TABLE matricula ADD COLUMN activada_en DATETIME(6);
ALTER TABLE matricula ADD COLUMN activada_por VARCHAR(60);
ALTER TABLE matricula ADD CONSTRAINT ck_matricula_activacion CHECK ((activada_en IS NULL AND activada_por IS NULL)
    OR (activada_en IS NOT NULL AND activada_por IS NOT NULL AND activada_por = 'sistema.matricula'));

-- Renovación: propuesta del sistema para cada alumno que continúa. La familia confirma (PORTAL) o Administración registra
-- su respuesta (PRESENCIAL; la familia recibe un aviso). deuda_al_proponer: solo informativa (decisión 55).
CREATE TABLE renovacion_matricula (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT         NOT NULL,
    anio_destino_id      BIGINT         NOT NULL,
    alumno_id            BIGINT         NOT NULL,
    familia_id           BIGINT         NOT NULL,
    matricula_origen_id  BIGINT         NOT NULL,
    grado_destino        VARCHAR(20)    NOT NULL,
    seccion_destino_id   BIGINT         NOT NULL,
    deuda_al_proponer    DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    vence_en             DATE           NOT NULL,
    estado               VARCHAR(20)    NOT NULL,
    canal_respuesta      VARCHAR(12),
    respondido_por       VARCHAR(60),
    respondido_en        DATETIME(6),
    matricula_id         BIGINT,
    creado_en            DATETIME(6)    NOT NULL,
    creado_por           VARCHAR(60)    NOT NULL,
    actualizado_en       DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_renovacion_alumno UNIQUE (colegio_id, alumno_id, anio_destino_id),
    CONSTRAINT uk_renovacion_matricula UNIQUE (matricula_id),
    CONSTRAINT uk_renovacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_renovacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_renovacion_anio FOREIGN KEY (anio_destino_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_renovacion_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_renovacion_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_renovacion_origen FOREIGN KEY (matricula_origen_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_renovacion_seccion FOREIGN KEY (seccion_destino_id, anio_destino_id)
        REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_renovacion_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT ck_renovacion_estado CHECK (estado IN ('PROPUESTA', 'CONFIRMADA', 'MATRICULADA', 'NO_CONTINUA',
        'VENCIDA')),
    CONSTRAINT ck_renovacion_grado CHECK (grado_destino IN ('INICIAL_3', 'INICIAL_4', 'INICIAL_5',
        'PRIMARIA_1', 'PRIMARIA_2', 'PRIMARIA_3', 'PRIMARIA_4', 'PRIMARIA_5', 'PRIMARIA_6',
        'SECUNDARIA_1', 'SECUNDARIA_2', 'SECUNDARIA_3', 'SECUNDARIA_4', 'SECUNDARIA_5')),
    CONSTRAINT ck_renovacion_deuda CHECK (deuda_al_proponer >= 0),
    CONSTRAINT ck_renovacion_respuesta CHECK (
        (estado IN ('PROPUESTA', 'VENCIDA') AND canal_respuesta IS NULL AND respondido_por IS NULL
            AND respondido_en IS NULL)
        OR (estado IN ('CONFIRMADA', 'MATRICULADA', 'NO_CONTINUA') AND canal_respuesta IS NOT NULL
            AND canal_respuesta IN ('PORTAL', 'PRESENCIAL') AND respondido_por IS NOT NULL
            AND respondido_en IS NOT NULL AND respondido_por NOT LIKE 'sistema%')),
    CONSTRAINT ck_renovacion_matricula CHECK ((estado = 'MATRICULADA' AND matricula_id IS NOT NULL)
        OR (estado <> 'MATRICULADA' AND matricula_id IS NULL))
);
CREATE INDEX ix_renovacion_estado ON renovacion_matricula (colegio_id, anio_destino_id, estado);
CREATE INDEX ix_renovacion_familia ON renovacion_matricula (colegio_id, familia_id);

-- «¿Algo no cuadra?»: lo escribe el apoderado; lo ven y lo atienden SOLO Promotoría o Dirección. El texto no cambia.
CREATE TABLE aviso_familia (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    familia_id      BIGINT        NOT NULL,
    apoderado_id    BIGINT        NOT NULL,
    tipo            VARCHAR(40)   NOT NULL,
    pago_id         BIGINT,
    cuota_id        BIGINT,
    texto           VARCHAR(500)  NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    atendido_por    VARCHAR(60),
    atendido_en     DATETIME(6),
    respuesta       VARCHAR(500),
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_aviso_familia_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_aviso_familia_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_aviso_familia_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_aviso_familia_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT fk_aviso_familia_pago FOREIGN KEY (pago_id, colegio_id) REFERENCES pago (id, colegio_id),
    CONSTRAINT fk_aviso_familia_cuota FOREIGN KEY (cuota_id, colegio_id) REFERENCES cuota (id, colegio_id),
    CONSTRAINT ck_aviso_familia_tipo CHECK (tipo IN ('PAGUE_Y_NO_APARECE', 'NO_RECONOZCO_PAGO',
        'NO_RECONOZCO_ANULACION_O_DESCUENTO', 'OTRO')),
    CONSTRAINT ck_aviso_familia_estado CHECK (
        (estado = 'ABIERTO' AND atendido_por IS NULL AND atendido_en IS NULL AND respuesta IS NULL)
        OR (estado = 'ATENDIDO' AND atendido_por IS NOT NULL AND atendido_en IS NOT NULL AND respuesta IS NOT NULL
            AND atendido_por NOT LIKE 'sistema%'))
);
CREATE INDEX ix_aviso_familia_estado ON aviso_familia (colegio_id, estado, creado_en);
CREATE INDEX ix_aviso_familia_familia ON aviso_familia (colegio_id, familia_id, creado_en);
