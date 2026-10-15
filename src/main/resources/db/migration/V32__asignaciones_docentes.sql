-- Sprint 9 · tanda 1 (docs/arquitectura/sprint-9-asistencia-comunicados.md, secciones 3.2, 3.3 y 6). Asignaciones de
-- docentes, tutores y auxiliares a secciones por año escolar. Dar acceso a los datos de una sección lo deciden dos
-- personas (propone Administración o Dirección; aprueba otra persona de Dirección o Promotoría con su firma, nunca el
-- docente asignado); quitarlo, una (Dirección o Promotoría con su firma, o sistema.academico). Nada se borra.
-- Compatible con MySQL 8 y con H2 en modo MySQL.
CREATE TABLE asignacion_docente (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    anio_escolar_id  BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    usuario_id       BIGINT        NOT NULL,   -- el docente (cuenta del personal con rol DOCENTE)
    tipo             VARCHAR(10)   NOT NULL,   -- TUTOR | DOCENTE | AUXILIAR
    areas            VARCHAR(120),             -- texto en este sprint; el sprint 10 agrega el catálogo
    estado           VARCHAR(12)   NOT NULL,   -- PROPUESTA | VIGENTE | RECHAZADA | TERMINADA
    pendiente        BOOLEAN,                  -- TRUE solo en PROPUESTA; si no, NULL
    vigente          BOOLEAN,                  -- TRUE solo en VIGENTE; si no, NULL
    tutor_vigente    BOOLEAN,                  -- TRUE solo en un TUTOR VIGENTE; si no, NULL (un tutor por sección)
    solicitud_id     BIGINT,
    propuesto_por    VARCHAR(60)   NOT NULL,
    resuelto_por     VARCHAR(60),
    resuelto_en      DATETIME(6),
    terminada_por    VARCHAR(60),
    terminada_en     DATETIME(6),
    motivo_fin       VARCHAR(300),
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_asignacion_docente_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_asignacion_docente_pendiente UNIQUE (seccion_id, usuario_id, tipo, pendiente),
    CONSTRAINT uk_asignacion_docente_vigente UNIQUE (seccion_id, usuario_id, tipo, vigente),
    CONSTRAINT uk_asignacion_docente_tutor UNIQUE (seccion_id, tutor_vigente),
    CONSTRAINT uk_asignacion_docente_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT fk_asignacion_docente_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_asignacion_docente_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_asignacion_docente_seccion FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_asignacion_docente_seccion_anio FOREIGN KEY (seccion_id, anio_escolar_id)
        REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_asignacion_docente_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_asignacion_docente_solicitud FOREIGN KEY (solicitud_id, colegio_id)
        REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT ck_asignacion_docente_tipo CHECK (tipo IN ('TUTOR', 'DOCENTE', 'AUXILIAR')),
    -- Las banderas valen TRUE o NULL, nunca FALSE ni NULL donde deben ser TRUE: un CHECK que evalúa NULL PASA, así que cada
    -- «pendiente» va con su IS NOT NULL (si no, una PROPUESTA con pendiente NULL se saltaría el UNIQUE de una pendiente).
    CONSTRAINT ck_asignacion_docente_estado CHECK (estado IN ('PROPUESTA', 'VIGENTE', 'RECHAZADA', 'TERMINADA')
        AND ((estado = 'PROPUESTA' AND pendiente IS NOT NULL AND pendiente)
            OR (estado <> 'PROPUESTA' AND pendiente IS NULL))
        AND ((estado = 'VIGENTE' AND vigente IS NOT NULL AND vigente)
            OR (estado <> 'VIGENTE' AND vigente IS NULL))
        AND ((estado = 'VIGENTE' AND tipo = 'TUTOR' AND tutor_vigente IS NOT NULL AND tutor_vigente)
            OR (NOT (estado = 'VIGENTE' AND tipo = 'TUTOR') AND tutor_vigente IS NULL))),
    CONSTRAINT ck_asignacion_docente_resolucion CHECK ((estado = 'PROPUESTA' AND resuelto_por IS NULL AND resuelto_en IS NULL)
        OR (estado <> 'PROPUESTA' AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL
            AND resuelto_por <> propuesto_por)),
    CONSTRAINT ck_asignacion_docente_fin CHECK ((estado = 'TERMINADA' AND terminada_por IS NOT NULL
            AND terminada_en IS NOT NULL AND motivo_fin IS NOT NULL AND CHAR_LENGTH(TRIM(motivo_fin)) >= 5)
        OR (estado <> 'TERMINADA' AND terminada_por IS NULL AND terminada_en IS NULL AND motivo_fin IS NULL)),
    CONSTRAINT ck_asignacion_docente_actor CHECK (creado_por = propuesto_por AND propuesto_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_asignacion_docente_usuario ON asignacion_docente (colegio_id, usuario_id, estado);
CREATE INDEX ix_asignacion_docente_seccion ON asignacion_docente (colegio_id, seccion_id, estado);

-- Ley 29733 (sección 3.12): quién vio la lista de una sección, un reporte de asistencia o la nómina para el SIAGIE
-- (cantidad de alumnos, sin nombres). Se agregan los tres tipos de una vez (H7). Se conserva COBRO (V27, S7-B1).
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_tipo;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_tipo CHECK (tipo IN ('FICHA_FAMILIA', 'FICHA_ALUMNO',
    'BUSQUEDA', 'MOROSOS', 'LLAMADA_CONTROL', 'IMPORTACION', 'APROBACION_CONTACTO', 'COBRO', 'LISTA_SECCION',
    'REPORTE_ASISTENCIA', 'NOMINA_SIAGIE'));
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_objeto;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_objeto CHECK (
    (tipo IN ('FICHA_FAMILIA', 'APROBACION_CONTACTO', 'LLAMADA_CONTROL', 'COBRO') AND familia_id IS NOT NULL)
    OR (tipo = 'FICHA_ALUMNO' AND alumno_id IS NOT NULL)
    OR tipo IN ('BUSQUEDA', 'MOROSOS', 'IMPORTACION', 'LISTA_SECCION', 'REPORTE_ASISTENCIA', 'NOMINA_SIAGIE'));
