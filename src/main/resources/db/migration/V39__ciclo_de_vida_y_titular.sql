-- Sprint 11 · tanda 1 (docs/arquitectura/sprint-11-ciclo-colegio.md, sección 5, con la auditoría en papel de la 3.13).
-- Estados nuevos del colegio (BAJA y SUSPENDIDO; CERRADO y SUPRIMIDO se usan en la tanda 2), el cambio de estado de dos
-- operadores (también el bloqueo de la facultad del titular y la objeción del titular), la facultad del titular, quién
-- firmó el último cambio de un año escolar, el motivo de cierre de sesión por suspensión y el tipo de mensaje del código
-- de la facultad. Compatible con MySQL 8 y con H2 en modo MySQL (comprobado en los dos: paso 1 de la tanda). Nada se borra.

-- 1. colegio (H5). estado_desde: desde cuándo está en su estado (la pone la base al cambiarlo, trg_colegio_cambio).
ALTER TABLE colegio ADD COLUMN estado_desde DATETIME(6);
ALTER TABLE colegio ADD COLUMN cambio_estado_id BIGINT;                       -- el último cambio de estado aplicado
ALTER TABLE colegio ADD COLUMN suspendido_en DATETIME(6);                     -- AP-11: cuándo se aplicó la suspensión
ALTER TABLE colegio ADD COLUMN facultad_bloqueada BOOLEAN NOT NULL DEFAULT FALSE;  -- AP-2: la bloquean dos operadores
ALTER TABLE colegio ADD COLUMN facultad_cambio_id BIGINT;                     -- el BLOQUEO o DESBLOQUEO aplicado
ALTER TABLE anio_escolar ADD COLUMN actualizado_por VARCHAR(60);              -- AP-9: quién firmó el último cambio
UPDATE colegio SET estado_desde = COALESCE(produccion_desde, creado_en);
-- Con valor por defecto: el colegio de una base nueva (V1) y el que inserta el administrador lo reciben al nacer.
ALTER TABLE colegio MODIFY estado_desde DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE colegio DROP CONSTRAINT ck_colegio_estado;
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_estado CHECK (
    estado IN ('PREPARACION', 'PRODUCCION', 'BAJA', 'SUSPENDIDO', 'CERRADO', 'SUPRIMIDO')
    AND (tipo = 'COLEGIO' OR estado = 'PRODUCCION'));
ALTER TABLE colegio DROP CONSTRAINT ck_colegio_produccion;
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_produccion CHECK (
    (estado IN ('PREPARACION', 'BAJA') AND produccion_desde IS NULL)
    OR (estado IN ('PRODUCCION', 'SUSPENDIDO', 'CERRADO') AND produccion_desde IS NOT NULL AND produccion_por IS NOT NULL)
    OR estado = 'SUPRIMIDO');
-- Fuera de PREPARACION y PRODUCCION, el colegio está ahí por un cambio aprobado (la reactivación deja PRODUCCION con su
-- cambio: el CHECK no lo prohíbe).
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_cambio_estado CHECK (
    estado IN ('PREPARACION', 'PRODUCCION') OR cambio_estado_id IS NOT NULL);
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_suspendido CHECK (
    (estado IN ('SUSPENDIDO', 'CERRADO') AND suspendido_en IS NOT NULL)
    OR (estado NOT IN ('SUSPENDIDO', 'CERRADO') AND suspendido_en IS NULL)
    OR estado = 'SUPRIMIDO');
-- La facultad bloqueada lleva el cambio que la bloqueó.
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_facultad CHECK (NOT facultad_bloqueada OR facultad_cambio_id IS NOT NULL);

