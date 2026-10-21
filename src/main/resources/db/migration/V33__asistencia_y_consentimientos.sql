-- Sprint 9 · tanda 2 (docs/arquitectura/sprint-9-asistencia-comunicados.md, secciones 3.4 a 3.6, 3.15 y 6; brief
-- docs/arquitectura/sprint-9/tanda-2.md). Asistencia diaria (registro por sección y día, marcas de SOLO INSERCIÓN: corregir
-- es agregar una marca que reemplaza a la vigente), consentimiento por finalidad y canal para los avisos de asistencia y
-- comunicados, la configuración académica de cada colegio, la cobertura de un docente ausente (A7) y la marca de agua de
-- los avisos (A2). Nada se borra. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Lección de V32: un CHECK que evalúa NULL PASA. Cada columna que admite NULL y que un CHECK exige va con su IS NOT NULL.

-- Horas y plazos de cada colegio (los cambia SOLO Dirección con su firma, A6; queda en la bitácora con el antes y el
-- después). Los rangos los fija la base: el aviso no se puede correr a la noche para que las familias no se enteren (E15).
-- Sin fila, la aplicación usa los valores por defecto (los mismos de esta tabla).
CREATE TABLE configuracion_academica (
    colegio_id               BIGINT       NOT NULL PRIMARY KEY,
    hora_alerta              TIME         NOT NULL DEFAULT '09:30:00',
    hora_aviso               TIME         NOT NULL DEFAULT '10:30:00',
    hora_segundo_aviso       TIME         NOT NULL DEFAULT '16:00:00',
    dias_justificacion       INT          NOT NULL DEFAULT 5,     -- días hábiles
    dias_registro_tardio     INT          NOT NULL DEFAULT 5,     -- días hábiles
    hora_resumen_comunicados TIME         NOT NULL DEFAULT '18:00:00',
    tope_resumenes_mes       INT          NOT NULL DEFAULT 4,     -- WhatsApp de comunicados por familia y mes (tanda 3)
    actualizado_por          VARCHAR(60)  NOT NULL,
    creado_en                DATETIME(6)  NOT NULL,
    creado_por               VARCHAR(60)  NOT NULL,
    actualizado_en           DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_configuracion_academica_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_configuracion_academica_horas CHECK (hora_alerta BETWEEN '07:00:00' AND '12:00:00'
        AND hora_aviso BETWEEN '09:00:00' AND '13:00:00' AND hora_aviso > hora_alerta
        AND hora_segundo_aviso BETWEEN '13:00:00' AND '18:00:00'
        AND hora_resumen_comunicados BETWEEN '12:00:00' AND '19:30:00'),
    CONSTRAINT ck_configuracion_academica_plazos CHECK (dias_justificacion BETWEEN 1 AND 10
        AND dias_registro_tardio BETWEEN 1 AND 10 AND tope_resumenes_mes BETWEEN 0 AND 20),
    CONSTRAINT ck_configuracion_academica_actor CHECK (actualizado_por NOT LIKE 'sistema%'
        AND creado_por NOT LIKE 'sistema%')
);

