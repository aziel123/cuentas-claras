-- Sprint 10 · tanda 2 (docs/arquitectura/sprint-10-notas-libretas-siagie.md, secciones 3.5, 3.6, 3.13 y 5). Notas por
-- competencia en un libro de SOLO INSERCIÓN: cambiar es agregar una fila que reemplaza a la vigente (cadena lineal, como
-- marca_asistencia). Con el periodo CERRADO, nada cambia sin una solicitud aprobada por otra persona. Con la auditoría en
-- papel: la matrícula RESERVADA (impaga) recibe nota igual (C1, en el trigger); la exoneración rige desde un periodo
-- ABIERTO (A3); la conclusión de una corrección va en borrador_conclusion, nunca en la solicitud ni en la bitácora (M8).
-- Nada se borra. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Lección de V32 a V35: un CHECK que evalúa NULL PASA; toda columna que admite NULL se compara con IS NOT NULL.

-- Un guardado del docente (un POST, una firma: lote_calificacion:{periodo}:{seccion}:{area}:{secuencia}, H3). secuencia:
-- 1, 2, 3… por periodo, sección y área. Con el periodo CERRADO, solo con su CALIFICACION_TARDIA aprobada (solicitud_id).
CREATE TABLE lote_calificacion (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    periodo_id       BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    area_id          BIGINT        NOT NULL,
    secuencia        INT           NOT NULL,
    solicitud_id     BIGINT,
    sesion_id        BIGINT        NOT NULL,
    filas            INT           NOT NULL,
    registrado_bd    DATETIME(6),
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_lote_calificacion_secuencia UNIQUE (periodo_id, seccion_id, area_id, secuencia),
    CONSTRAINT uk_lote_calificacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_lote_calificacion_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT fk_lote_calificacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_lote_calificacion_periodo FOREIGN KEY (periodo_id, colegio_id) REFERENCES periodo_evaluacion (id, colegio_id),
    CONSTRAINT fk_lote_calificacion_seccion FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_lote_calificacion_area FOREIGN KEY (area_id) REFERENCES area_curricular (id),
    CONSTRAINT fk_lote_calificacion_solicitud FOREIGN KEY (solicitud_id, colegio_id) REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT fk_lote_calificacion_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_lote_calificacion_valores CHECK (secuencia BETWEEN 1 AND 100000 AND filas BETWEEN 1 AND 2000),
    CONSTRAINT ck_lote_calificacion_actor CHECK (creado_por NOT LIKE 'sistema%')
);

