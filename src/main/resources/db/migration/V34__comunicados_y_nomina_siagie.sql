-- Sprint 9 · tanda 3. Comunicados que no se editan (fe de erratas), con PDF saneado, destinatarios congelados y
-- confirmación de lectura de la familia con su sesión; datos para la matrícula en el SIAGIE. Nada se borra.
-- Con la auditoría en papel de la tanda 3 (sección 3.16 del diseño): la huella del contenido es única por colegio (B2),
-- el adjunto dice si requiere revisión (B3), un comunicado POR_APROBAR o RECHAZADO no tiene envío ni destinatarios (B13),
-- la exportación de la nómina lleva la sesión de quien la sacó (B14) y el código de estudiante va en mayúsculas (B15).
-- Lección de V32: un CHECK que evalúa NULL PASA; toda columna que admite NULL se compara con IS NOT NULL.

-- SIAGIE (sección 3.11): sexo y código de estudiante (solo para quien no tiene DNI), opcionales.
ALTER TABLE alumno ADD COLUMN sexo VARCHAR(1);
ALTER TABLE alumno ADD COLUMN codigo_estudiante VARCHAR(20);
ALTER TABLE alumno ADD CONSTRAINT ck_alumno_sexo CHECK (sexo IS NULL OR sexo IN ('M', 'F'));
ALTER TABLE alumno ADD CONSTRAINT ck_alumno_codigo_estudiante CHECK (codigo_estudiante IS NULL
    OR REGEXP_LIKE(codigo_estudiante, '^[0-9A-Z]{8,20}$', 'c'));
ALTER TABLE alumno ADD CONSTRAINT uk_alumno_codigo_estudiante UNIQUE (colegio_id, codigo_estudiante);

-- Códigos modulares del colegio, uno por nivel (los registra Dirección; se ven en ESCALE).
CREATE TABLE codigo_modular (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    nivel            VARCHAR(12)   NOT NULL,
    codigo           VARCHAR(7)    NOT NULL,
    anexo            VARCHAR(1)    NOT NULL DEFAULT '0',
    actualizado_por  VARCHAR(60)   NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_codigo_modular_nivel UNIQUE (colegio_id, nivel),
    CONSTRAINT fk_codigo_modular_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_codigo_modular_valores CHECK (nivel IN ('INICIAL', 'PRIMARIA', 'SECUNDARIA')
        AND REGEXP_LIKE(codigo, '^[0-9]{7}$') AND REGEXP_LIKE(anexo, '^[0-9]$')),
    CONSTRAINT ck_codigo_modular_actor CHECK (creado_por NOT LIKE 'sistema%')
);

