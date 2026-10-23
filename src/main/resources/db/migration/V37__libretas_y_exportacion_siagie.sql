-- Sprint 10 · tanda 3 (docs/arquitectura/sprint-10-notas-libretas-siagie.md, secciones 3.7 a 3.10, 3.13 y 5; brief
-- docs/arquitectura/sprint-10/tanda-3.md). Libretas emitidas como FOTO inmutable (marcas de agua, cabecera congelada y
-- huella del contenido: el PDF se regenera igual siempre y no se guarda), su entrega (portal, impresa o en la entrevista),
-- la exportación para el SIAGIE (de la libreta vigente de cada matrícula, A6), la delegación de esa exportación a una
-- persona de Administración (M12, decisión 189 ajustada) y los avisos a las familias. La libreta NUNCA se retiene por
-- deuda: se emite para toda matrícula ACTIVA o RESERVADA (C1). Nada se borra. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Lección de V32 a V36: un CHECK que evalúa NULL PASA; toda columna que admite NULL se compara con IS NOT NULL.

-- SOLO INSERCIÓN y SOLO cc_sistema (sistema.libretas): cc_negocio no tiene GRANT sobre esta tabla (E14).
-- Versión 1 = lo informado al cierre (marca de agua hasta el cerrado_en del periodo); la siguiente, por cada corrección o
-- nota tardía aprobada (CORRECCION) o por una reemisión aprobada por Promotoría tras una huella que no coincide
-- (REEMISION, A3). La cabecera se congela en la foto y entra en la huella.
CREATE TABLE libreta_emitida (
    id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id             BIGINT        NOT NULL,
    anio_escolar_id        BIGINT        NOT NULL,
    periodo_id             BIGINT        NOT NULL,
    seccion_id             BIGINT        NOT NULL,
    matricula_id           BIGINT        NOT NULL,
    alumno_id              BIGINT        NOT NULL,
    numero_version         INT           NOT NULL,
    reemplaza_id           BIGINT,
    motivo                 VARCHAR(12)   NOT NULL,   -- CIERRE | CORRECCION | REEMISION
    solicitud_id           BIGINT,                   -- la REEMISION_LIBRETA aprobada por Promotoría
    hasta_calificacion_id  BIGINT        NOT NULL,   -- marca de agua de las notas (0 si no hay ninguna)
    hasta_marca_id         BIGINT        NOT NULL,   -- marca de agua de la asistencia (0 si no hay ninguna)
    hasta_exoneracion_id   BIGINT        NOT NULL,   -- marca de agua de las exoneraciones (0 si no hay ninguna)
    alumno_nombre          VARCHAR(190)  NOT NULL,
    alumno_codigo          VARCHAR(20)   NOT NULL,   -- código de estudiante; el documento solo si no hay código (M9)
    tipo_codigo            VARCHAR(18)   NOT NULL,   -- CODIGO_ESTUDIANTE | DNI | CE | PASAPORTE
    codigo_modular         VARCHAR(9),
    dre                    VARCHAR(80),
    ugel                   VARCHAR(80),
    tutor_nombre           VARCHAR(190),
    director_nombre        VARCHAR(190)  NOT NULL,   -- quien cerró el periodo
    huella_contenido       VARCHAR(64)   NOT NULL,
    cambios                VARCHAR(1000),            -- «Matemática · Resuelve problemas de cantidad: B → A» (sin conclusiones)
    registrado_bd          DATETIME(6),
    creado_en              DATETIME(6)   NOT NULL,
    creado_por             VARCHAR(60)   NOT NULL,
    actualizado_en         DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_libreta_emitida_version UNIQUE (periodo_id, matricula_id, numero_version),
    CONSTRAINT uk_libreta_emitida_reemplaza UNIQUE (reemplaza_id),
    CONSTRAINT uk_libreta_emitida_solicitud UNIQUE (colegio_id, solicitud_id),
    CONSTRAINT uk_libreta_emitida_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_libreta_emitida_periodo FOREIGN KEY (periodo_id, anio_escolar_id) REFERENCES periodo_evaluacion (id, anio_escolar_id),
    CONSTRAINT fk_libreta_emitida_periodo_colegio FOREIGN KEY (periodo_id, colegio_id) REFERENCES periodo_evaluacion (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_seccion FOREIGN KEY (seccion_id, anio_escolar_id) REFERENCES seccion (id, anio_escolar_id),
    CONSTRAINT fk_libreta_emitida_seccion_colegio FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_alumno FOREIGN KEY (alumno_id, colegio_id) REFERENCES alumno (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_reemplaza FOREIGN KEY (reemplaza_id, colegio_id) REFERENCES libreta_emitida (id, colegio_id),
    CONSTRAINT fk_libreta_emitida_solicitud FOREIGN KEY (solicitud_id, colegio_id) REFERENCES solicitud_cambio (id, colegio_id),
    CONSTRAINT ck_libreta_emitida_valores CHECK (numero_version BETWEEN 1 AND 999 AND hasta_calificacion_id >= 0
        AND hasta_marca_id >= 0 AND hasta_exoneracion_id >= 0 AND REGEXP_LIKE(huella_contenido, '^[0-9a-f]{64}$', 'c')
        AND tipo_codigo IN ('CODIGO_ESTUDIANTE', 'DNI', 'CE', 'PASAPORTE') AND CHAR_LENGTH(TRIM(alumno_nombre)) >= 3
        AND CHAR_LENGTH(TRIM(alumno_codigo)) >= 6 AND CHAR_LENGTH(TRIM(director_nombre)) >= 3
        AND creado_por = 'sistema.libretas'),
    -- La cadena (cambios y solicitud_id admiten NULL: cada rama los nombra con IS NULL o IS NOT NULL).
    CONSTRAINT ck_libreta_emitida_cadena CHECK ((numero_version = 1 AND reemplaza_id IS NULL AND motivo = 'CIERRE'
            AND cambios IS NULL AND solicitud_id IS NULL)
        OR (numero_version > 1 AND reemplaza_id IS NOT NULL AND cambios IS NOT NULL AND CHAR_LENGTH(TRIM(cambios)) >= 5
            AND ((motivo = 'CORRECCION' AND solicitud_id IS NULL) OR (motivo = 'REEMISION' AND solicitud_id IS NOT NULL))))
);
CREATE INDEX ix_libreta_emitida_alumno ON libreta_emitida (colegio_id, alumno_id, periodo_id);
CREATE INDEX ix_libreta_emitida_seccion ON libreta_emitida (colegio_id, periodo_id, seccion_id);

-- Constancia de entrega (ante un reclamo en INDECOPI: el colegio ENTREGÓ la libreta). SOLO INSERCIÓN. PORTAL: un
-- apoderado activo de la familia con su cuenta y su sesión. IMPRESA: Dirección o el tutor vigente de la sección (M9), con
-- su sesión. ENTREVISTA: igual que IMPRESA y solo en Inicial.
CREATE TABLE entrega_libreta (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    libreta_id       BIGINT        NOT NULL,
    canal            VARCHAR(10)   NOT NULL,   -- PORTAL | IMPRESA | ENTREVISTA
    apoderado_id     BIGINT,
    sesion_id        BIGINT        NOT NULL,
    registrado_bd    DATETIME(6),
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_entrega_libreta_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_entrega_libreta_libreta FOREIGN KEY (libreta_id, colegio_id) REFERENCES libreta_emitida (id, colegio_id),
    CONSTRAINT fk_entrega_libreta_apoderado FOREIGN KEY (apoderado_id, colegio_id) REFERENCES apoderado (id, colegio_id),
    CONSTRAINT fk_entrega_libreta_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_entrega_libreta_canal CHECK ((canal = 'PORTAL' AND apoderado_id IS NOT NULL)
        OR (canal IN ('IMPRESA', 'ENTREVISTA') AND apoderado_id IS NULL)),
    CONSTRAINT ck_entrega_libreta_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_entrega_libreta_libreta ON entrega_libreta (colegio_id, libreta_id);

-- M12 (decisión 189 ajustada por el coordinador): Dirección delega con su firma la exportación para el SIAGIE a UNA cuenta
-- activa del personal con ADMINISTRACION, sin CAJA ni DOCENTE, por hasta 15 días. SOLO INSERCIÓN: vence sola (desactivar
-- la cuenta la corta, porque el trigger de la exportación exige la cuenta activa).
CREATE TABLE delegacion_siagie (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    usuario_id       BIGINT        NOT NULL,   -- la persona delegada
    desde            DATE          NOT NULL,
    hasta            DATE          NOT NULL,
    autorizado_por   VARCHAR(60)   NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_delegacion_siagie_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_delegacion_siagie_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_delegacion_siagie_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_delegacion_siagie_valores CHECK (desde <= hasta AND creado_por = autorizado_por
        AND creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_delegacion_siagie_usuario ON delegacion_siagie (colegio_id, usuario_id, hasta);

-- Cada descarga de la exportación para el SIAGIE (sin datos de alumnos: el archivo no se guarda). SOLO INSERCIÓN.
-- huella_libretas (A6): SHA-256 de los «id:huella» de las libretas vigentes usadas, en orden; lo calcula la aplicación.
CREATE TABLE exportacion_calificaciones (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    periodo_id       BIGINT        NOT NULL,
    seccion_id       BIGINT        NOT NULL,
    perfil           VARCHAR(40)   NOT NULL,
    filas            INT           NOT NULL,
    huella_libretas  VARCHAR(64)   NOT NULL,
    codigo           VARCHAR(16)   NOT NULL,
    sha256           VARCHAR(64)   NOT NULL,
    sesion_id        BIGINT        NOT NULL,
    registrado_bd    DATETIME(6),
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exportacion_calificaciones_codigo UNIQUE (colegio_id, codigo),
    CONSTRAINT fk_exportacion_calificaciones_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_exportacion_calificaciones_periodo FOREIGN KEY (periodo_id, colegio_id)
        REFERENCES periodo_evaluacion (id, colegio_id),
    CONSTRAINT fk_exportacion_calificaciones_seccion FOREIGN KEY (seccion_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_exportacion_calificaciones_sesion FOREIGN KEY (sesion_id, colegio_id) REFERENCES sesion_usuario (id, colegio_id),
    CONSTRAINT ck_exportacion_calificaciones_valores CHECK (filas >= 0
        AND REGEXP_LIKE(huella_libretas, '^[0-9a-f]{64}$', 'c') AND REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c')
        AND REGEXP_LIKE(codigo, '^[A-Z0-9-]{6,16}$', 'c') AND creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_exportacion_calificaciones_persona ON exportacion_calificaciones (colegio_id, creado_por, creado_en);

-- Consentimiento: finalidad nueva LIBRETAS (decisión 180). El resto del CHECK, copiado de V33 (V34 a V36 no lo cambiaron).
ALTER TABLE consentimiento_aviso DROP CONSTRAINT ck_consentimiento_aviso_valores;
ALTER TABLE consentimiento_aviso ADD CONSTRAINT ck_consentimiento_aviso_valores CHECK (
    finalidad IN ('ASISTENCIA', 'COMUNICADOS', 'LIBRETAS')
    AND canal IN ('WHATSAPP', 'CORREO') AND origen IN ('PORTAL', 'FICHA')
    AND (NOT contacto_principal OR (finalidad = 'COMUNICADOS' AND canal = 'WHATSAPP' AND otorgado)));

-- Mensajes: la lista completa de V34 (la última que la cambió) y los dos tipos nuevos.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA', 'COMUNICADOS_RESUMEN', 'COMUNICADO_URGENTE', 'LIBRETA_DISPONIBLE', 'LIBRETA_CORREGIDA'));