-- 2. cambio_estado_colegio: lo propone un operador y lo aprueba OTRO (como cambio_contacto_titular, V31). ambito separa
--    los pendientes: un bloqueo de la facultad (FACULTAD) no espera a que se resuelva una suspensión (ESTADO).
CREATE TABLE cambio_estado_colegio (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT        NOT NULL,
    plataforma_id         BIGINT        NOT NULL,
    tipo                  VARCHAR(20)   NOT NULL,   -- BAJA, SUSPENSION, REACTIVACION, BLOQUEO_FACULTAD, DESBLOQUEO_FACULTAD
    ambito                VARCHAR(8)    NOT NULL,   -- ESTADO o FACULTAD
    motivo_tipo           VARCHAR(16)   NOT NULL,
    motivo                VARCHAR(500)  NOT NULL,
    referencia_aviso      VARCHAR(120),             -- la carta o el correo al representante legal (SUSPENSION)
    estado_origen         VARCHAR(12)   NOT NULL,
    estado_destino        VARCHAR(12)   NOT NULL,
    estado                VARCHAR(10)   NOT NULL,   -- PROPUESTO, APROBADO, APLICADO, RECHAZADO, ANULADO
    pendiente             BOOLEAN,                  -- TRUE en PROPUESTO y APROBADO; si no, NULL (uno pendiente por ámbito)
    propuesto_por         VARCHAR(60)   NOT NULL,
    operador_id           BIGINT        NOT NULL,
    aprobado_por          VARCHAR(60),
    aprobador_id          BIGINT,
    aprobado_en           DATETIME(6),              -- lo pone la base
    efectiva_desde        DATETIME(6),              -- lo pone la base al aprobar (hora de Lima)
    aplicado_en           DATETIME(6),              -- lo pone la base
    resuelto_por          VARCHAR(60),              -- quien rechaza o anula
    resuelto_en           DATETIME(6),
    objecion              VARCHAR(500),             -- AP-10: la objeción del titular, con su firma
    objetado_por          VARCHAR(60),
    objetado_en           DATETIME(6),
    objecion_respuesta    VARCHAR(500),
    objecion_resuelta_por VARCHAR(60),
    objecion_resuelta_en  DATETIME(6),
    creado_en             DATETIME(6)   NOT NULL,
    creado_por            VARCHAR(60)   NOT NULL,
    actualizado_en        DATETIME(6)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_cambio_estado_colegio_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_cambio_estado_colegio_pendiente UNIQUE (colegio_id, ambito, pendiente),
    CONSTRAINT fk_cambio_estado_colegio_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_cambio_estado_colegio_plataforma FOREIGN KEY (plataforma_id) REFERENCES colegio (id),
    CONSTRAINT fk_cambio_estado_colegio_operador FOREIGN KEY (operador_id, plataforma_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_cambio_estado_colegio_aprobador FOREIGN KEY (aprobador_id, plataforma_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_cambio_estado_colegio_tipo CHECK (
        (tipo = 'BAJA' AND estado_origen = 'PREPARACION' AND estado_destino = 'BAJA'
            AND motivo_tipo IN ('PRUEBA', 'NO_ARRANCO', 'PEDIDO_COLEGIO', 'DUPLICADO', 'OTRO'))
        OR (tipo = 'SUSPENSION' AND estado_origen = 'PRODUCCION' AND estado_destino = 'SUSPENDIDO'
            AND motivo_tipo IN ('FALTA_PAGO', 'FIN_CONTRATO', 'PEDIDO_COLEGIO', 'OTRO')
            AND referencia_aviso IS NOT NULL AND CHAR_LENGTH(TRIM(referencia_aviso)) >= 5)
        OR (tipo = 'REACTIVACION' AND estado_origen = 'SUSPENDIDO' AND estado_destino = 'PRODUCCION'
            AND motivo_tipo IN ('PAGO_RECIBIDO', 'ACUERDO', 'OTRO'))
        OR (tipo IN ('BLOQUEO_FACULTAD', 'DESBLOQUEO_FACULTAD') AND estado_origen = estado_destino
            AND estado_origen IN ('PREPARACION', 'PRODUCCION') AND motivo_tipo IN ('SEGURIDAD', 'PEDIDO_COLEGIO', 'OTRO'))),
    CONSTRAINT ck_cambio_estado_colegio_ambito CHECK (
        (ambito = 'FACULTAD' AND tipo IN ('BLOQUEO_FACULTAD', 'DESBLOQUEO_FACULTAD'))
        OR (ambito = 'ESTADO' AND tipo IN ('BAJA', 'SUSPENSION', 'REACTIVACION'))),
    -- AP-10: una objeción por cambio, del titular, a una BAJA o una SUSPENSION; la resuelve un operador que no propuso.
    CONSTRAINT ck_cambio_estado_colegio_objecion CHECK (
        (objecion IS NULL AND objetado_por IS NULL AND objetado_en IS NULL AND objecion_respuesta IS NULL
            AND objecion_resuelta_por IS NULL AND objecion_resuelta_en IS NULL)
        OR (objecion IS NOT NULL AND CHAR_LENGTH(TRIM(objecion)) >= 20 AND objetado_por IS NOT NULL
            AND objetado_en IS NOT NULL AND tipo IN ('BAJA', 'SUSPENSION')
            AND ((objecion_resuelta_por IS NULL AND objecion_resuelta_en IS NULL AND objecion_respuesta IS NULL)
                OR (objecion_resuelta_por IS NOT NULL AND objecion_resuelta_por <> propuesto_por
                    AND objecion_resuelta_en IS NOT NULL AND objecion_respuesta IS NOT NULL
                    AND CHAR_LENGTH(TRIM(objecion_respuesta)) >= 20)))),
    CONSTRAINT ck_cambio_estado_colegio_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) BETWEEN 20 AND 500),
    CONSTRAINT ck_cambio_estado_colegio_estado CHECK (estado IN ('PROPUESTO', 'APROBADO', 'APLICADO', 'RECHAZADO', 'ANULADO')
        AND ((estado IN ('PROPUESTO', 'APROBADO') AND pendiente IS NOT NULL AND pendiente)
            OR (estado IN ('APLICADO', 'RECHAZADO', 'ANULADO') AND pendiente IS NULL))),
    CONSTRAINT ck_cambio_estado_colegio_aprobado CHECK (
        (estado IN ('APROBADO', 'APLICADO') AND aprobado_por IS NOT NULL AND aprobador_id IS NOT NULL
            AND aprobado_en IS NOT NULL AND efectiva_desde IS NOT NULL)
        OR (estado IN ('PROPUESTO', 'RECHAZADO') AND aprobado_por IS NULL AND aprobador_id IS NULL
            AND aprobado_en IS NULL AND efectiva_desde IS NULL)
        OR estado = 'ANULADO'),
    -- Quien aprueba no es quien propone; quien rechaza tampoco (dos operadores).
    CONSTRAINT ck_cambio_estado_colegio_otro CHECK (
        (aprobado_por IS NULL OR aprobado_por <> propuesto_por)
        AND (estado <> 'RECHAZADO' OR (resuelto_por IS NOT NULL AND resuelto_por <> propuesto_por))),
    CONSTRAINT ck_cambio_estado_colegio_aplicado CHECK ((estado = 'APLICADO' AND aplicado_en IS NOT NULL)
        OR (estado <> 'APLICADO' AND aplicado_en IS NULL)),
    CONSTRAINT ck_cambio_estado_colegio_resuelto CHECK (
        (estado IN ('RECHAZADO', 'ANULADO') AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL)
        OR (estado NOT IN ('RECHAZADO', 'ANULADO') AND resuelto_por IS NULL AND resuelto_en IS NULL)),
    CONSTRAINT ck_cambio_estado_colegio_actor CHECK (creado_por = propuesto_por AND propuesto_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_cambio_estado_colegio_estado ON cambio_estado_colegio (estado, efectiva_desde);
-- FK circular: se agrega después de crear la tabla (la FK compuesta con la PK del colegio: comprobada en H2 y MySQL).
ALTER TABLE colegio ADD CONSTRAINT fk_colegio_cambio_estado FOREIGN KEY (cambio_estado_id, id)
    REFERENCES cambio_estado_colegio (id, colegio_id);
ALTER TABLE colegio ADD CONSTRAINT fk_colegio_facultad_cambio FOREIGN KEY (facultad_cambio_id, id)
    REFERENCES cambio_estado_colegio (id, colegio_id);

-- 3. Mensajes: el código de cada uso de la facultad del titular (AP-2). Se copia la lista de V37 y se agrega uno.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA', 'COMUNICADOS_RESUMEN', 'COMUNICADO_URGENTE', 'LIBRETA_DISPONIBLE', 'LIBRETA_CORREGIDA',
    'CODIGO_FACULTAD'));