-- Una nota. SOLO INSERCIÓN. inicial: TRUE en la primera de la cadena (reemplaza_id NULL), si no NULL (UNIQUE de la inicial).
-- nivel_logro NULL = «sin nota en este periodo» (solo en un cambio con el periodo abierto o en una corrección aprobada).
CREATE TABLE calificacion (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT        NOT NULL,
    anio_escolar_id    BIGINT        NOT NULL,
    periodo_id         BIGINT        NOT NULL,
    seccion_id         BIGINT        NOT NULL,
    matricula_id       BIGINT        NOT NULL,
    alumno_id          BIGINT        NOT NULL,
    competencia_id     BIGINT        NOT NULL,
    nivel_logro        VARCHAR(2),
    conclusion         VARCHAR(1000),
    conclusion_sha256  VARCHAR(64),             -- SHA2 de la conclusión (la bitácora y la solicitud guardan esto, no el texto)
    origen             VARCHAR(20)   NOT NULL,   -- REGISTRO | CAMBIO_ABIERTO | REGISTRO_TARDIO | CORRECCION_APROBADA
    inicial            BOOLEAN,
    reemplaza_id       BIGINT,
    lote_id            BIGINT,
    solicitud_id       BIGINT,
    registrado_bd      DATETIME(6),
    creado_en          DATETIME(6)   NOT NULL,
    creado_por         VARCHAR(60)   NOT NULL,
    actualizado_en     DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_calificacion_inicial UNIQUE (periodo_id, matricula_id, competencia_id, inicial),
    CONSTRAINT uk_calificacion_reemplaza UNIQUE (reemplaza_id),
    CONSTRAINT uk_calificacion_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT uk_calificacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_calificacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_calificacion_periodo FOREIGN KEY (periodo_id, anio_escolar_id) REFERENCES periodo_evaluacion (id, anio_escolar_id),
    CONSTRAINT fk_calificacion_periodo_colegio FOREIGN KEY (periodo_id, colegio_id) REFERENCES periodo_evaluacion (id, colegio_id),
    CONSTRAINT fk_calificacion_seccion FOREIGN KEY (seccion_id, anio_escolar_id) REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_calificacion_seccion_colegio FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_calificacion_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_calificacion_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_calificacion_competencia FOREIGN KEY (competencia_id) REFERENCES competencia (id),
    CONSTRAINT fk_calificacion_reemplaza FOREIGN KEY (reemplaza_id, colegio_id) REFERENCES calificacion (id, colegio_id),
    CONSTRAINT fk_calificacion_lote FOREIGN KEY (lote_id, colegio_id) REFERENCES lote_calificacion (id, colegio_id),
    CONSTRAINT fk_calificacion_solicitud FOREIGN KEY (solicitud_id, colegio_id) REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT ck_calificacion_nivel CHECK (nivel_logro IS NULL OR nivel_logro IN ('AD', 'A', 'B', 'C')),
    CONSTRAINT ck_calificacion_origen CHECK (origen IN ('REGISTRO', 'CAMBIO_ABIERTO', 'REGISTRO_TARDIO', 'CORRECCION_APROBADA')
        AND ((origen IN ('REGISTRO', 'REGISTRO_TARDIO') AND reemplaza_id IS NULL AND inicial IS NOT NULL AND inicial
                AND nivel_logro IS NOT NULL)
            OR (origen IN ('CAMBIO_ABIERTO', 'CORRECCION_APROBADA') AND reemplaza_id IS NOT NULL AND inicial IS NULL))
        AND ((origen = 'CORRECCION_APROBADA' AND solicitud_id IS NOT NULL AND lote_id IS NULL)
            OR (origen <> 'CORRECCION_APROBADA' AND lote_id IS NOT NULL AND solicitud_id IS NULL))),
    CONSTRAINT ck_calificacion_conclusion CHECK ((conclusion IS NULL AND conclusion_sha256 IS NULL)
        OR (conclusion IS NOT NULL AND CHAR_LENGTH(TRIM(conclusion)) >= 10 AND conclusion_sha256 IS NOT NULL
            AND REGEXP_LIKE(conclusion_sha256, '^[0-9a-f]{64}$', 'c') AND nivel_logro IS NOT NULL)),
    CONSTRAINT ck_calificacion_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_calificacion_seccion ON calificacion (colegio_id, periodo_id, seccion_id, competencia_id);
CREATE INDEX ix_calificacion_matricula ON calificacion (colegio_id, matricula_id, periodo_id);

-- Exoneración de Religión o Educación Física (RVM 094-2020, 5.2.3.2 y 5.2.3.3). SOLO INSERCIÓN. La autoriza Dirección con
-- su firma (exoneracion_area:{matricula}:{area}:AUTORIZADA) y rige DESDE un periodo ABIERTO (A3): las notas de los periodos
-- anteriores quedan.
CREATE TABLE exoneracion_area (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id        BIGINT        NOT NULL,
    anio_escolar_id   BIGINT        NOT NULL,
    matricula_id      BIGINT        NOT NULL,
    area_id           BIGINT        NOT NULL,
    desde_periodo_id  BIGINT        NOT NULL,
    documento         VARCHAR(40)   NOT NULL,   -- código del escrito del padre (no se guarda el escrito)
    motivo            VARCHAR(300)  NOT NULL,
    autorizado_por    VARCHAR(60)   NOT NULL,
    creado_en         DATETIME(6)   NOT NULL,
    creado_por        VARCHAR(60)   NOT NULL,
    actualizado_en    DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exoneracion_area UNIQUE (matricula_id, area_id),
    CONSTRAINT uk_exoneracion_area_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_exoneracion_area_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_exoneracion_area_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_exoneracion_area_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_exoneracion_area_area FOREIGN KEY (area_id) REFERENCES area_curricular (id),
    CONSTRAINT fk_exoneracion_area_periodo FOREIGN KEY (desde_periodo_id, colegio_id) REFERENCES periodo_evaluacion (id, colegio_id),
    CONSTRAINT ck_exoneracion_area_valores CHECK (CHAR_LENGTH(TRIM(motivo)) >= 10 AND CHAR_LENGTH(TRIM(documento)) >= 3
        AND creado_por = autorizado_por AND creado_por NOT LIKE 'sistema%')
);

-- M8: el borrador de la conclusión de una corrección. SOLO INSERCIÓN. El texto nunca va en solicitud_cambio.datos ni en la
-- bitácora: datos lleva borrador_id y su SHA-256. Lo escribe el docente del área o Dirección con su sesión, con el periodo
-- de la calificación CERRADO (trg_borrador_conclusion_nace recalcula el SHA-256).
CREATE TABLE borrador_conclusion (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    calificacion_id  BIGINT        NOT NULL,   -- la vigente que se quiere corregir
    texto            VARCHAR(1000) NOT NULL,
    sha256           VARCHAR(64)   NOT NULL,
    sesion_id        BIGINT        NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_borrador_conclusion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_borrador_conclusion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_borrador_conclusion_calificacion FOREIGN KEY (calificacion_id, colegio_id) REFERENCES calificacion (id, colegio_id),
    CONSTRAINT fk_borrador_conclusion_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_borrador_conclusion_valores CHECK (CHAR_LENGTH(TRIM(texto)) >= 10
        AND REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c') AND creado_por NOT LIKE 'sistema%')
);
