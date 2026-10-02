-- Sprint 2, tanda 2: registro de cada importación CONFIRMADA de alumnos y apoderados desde Excel.
-- Solo inserción (sin UPDATE ni DELETE para cc_app). No guarda el archivo ni datos personales: solo
-- su huella SHA-256 (para avisar si se reimporta el mismo archivo) y los conteos.
CREATE TABLE importacion_alumnos (
    id                       BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id               BIGINT        NOT NULL,
    anio_escolar_id          BIGINT        NOT NULL,
    archivo_nombre           VARCHAR(150)  NOT NULL,
    archivo_sha256           VARCHAR(64)   NOT NULL,
    archivo_bytes            INT           NOT NULL,
    filas                    INT           NOT NULL,
    alumnos_nuevos           INT           NOT NULL,
    alumnos_actualizados     INT           NOT NULL,
    alumnos_sin_cambios      INT           NOT NULL,
    apoderados_nuevos        INT           NOT NULL,
    apoderados_actualizados  INT           NOT NULL,
    familias_nuevas          INT           NOT NULL,
    matriculas_nuevas        INT           NOT NULL,
    creado_en                DATETIME(6)   NOT NULL,
    creado_por               VARCHAR(60)   NOT NULL,
    actualizado_en           DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_importacion_alumnos_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_importacion_alumnos_anio FOREIGN KEY (anio_escolar_id, colegio_id)
        REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_importacion_alumnos_conteos CHECK (filas > 0 AND alumnos_nuevos >= 0 AND alumnos_actualizados >= 0
        AND alumnos_sin_cambios >= 0 AND apoderados_nuevos >= 0 AND apoderados_actualizados >= 0
        AND familias_nuevas >= 0 AND matriculas_nuevas >= 0)
);
CREATE INDEX ix_importacion_alumnos_sha ON importacion_alumnos (colegio_id, archivo_sha256);