-- El PDF saneado de un comunicado (sección 3.9). SOLO INSERCIÓN. requiere_revision (B3) lo calcula el analizador: menos
-- de 50 caracteres extraíbles por página o con imágenes; ese PDF sale solo con la aprobación del otro órgano.
CREATE TABLE comunicado_adjunto (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id         BIGINT        NOT NULL,
    contenido          MEDIUMBLOB    NOT NULL,   -- el PDF vuelto a guardar por PDFBox (en el proceso hijo)
    bytes              INT           NOT NULL,
    paginas            INT           NOT NULL,
    sha256             VARCHAR(64)   NOT NULL,   -- del saneado
    sha256_original    VARCHAR(64)   NOT NULL,
    requiere_revision  BOOLEAN       NOT NULL,
    creado_en          DATETIME(6)   NOT NULL,
    creado_por         VARCHAR(60)   NOT NULL,
    actualizado_en     DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_comunicado_adjunto_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_comunicado_adjunto_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_comunicado_adjunto_tamano CHECK (bytes BETWEEN 1 AND 2097152 AND paginas BETWEEN 1 AND 10),
    CONSTRAINT ck_comunicado_adjunto_sha CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c')
        AND REGEXP_LIKE(sha256_original, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_comunicado_adjunto_actor CHECK (creado_por NOT LIKE 'sistema%')
);

-- El comunicado nace COMPLETO al enviarse (no hay borradores en la base). Título, texto, alcance, emisor, urgente,
-- confirmación, adjunto, sesión y huella NO están en ningún GRANT de UPDATE (1143): un comunicado enviado no se edita;
-- se corrige con una fe de erratas. La huella la recalcula trg_comunicado_nace (B2) y es única por colegio.
CREATE TABLE comunicado (
    id                     BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id             BIGINT         NOT NULL,
    anio_escolar_id        BIGINT         NOT NULL,
    tipo                   VARCHAR(14)    NOT NULL,   -- NORMAL | FE_DE_ERRATAS
    corrige_id             BIGINT,
    emisor_usuario_id      BIGINT         NOT NULL,
    emisor_rol             VARCHAR(12)    NOT NULL,   -- PROMOTORIA | DIRECCION | DOCENTE
    emisor_etiqueta        VARCHAR(120)   NOT NULL,   -- la arma el sistema («Dirección del …», «Prof. …, tutor(a) de …»)
    alcance                VARCHAR(10)    NOT NULL,   -- COLEGIO | NIVEL | GRADO | SECCION
    nivel                  VARCHAR(12),
    grado                  VARCHAR(20),
    seccion_id             BIGINT,
    titulo                 VARCHAR(120)   NOT NULL,
    cuerpo                 VARCHAR(4000)  NOT NULL,
    urgente                BOOLEAN        NOT NULL DEFAULT FALSE,
    requiere_confirmacion  BOOLEAN        NOT NULL DEFAULT TRUE,
    adjunto_id             BIGINT,
    huella_contenido       VARCHAR(64)    NOT NULL,   -- SHA-256 de alcance (con nivel, grado o sección), título, texto y SHA-256 del adjunto
    estado                 VARCHAR(12)    NOT NULL,   -- POR_APROBAR | ENVIADO | RECHAZADO | RETIRADO
    solicitud_id           BIGINT,                    -- PUBLICAR_COMUNICADO (PDF de docente o PDF que requiere revisión)
    enviado_en             DATETIME(6),
    destinatarios          INT,
    retirado_por           VARCHAR(60),
    retirado_en            DATETIME(6),
    motivo_retiro          VARCHAR(300),
    sesion_id              BIGINT         NOT NULL,
    creado_en              DATETIME(6)    NOT NULL,
    creado_por             VARCHAR(60)    NOT NULL,
    actualizado_en         DATETIME(6)    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_comunicado_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_comunicado_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT uk_comunicado_huella UNIQUE (colegio_id, huella_contenido),
    CONSTRAINT fk_comunicado_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_comunicado_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_comunicado_corrige FOREIGN KEY (corrige_id, colegio_id) REFERENCES comunicado (id, colegio_id),
    CONSTRAINT fk_comunicado_emisor FOREIGN KEY (emisor_usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_comunicado_seccion FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_comunicado_seccion_anio FOREIGN KEY (seccion_id, anio_escolar_id) REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_comunicado_adjunto FOREIGN KEY (adjunto_id, colegio_id) REFERENCES comunicado_adjunto (id, colegio_id),
    CONSTRAINT fk_comunicado_solicitud FOREIGN KEY (solicitud_id, colegio_id) REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT fk_comunicado_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_comunicado_tipo CHECK (tipo IN ('NORMAL', 'FE_DE_ERRATAS')
        AND ((tipo = 'FE_DE_ERRATAS' AND corrige_id IS NOT NULL) OR (tipo = 'NORMAL' AND corrige_id IS NULL))),
    CONSTRAINT ck_comunicado_alcance CHECK (
        (alcance = 'COLEGIO' AND nivel IS NULL AND grado IS NULL AND seccion_id IS NULL)
        OR (alcance = 'NIVEL' AND nivel IS NOT NULL AND nivel IN ('INICIAL', 'PRIMARIA', 'SECUNDARIA')
            AND grado IS NULL AND seccion_id IS NULL)
        OR (alcance = 'GRADO' AND grado IS NOT NULL AND grado IN ('INICIAL_3', 'INICIAL_4', 'INICIAL_5',
                'PRIMARIA_1', 'PRIMARIA_2', 'PRIMARIA_3', 'PRIMARIA_4', 'PRIMARIA_5', 'PRIMARIA_6',
                'SECUNDARIA_1', 'SECUNDARIA_2', 'SECUNDARIA_3', 'SECUNDARIA_4', 'SECUNDARIA_5')
            AND nivel IS NULL AND seccion_id IS NULL)
        OR (alcance = 'SECCION' AND seccion_id IS NOT NULL AND nivel IS NULL AND grado IS NULL)),
    CONSTRAINT ck_comunicado_emisor CHECK (emisor_rol IN ('PROMOTORIA', 'DIRECCION', 'DOCENTE')
        AND (emisor_rol <> 'DOCENTE' OR (alcance = 'SECCION' AND NOT urgente))),
    CONSTRAINT ck_comunicado_texto CHECK (CHAR_LENGTH(TRIM(titulo)) >= 5 AND CHAR_LENGTH(TRIM(cuerpo)) >= 10
        AND REGEXP_LIKE(huella_contenido, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_comunicado_estado CHECK (estado IN ('POR_APROBAR', 'ENVIADO', 'RECHAZADO', 'RETIRADO')
        AND ((estado IN ('ENVIADO', 'RETIRADO') AND enviado_en IS NOT NULL)
            OR (estado IN ('POR_APROBAR', 'RECHAZADO') AND enviado_en IS NULL AND destinatarios IS NULL))
        AND ((estado = 'RETIRADO' AND retirado_por IS NOT NULL AND retirado_en IS NOT NULL
                AND motivo_retiro IS NOT NULL AND CHAR_LENGTH(TRIM(motivo_retiro)) >= 10)
            OR (estado <> 'RETIRADO' AND retirado_por IS NULL AND retirado_en IS NULL AND motivo_retiro IS NULL))),
    CONSTRAINT ck_comunicado_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_comunicado_fecha ON comunicado (colegio_id, estado, enviado_en);
CREATE INDEX ix_comunicado_emisor ON comunicado (colegio_id, emisor_usuario_id, creado_en);

-- Familias destinatarias, congeladas al enviarse. Solo cambian abierto_en (la primera vez que la familia lo abre) y el
-- mensaje que lo avisó y el que lo recordó (los escribe sistema.comunicados). Una fe de erratas llega a TODAS las del
-- original (B1): lo exige trg_comunicado_estado al cerrar la lista (comunicado.destinatarios).
CREATE TABLE comunicado_destinatario (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id           BIGINT        NOT NULL,
    comunicado_id        BIGINT        NOT NULL,
    familia_id           BIGINT        NOT NULL,
    alumnos              INT           NOT NULL,   -- cuántos hijos tiene en el alcance
    abierto_en           DATETIME(6),
    avisado_mensaje_id   BIGINT,                   -- el resumen (o urgente) que lo avisó por primera vez
    recordado_mensaje_id BIGINT,                   -- el resumen que llevó el recordatorio de las 48 h
    creado_en            DATETIME(6)   NOT NULL,
    creado_por           VARCHAR(60)   NOT NULL,
    actualizado_en       DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_comunicado_destinatario UNIQUE (comunicado_id, familia_id),
    CONSTRAINT uk_comunicado_destinatario_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_comunicado_destinatario_comunicado FOREIGN KEY (comunicado_id, colegio_id)
        REFERENCES comunicado (id, colegio_id),
    CONSTRAINT fk_comunicado_destinatario_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_comunicado_destinatario_aviso FOREIGN KEY (avisado_mensaje_id, colegio_id)
        REFERENCES mensaje (id, colegio_id),
    CONSTRAINT fk_comunicado_destinatario_recordatorio FOREIGN KEY (recordado_mensaje_id, colegio_id)
        REFERENCES mensaje (id, colegio_id),
    CONSTRAINT ck_comunicado_destinatario_alumnos CHECK (alumnos >= 1)
);
CREATE INDEX ix_comunicado_destinatario_familia ON comunicado_destinatario (colegio_id, familia_id, creado_en);

-- «Confirmo que lo leí»: una por familia y comunicado, por un apoderado de ESA familia con su sesión. SOLO INSERCIÓN.
-- La FK a comunicado_destinatario exige que la familia sea destinataria.
CREATE TABLE confirmacion_lectura (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    comunicado_id    BIGINT        NOT NULL,
    familia_id       BIGINT        NOT NULL,
    apoderado_id     BIGINT        NOT NULL,
    usuario_id       BIGINT        NOT NULL,
    sesion_id        BIGINT        NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_confirmacion_lectura UNIQUE (comunicado_id, familia_id),
    CONSTRAINT uk_confirmacion_lectura_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_confirmacion_lectura_destinatario FOREIGN KEY (comunicado_id, familia_id)
        REFERENCES comunicado_destinatario (comunicado_id, familia_id),
    CONSTRAINT fk_confirmacion_lectura_comunicado FOREIGN KEY (comunicado_id, colegio_id)
        REFERENCES comunicado (id, colegio_id),
    CONSTRAINT fk_confirmacion_lectura_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT fk_confirmacion_lectura_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_confirmacion_lectura_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_confirmacion_lectura_actor CHECK (creado_por NOT LIKE 'sistema%')
);

-- Cada descarga de la nómina para el SIAGIE (sin datos de alumnos: el archivo no se guarda). SOLO INSERCIÓN. La saca
-- Dirección con su sesión (B14, trg_exportacion_nomina_nace).
CREATE TABLE exportacion_nomina (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    anio_escolar_id  BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    perfil           VARCHAR(40)   NOT NULL,
    filas            INT           NOT NULL,
    faltantes        INT           NOT NULL,   -- alumnos con datos que faltan para el SIAGIE
    codigo           VARCHAR(16)   NOT NULL,   -- impreso en el archivo
    sha256           VARCHAR(64)   NOT NULL,
    sesion_id        BIGINT        NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exportacion_nomina_codigo UNIQUE (colegio_id, codigo),
    CONSTRAINT uk_exportacion_nomina_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_exportacion_nomina_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_exportacion_nomina_seccion FOREIGN KEY (seccion_id, anio_escolar_id) REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_exportacion_nomina_seccion_colegio FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_exportacion_nomina_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_exportacion_nomina_valores CHECK (filas >= 0 AND faltantes BETWEEN 0 AND filas
        AND REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c') AND REGEXP_LIKE(codigo, '^[A-Z0-9-]{6,16}$', 'c')),
    CONSTRAINT ck_exportacion_nomina_actor CHECK (creado_por NOT LIKE 'sistema%')
);

ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA', 'COMUNICADOS_RESUMEN', 'COMUNICADO_URGENTE'));