-- 4. La facultad del titular (sección 3.12 y AP-2). Solo inserción (sin GRANT de UPDATE ni DELETE para nadie); lleva
--    actualizado_en y version como toda entidad de negocio (BaseEntity), aunque nunca cambian. Cada uso apunta al mensaje
--    con el código que recibió el titular (una vez).
CREATE TABLE uso_facultad_titular (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id   BIGINT       NOT NULL,
    numero       INT          NOT NULL,   -- 1, 2, 3… por colegio (la firma lo lleva)
    titular_id   BIGINT       NOT NULL,
    titular      VARCHAR(60)  NOT NULL,
    usuario_id   BIGINT       NOT NULL,   -- la cuenta que recibe o pierde el rol
    rol          VARCHAR(30)  NOT NULL,
    accion       VARCHAR(6)   NOT NULL,
    cuenta_nueva BOOLEAN      NOT NULL,   -- TRUE si la cuenta se creó en esta misma operación
    mensaje_id   BIGINT       NOT NULL,   -- el CODIGO_FACULTAD que recibió el titular (una vez)
    creado_en    DATETIME(6)  NOT NULL,   -- lo pone la base
    creado_por   VARCHAR(60)  NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_uso_facultad_titular_numero UNIQUE (colegio_id, numero),
    CONSTRAINT uk_uso_facultad_titular_mensaje UNIQUE (mensaje_id),
    CONSTRAINT fk_uso_facultad_titular_mensaje FOREIGN KEY (mensaje_id, colegio_id) REFERENCES mensaje (id, colegio_id),
    CONSTRAINT fk_uso_facultad_titular_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_uso_facultad_titular_titular FOREIGN KEY (titular_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_uso_facultad_titular_usuario FOREIGN KEY (usuario_id, colegio_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_uso_facultad_titular_rol CHECK (rol IN ('PROMOTOR', 'DIRECTOR')),
    CONSTRAINT ck_uso_facultad_titular_accion CHECK (accion IN ('DAR', 'QUITAR') AND (accion = 'DAR' OR NOT cuenta_nueva)),
    CONSTRAINT ck_uso_facultad_titular_otro CHECK (usuario_id <> titular_id),
    CONSTRAINT ck_uso_facultad_titular_actor CHECK (creado_por = titular AND numero >= 1)
);
CREATE INDEX ix_uso_facultad_titular_usuario ON uso_facultad_titular (colegio_id, usuario_id, rol, accion, creado_en);

-- 5. Motivo de cierre de sesión por suspensión (H8): se copia la lista de V25 y se agrega uno.
ALTER TABLE sesion_usuario DROP CONSTRAINT ck_sesion_usuario_cierre;
ALTER TABLE sesion_usuario ADD CONSTRAINT ck_sesion_usuario_cierre CHECK ((cerrada_en IS NULL AND motivo_cierre IS NULL)
    OR (cerrada_en IS NOT NULL AND motivo_cierre IS NOT NULL
        AND motivo_cierre IN ('SALIO', 'VENCIO', 'OTRA_SESION', 'CUENTA_CAMBIADA', 'REINICIO', 'COLEGIO_SUSPENDIDO')));
