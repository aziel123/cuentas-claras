-- Sprint 2, tanda 1: estructura académica, familias, apoderados, alumnos y matrículas.
-- Las FK compuestas (x_id, colegio_id) hacen que la base rechace referencias a datos de otro colegio,
-- aunque el código fallara. Nada se borra: se desactiva, se retira o se anula.

-- Año escolar. Solo uno EN_CURSO por colegio: "vigente" vale TRUE en ese año y NULL en los demás
-- (MySQL y H2 aceptan varios NULL en un índice único).
CREATE TABLE anio_escolar (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT       NOT NULL,
    anio            INT          NOT NULL,
    estado          VARCHAR(20)  NOT NULL,
    vigente         BOOLEAN,
    inicio_clases   DATE         NOT NULL,
    fin_clases      DATE         NOT NULL,
    creado_en       DATETIME(6)  NOT NULL,
    creado_por      VARCHAR(60)  NOT NULL,
    actualizado_en  DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_anio_escolar_anio UNIQUE (colegio_id, anio),
    CONSTRAINT uk_anio_escolar_vigente UNIQUE (colegio_id, vigente),
    CONSTRAINT uk_anio_escolar_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_anio_escolar_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_anio_escolar_anio CHECK (anio BETWEEN 2000 AND 2100),
    CONSTRAINT ck_anio_escolar_estado CHECK (estado IN ('PLANIFICADO', 'EN_CURSO', 'CERRADO')),
    CONSTRAINT ck_anio_escolar_vigente CHECK ((estado = 'EN_CURSO' AND vigente IS NOT NULL AND vigente = TRUE)
        OR (estado <> 'EN_CURSO' AND vigente IS NULL)),
    CONSTRAINT ck_anio_escolar_fechas CHECK (inicio_clases < fin_clases)
);

-- Sección de un grado en un año. Los niveles y grados son el catálogo nacional (enum Grado).
CREATE TABLE seccion (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT       NOT NULL,
    anio_escolar_id  BIGINT       NOT NULL,
    grado            VARCHAR(20)  NOT NULL,
    nombre           VARCHAR(30)  NOT NULL,
    activa           BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en        DATETIME(6)  NOT NULL,
    creado_por       VARCHAR(60)  NOT NULL,
    actualizado_en   DATETIME(6)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_seccion_grado_nombre UNIQUE (colegio_id, anio_escolar_id, grado, nombre),
    CONSTRAINT uk_seccion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_seccion_id_anio UNIQUE (id, anio_escolar_id),
    CONSTRAINT fk_seccion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_seccion_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_seccion_grado CHECK (grado IN ('INICIAL_3', 'INICIAL_4', 'INICIAL_5',
        'PRIMARIA_1', 'PRIMARIA_2', 'PRIMARIA_3', 'PRIMARIA_4', 'PRIMARIA_5', 'PRIMARIA_6',
        'SECUNDARIA_1', 'SECUNDARIA_2', 'SECUNDARIA_3', 'SECUNDARIA_4', 'SECUNDARIA_5'))
);

-- Familia: agrupa a los hermanos y a sus apoderados.
CREATE TABLE familia (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    nombre          VARCHAR(120)  NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_familia_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_familia_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id)
);

-- Apoderado: pertenece a una sola familia. Debe poder recibir avisos: WhatsApp o correo.
CREATE TABLE apoderado (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT        NOT NULL,
    familia_id         BIGINT        NOT NULL,
    tipo_documento     VARCHAR(20)   NOT NULL,
    numero_documento   VARCHAR(12)   NOT NULL,
    apellido_paterno   VARCHAR(60)   NOT NULL,
    apellido_materno   VARCHAR(60),
    nombres            VARCHAR(60)   NOT NULL,
    parentesco         VARCHAR(20)   NOT NULL,
    telefono_whatsapp  VARCHAR(16),
    correo             VARCHAR(150),
    nombre_busqueda    VARCHAR(190)  NOT NULL,
    activo             BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en          DATETIME(6)   NOT NULL,
    creado_por         VARCHAR(60)   NOT NULL,
    actualizado_en     DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_apoderado_documento UNIQUE (colegio_id, tipo_documento, numero_documento),
    CONSTRAINT uk_apoderado_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_apoderado_id_familia UNIQUE (id, familia_id),
    CONSTRAINT fk_apoderado_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_apoderado_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT ck_apoderado_tipo_documento CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE')),
    CONSTRAINT ck_apoderado_parentesco CHECK (parentesco IN ('MADRE', 'PADRE', 'ABUELO', 'TIO', 'HERMANO',
        'TUTOR_LEGAL', 'OTRO')),
    CONSTRAINT ck_apoderado_contacto CHECK (telefono_whatsapp IS NOT NULL OR correo IS NOT NULL)
);
CREATE INDEX ix_apoderado_nombre ON apoderado (colegio_id, nombre_busqueda);

-- Alumno. El responsable de pago es un apoderado de SU familia: lo exige la FK (responsable_pago_id, familia_id).
CREATE TABLE alumno (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT        NOT NULL,
    familia_id           BIGINT        NOT NULL,
    responsable_pago_id  BIGINT        NOT NULL,
    tipo_documento       VARCHAR(20)   NOT NULL,
    numero_documento     VARCHAR(12)   NOT NULL,
    apellido_paterno     VARCHAR(60)   NOT NULL,
    apellido_materno     VARCHAR(60),
    nombres              VARCHAR(60)   NOT NULL,
    fecha_nacimiento     DATE          NOT NULL,
    estado               VARCHAR(20)   NOT NULL,
    nombre_busqueda      VARCHAR(190)  NOT NULL,
    retirado_en          DATE,
    retirado_por         VARCHAR(60),
    motivo_retiro        VARCHAR(500),
    creado_en            DATETIME(6)   NOT NULL,
    creado_por           VARCHAR(60)   NOT NULL,
    actualizado_en       DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_alumno_documento UNIQUE (colegio_id, tipo_documento, numero_documento),
    CONSTRAINT uk_alumno_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_alumno_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_alumno_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_alumno_responsable FOREIGN KEY (responsable_pago_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT ck_alumno_tipo_documento CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE')),
    CONSTRAINT ck_alumno_estado CHECK (estado IN ('ACTIVO', 'RETIRADO', 'EGRESADO')),
    CONSTRAINT ck_alumno_retiro CHECK ((estado = 'RETIRADO' AND retirado_en IS NOT NULL AND motivo_retiro IS NOT NULL)
        OR (estado <> 'RETIRADO' AND retirado_en IS NULL))
);
CREATE INDEX ix_alumno_nombre ON alumno (colegio_id, nombre_busqueda);

-- Matrícula: un alumno en una sección de un año. Una por alumno y año. La sección debe ser de ese año.
CREATE TABLE matricula (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    alumno_id        BIGINT        NOT NULL,
    anio_escolar_id  BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    fecha_matricula  DATE          NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    retirada_en      DATE,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_matricula_alumno_anio UNIQUE (colegio_id, alumno_id, anio_escolar_id),
    CONSTRAINT uk_matricula_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_matricula_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_matricula_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_matricula_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_matricula_seccion FOREIGN KEY (seccion_id, anio_escolar_id) REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT ck_matricula_estado CHECK (estado IN ('ACTIVA', 'RETIRADA')),
    CONSTRAINT ck_matricula_retiro CHECK ((estado = 'RETIRADA' AND retirada_en IS NOT NULL)
        OR (estado = 'ACTIVA' AND retirada_en IS NULL))
);
CREATE INDEX ix_matricula_seccion ON matricula (colegio_id, seccion_id);