-- Un registro por sección y día. SOLO INSERCIÓN. registrado_bd lo pone la base (trigger): la hora del servidor de la
-- aplicación no decide qué día es (E9). fuente: DOCENTE; MARCADOR queda como puerta abierta y hoy el trigger la rechaza.
CREATE TABLE registro_asistencia (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    anio_escolar_id  BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    fecha            DATE          NOT NULL,
    origen           VARCHAR(10)   NOT NULL,   -- DEL_DIA | TARDIO
    fuente           VARCHAR(10)   NOT NULL DEFAULT 'DOCENTE',  -- DOCENTE | MARCADOR (futuro; hoy el trigger lo rechaza)
    solicitud_id     BIGINT,                   -- REGISTRO_ASISTENCIA_TARDIO aprobada (solo TARDIO)
    registrado_por   VARCHAR(60)   NOT NULL,
    sesion_id        BIGINT        NOT NULL,
    registrado_bd    DATETIME(6),
    alumnos          INT           NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_registro_asistencia_dia UNIQUE (colegio_id, seccion_id, fecha),
    CONSTRAINT uk_registro_asistencia_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_registro_asistencia_id_seccion UNIQUE (id, seccion_id),
    CONSTRAINT uk_registro_asistencia_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT fk_registro_asistencia_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_registro_asistencia_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_registro_asistencia_seccion FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_registro_asistencia_seccion_anio FOREIGN KEY (seccion_id, anio_escolar_id)
        REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_registro_asistencia_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT fk_registro_asistencia_solicitud FOREIGN KEY (solicitud_id, colegio_id)
        REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT ck_registro_asistencia_origen CHECK (origen IN ('DEL_DIA', 'TARDIO')
        AND ((origen = 'TARDIO') = (solicitud_id IS NOT NULL))),
    CONSTRAINT ck_registro_asistencia_alumnos CHECK (alumnos >= 0),
    CONSTRAINT ck_registro_asistencia_fuente CHECK (fuente IN ('DOCENTE', 'MARCADOR')),
    CONSTRAINT ck_registro_asistencia_actor CHECK (creado_por = registrado_por AND registrado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_registro_asistencia_fecha ON registro_asistencia (colegio_id, fecha);

-- Una marca por alumno y registro; corregir es INSERTAR otra que reemplaza a la vigente (reemplaza_id). SOLO INSERCIÓN.
-- Una sola sucesora por marca (E8): la historia es una cadena y la vigente es la que nadie reemplazó.
CREATE TABLE marca_asistencia (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT        NOT NULL,
    registro_id           BIGINT        NOT NULL,
    seccion_id            BIGINT        NOT NULL,   -- la del registro (el alumno puede cambiar de sección después)
    fecha                 DATE          NOT NULL,   -- la del registro (consultas por alumno y mes)
    alumno_id             BIGINT        NOT NULL,
    matricula_id          BIGINT        NOT NULL,
    estado                VARCHAR(20)   NOT NULL,   -- PRESENTE | TARDE | FALTA | FALTA_JUSTIFICADA
    hora_llegada          TIME,
    fuente                VARCHAR(10)   NOT NULL DEFAULT 'DOCENTE',  -- DOCENTE | MARCADOR (futuro; hoy el trigger lo rechaza)
    origen                VARCHAR(24)   NOT NULL,   -- REGISTRO | CORRECCION_DEL_DIA | CORRECCION_APROBADA |
                                                    -- JUSTIFICACION | REVERSION_JUSTIFICACION
    primera               BOOLEAN,                  -- TRUE en la marca del registro; NULL en las que reemplazan
    reemplaza_id          BIGINT,
    solicitud_id          BIGINT,                   -- CORRECCION_ASISTENCIA aprobada (solo CORRECCION_APROBADA)
    motivo                VARCHAR(300),             -- de la corrección (persona) o de la reversión
    motivo_justificacion  VARCHAR(12),              -- SALUD | FAMILIAR | TRAMITE | OTRO (solo JUSTIFICACION)
    nota_justificacion    VARCHAR(200),             -- opcional; se anonimiza al año (sección 3.12)
    apoderado_id          BIGINT,                   -- quien justificó
    familia_id            BIGINT,
    sesion_id             BIGINT        NOT NULL,
    creado_en             DATETIME(6)   NOT NULL,
    creado_por            VARCHAR(60)   NOT NULL,
    actualizado_en        DATETIME(6)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_marca_asistencia_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_marca_asistencia_id_alumno UNIQUE (id, alumno_id),
    CONSTRAINT uk_marca_asistencia_primera UNIQUE (registro_id, alumno_id, primera),
    CONSTRAINT uk_marca_asistencia_reemplaza UNIQUE (colegio_id, reemplaza_id),
    CONSTRAINT uk_marca_asistencia_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT fk_marca_asistencia_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_marca_asistencia_registro FOREIGN KEY (registro_id, seccion_id)
        REFERENCES registro_asistencia (id, seccion_id),
    CONSTRAINT fk_marca_asistencia_registro_colegio FOREIGN KEY (registro_id, colegio_id)
        REFERENCES registro_asistencia (id, colegio_id),
    CONSTRAINT fk_marca_asistencia_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_marca_asistencia_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_marca_asistencia_reemplaza FOREIGN KEY (reemplaza_id, alumno_id)
        REFERENCES marca_asistencia (id, alumno_id),
    CONSTRAINT fk_marca_asistencia_solicitud FOREIGN KEY (solicitud_id, colegio_id)
        REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT fk_marca_asistencia_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT fk_marca_asistencia_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_marca_asistencia_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_marca_asistencia_estado CHECK (estado IN ('PRESENTE', 'TARDE', 'FALTA', 'FALTA_JUSTIFICADA')
        AND (hora_llegada IS NULL OR estado = 'TARDE')),
    -- primera y motivo admiten NULL: cada uno con su IS NOT NULL (una REGISTRO con primera NULL, o una corrección sin
    -- motivo, pasarían el CHECK).
    CONSTRAINT ck_marca_asistencia_origen CHECK (origen IN ('REGISTRO', 'CORRECCION_DEL_DIA', 'CORRECCION_APROBADA',
        'JUSTIFICACION', 'REVERSION_JUSTIFICACION')
        AND ((origen = 'REGISTRO' AND primera IS NOT NULL AND primera AND reemplaza_id IS NULL)
            OR (origen <> 'REGISTRO' AND primera IS NULL AND reemplaza_id IS NOT NULL))
        AND ((origen = 'CORRECCION_APROBADA') = (solicitud_id IS NOT NULL))
        AND (origen NOT IN ('CORRECCION_DEL_DIA', 'CORRECCION_APROBADA', 'REVERSION_JUSTIFICACION')
            OR (motivo IS NOT NULL AND CHAR_LENGTH(TRIM(motivo)) >= 5))),
    CONSTRAINT ck_marca_asistencia_justificacion CHECK (
        (origen = 'JUSTIFICACION' AND estado = 'FALTA_JUSTIFICADA' AND apoderado_id IS NOT NULL AND familia_id IS NOT NULL
            AND motivo_justificacion IS NOT NULL AND motivo_justificacion IN ('SALUD', 'FAMILIAR', 'TRAMITE', 'OTRO'))
        OR (origen <> 'JUSTIFICACION' AND apoderado_id IS NULL AND familia_id IS NULL AND motivo_justificacion IS NULL
            AND nota_justificacion IS NULL)),
    -- A1 (auditoría en papel): una FALTA_JUSTIFICADA solo la crea la familia (JUSTIFICACION) o una solicitud aprobada.
    CONSTRAINT ck_marca_asistencia_justificada CHECK (estado <> 'FALTA_JUSTIFICADA'
        OR origen IN ('JUSTIFICACION', 'CORRECCION_APROBADA')),
    CONSTRAINT ck_marca_asistencia_fuente CHECK (fuente IN ('DOCENTE', 'MARCADOR')),
    CONSTRAINT ck_marca_asistencia_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_marca_asistencia_alumno ON marca_asistencia (colegio_id, alumno_id, fecha);
CREATE INDEX ix_marca_asistencia_registro ON marca_asistencia (colegio_id, registro_id);
CREATE INDEX ix_marca_asistencia_fecha ON marca_asistencia (colegio_id, fecha, estado);

-- H2 del diseño. Consentimiento por finalidad y canal para los avisos de asistencia y de comunicados. SOLO INSERCIÓN: vale
-- la última fila de cada apoderado, finalidad y canal. WhatsApp exige un «sí» explícito (opt-in de Meta); el correo sale
-- salvo oposición (la finalidad está en el aviso de privacidad del colegio).
CREATE TABLE consentimiento_aviso (
    id                        BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id                BIGINT        NOT NULL,
    familia_id                BIGINT        NOT NULL,
    apoderado_id              BIGINT        NOT NULL,
    finalidad                 VARCHAR(12)   NOT NULL,   -- ASISTENCIA | COMUNICADOS
    canal                     VARCHAR(10)   NOT NULL,   -- WHATSAPP | CORREO
    otorgado                  BOOLEAN       NOT NULL,
    contacto_principal        BOOLEAN       NOT NULL DEFAULT FALSE,  -- COMUNICADOS por WhatsApp: el que recibe el resumen
    origen                    VARCHAR(8)    NOT NULL,   -- PORTAL | FICHA
    evidencia                 VARCHAR(40),              -- código de la ficha de matrícula firmada (FICHA)
    aviso_privacidad_version  VARCHAR(20)   NOT NULL,
    sesion_id                 BIGINT        NOT NULL,
    creado_en                 DATETIME(6)   NOT NULL,
    creado_por                VARCHAR(60)   NOT NULL,
    actualizado_en            DATETIME(6)   NOT NULL,
    version                   BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_consentimiento_aviso_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_consentimiento_aviso_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_consentimiento_aviso_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_consentimiento_aviso_apoderado FOREIGN KEY (apoderado_id, familia_id) REFERENCES apoderado (id, familia_id),
    CONSTRAINT fk_consentimiento_aviso_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_consentimiento_aviso_valores CHECK (finalidad IN ('ASISTENCIA', 'COMUNICADOS')
        AND canal IN ('WHATSAPP', 'CORREO') AND origen IN ('PORTAL', 'FICHA')
        AND (NOT contacto_principal OR (finalidad = 'COMUNICADOS' AND canal = 'WHATSAPP' AND otorgado))),
    -- evidencia admite NULL: una FICHA sin evidencia pasaría sin su IS NOT NULL.
    CONSTRAINT ck_consentimiento_aviso_evidencia CHECK ((origen = 'FICHA' AND evidencia IS NOT NULL
            AND CHAR_LENGTH(TRIM(evidencia)) >= 3)
        OR (origen = 'PORTAL' AND evidencia IS NULL)),
    CONSTRAINT ck_consentimiento_aviso_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_consentimiento_aviso_vigente ON consentimiento_aviso (colegio_id, apoderado_id, finalidad, canal, id);

-- A7 (auditoría en papel): asignación de COBERTURA de un docente ausente, con fecha de fin (como máximo 5 días hábiles,
-- la aplicación; 10 días calendario, la base). cc_docente_de_seccion la ignora desde el día siguiente a `hasta` y
-- FinAsignaciones la termina (sistema.academico). `hasta` no está en el UPDATE de cc_negocio (1143). Una cobertura no es
-- de tutor (el tutor es uno por sección). V32 ya está publicada: no se edita.
ALTER TABLE asignacion_docente ADD COLUMN hasta DATE;
ALTER TABLE asignacion_docente ADD CONSTRAINT ck_asignacion_docente_hasta CHECK (hasta IS NULL OR tipo <> 'TUTOR');

-- A2 (auditoría en papel): marca de agua de los avisos de asistencia, una fila por colegio. Cada envío (10:30 y 16:00)
-- avisa las marcas con id mayor que `ultima_marca_id` (también las de días anteriores: lo hecho después del envío de
-- las 16:00 sale a las 10:30 del día siguiente) y la sube al id más alto que avisó. Solo la escribe cc_sistema.
CREATE TABLE aviso_asistencia_corte (
    colegio_id       BIGINT       NOT NULL PRIMARY KEY,
    ultima_marca_id  BIGINT       NOT NULL DEFAULT 0,
    creado_en        DATETIME(6)  NOT NULL,
    creado_por       VARCHAR(60)  NOT NULL,
    actualizado_en   DATETIME(6)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_aviso_asistencia_corte_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_aviso_asistencia_corte_actor CHECK (creado_por = 'sistema.asistencia' AND ultima_marca_id >= 0)
);

-- Avisos nuevos a las familias (tipos propios, plantillas propias: sección 3.6). La lista completa de V31 más los dos
-- tipos de asistencia.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA'));
CREATE INDEX ix_mensaje_tipo_fecha ON mensaje (colegio_id, tipo, creado_en);
