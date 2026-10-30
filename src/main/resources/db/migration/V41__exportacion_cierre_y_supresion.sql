-- Sprint 11 · tanda 2 (docs/arquitectura/sprint-11-ciclo-colegio.md, secciones 3.9 a 3.11 y 3.17, que prevalece; brief
-- docs/arquitectura/sprint-11/tanda-2.md). La exportación completa de los datos de un colegio (ZIP cifrado, entrega al
-- titular), el cierre (SUSPENDIDO → CERRADO, dos operadores, con la exportación), la conservación hasta una fecha que pone
-- la base, la autorización expresa del titular, la retención legal y el acta de supresión (dos operadores; la ejecuta el
-- DBA). Compatible con MySQL 8 y con H2 en modo MySQL (comprobado en los dos). Nada se borra fuera del acta.

-- 1. colegio. cerrado_en y conservar_hasta son COLUMNAS GENERADAS (desviación del diseño, que las hacía escribir por el
--    trigger): nadie las escribe, ni la aplicación ni el DBA (cc_app y cc_sistema no tienen GRANT: 1143; el DBA: 3105), y
--    H2, que no tiene triggers, calcula lo mismo. Fórmulas de AP-7 y AP-11: con la autorización expresa del titular, la
--    suspensión aplicada + 12 meses exactos; sin ella, el cierre + 30 días; una BAJA, + 30 días.
ALTER TABLE colegio ADD COLUMN conservacion_autorizada_por VARCHAR(60);     -- AP-11 y AP2-9: el titular, con su firma
ALTER TABLE colegio ADD COLUMN conservacion_autorizada_en DATETIME(6);      -- lo pone la base
ALTER TABLE colegio ADD COLUMN retencion_legal_motivo VARCHAR(500);         -- AP2-10: el pedido escrito de una autoridad
ALTER TABLE colegio ADD COLUMN retencion_legal_desde DATETIME(6);           -- lo pone la base
ALTER TABLE colegio ADD COLUMN retencion_legal_por VARCHAR(60);             -- el operador que la puso (otro la quita)
ALTER TABLE colegio ADD COLUMN acta_supresion_id BIGINT;                    -- AP2-7: el acta EJECUTADA del SUPRIMIDO
ALTER TABLE colegio ADD COLUMN cerrado_en DATETIME(6) GENERATED ALWAYS AS (
    CASE WHEN estado = 'CERRADO' THEN estado_desde END);
ALTER TABLE colegio ADD COLUMN conservar_hasta DATE GENERATED ALWAYS AS (
    CASE WHEN estado = 'CERRADO' AND conservacion_autorizada_en IS NOT NULL AND suspendido_en IS NOT NULL
            THEN CAST(TIMESTAMPADD(MONTH, 12, suspendido_en) AS DATE)
        WHEN estado = 'CERRADO' THEN CAST(TIMESTAMPADD(DAY, 30, estado_desde) AS DATE)
        WHEN estado = 'BAJA' THEN CAST(TIMESTAMPADD(DAY, 30, estado_desde) AS DATE) END);
-- El CHECK exacto de AP-7 (cada rama con IS NOT NULL en lo que compara, AP2-7): con las columnas generadas se cumple por
-- construcción y queda como segunda línea si alguien cambia la fórmula.
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_conservar CHECK (
    (estado NOT IN ('CERRADO', 'BAJA', 'SUPRIMIDO') AND conservar_hasta IS NULL)
    OR (estado = 'CERRADO' AND conservacion_autorizada_en IS NOT NULL AND suspendido_en IS NOT NULL
        AND conservar_hasta IS NOT NULL AND conservar_hasta = CAST(TIMESTAMPADD(MONTH, 12, suspendido_en) AS DATE))
    OR (estado = 'CERRADO' AND conservacion_autorizada_en IS NULL AND cerrado_en IS NOT NULL
        AND conservar_hasta IS NOT NULL AND conservar_hasta = CAST(TIMESTAMPADD(DAY, 30, cerrado_en) AS DATE))
    OR (estado = 'BAJA' AND estado_desde IS NOT NULL AND conservar_hasta IS NOT NULL
        AND conservar_hasta = CAST(TIMESTAMPADD(DAY, 30, estado_desde) AS DATE))
    OR (estado = 'SUPRIMIDO' AND acta_supresion_id IS NOT NULL));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_conservacion CHECK (
    (conservacion_autorizada_por IS NULL AND conservacion_autorizada_en IS NULL)
    OR (conservacion_autorizada_por IS NOT NULL AND conservacion_autorizada_en IS NOT NULL));
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_retencion CHECK (
    (retencion_legal_motivo IS NULL AND retencion_legal_desde IS NULL AND retencion_legal_por IS NULL)
    OR (retencion_legal_motivo IS NOT NULL AND CHAR_LENGTH(TRIM(retencion_legal_motivo)) >= 20
        AND retencion_legal_desde IS NOT NULL AND retencion_legal_por IS NOT NULL));

-- 2. exportacion_colegio (3.9; AP-12, AP2-2, AP2-4 y AP2-13). La genera sistema.exportacion (o un operador a pedido
--    escrito del colegio, con su firma). Una GENERANDO o LISTA por colegio (UNIQUE con vigente). La clave del ZIP se
--    guarda cifrada con CC_CLAVE_CONECTORES hasta que el titular la ve; después, NULL. El enlace y el código de la entrega
--    se guardan solo como SHA-256. Las huellas del ZIP y del manifiesto no cambian una vez puestas (trigger).
CREATE TABLE exportacion_colegio (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id          BIGINT        NOT NULL,
    plataforma_id       BIGINT        NOT NULL,
    cambio_estado_id    BIGINT,                   -- la SUSPENSION o el CIERRE que la pidió (nulo en PEDIDO)
    motivo              VARCHAR(12)   NOT NULL,   -- SUSPENSION, CIERRE, PEDIDO
    estado              VARCHAR(10)   NOT NULL,   -- GENERANDO, LISTA, ENTREGADA, VENCIDA, FALLIDA
    vigente             BOOLEAN,                  -- TRUE en GENERANDO y LISTA; si no, NULL
    archivo             VARCHAR(200),             -- {ruc}-{aaaammdd}-exportacion.zip (en CC_DIR_EXPORTACIONES)
    archivos            INT,
    filas               BIGINT,
    bytes               BIGINT,
    sha256_zip          VARCHAR(64),
    sha256_manifiesto   VARCHAR(64),
    clave_cifrada       VARBINARY(512),           -- NULL después de verla (o al vencer)
    clave_version       INT,
    clave_vista_en      DATETIME(6),
    token_hash          VARCHAR(64),              -- SHA-256 del enlace de la entrega (solo por correo)
    token_vence_en      DATETIME(6),
    dni_confirmado_en   DATETIME(6),
    codigo_hash         VARCHAR(64),              -- SHA-256 del código (solo por WhatsApp)
    codigo_vence_en     DATETIME(6),
    intentos_fallidos   INT           NOT NULL DEFAULT 0,
    descargas           INT           NOT NULL DEFAULT 0,
    detalle_fallo       VARCHAR(500),
    generada_en         DATETIME(6),              -- lo pone la base
    vence_en            DATETIME(6),              -- lo pone la base: + 30 días
    entregada_en        DATETIME(6),              -- lo pone la base
    creado_en           DATETIME(6)   NOT NULL,
    creado_por          VARCHAR(60)   NOT NULL,
    actualizado_en      DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exportacion_colegio_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_exportacion_colegio_vigente UNIQUE (colegio_id, vigente),
    CONSTRAINT uk_exportacion_colegio_token UNIQUE (token_hash),
    CONSTRAINT fk_exportacion_colegio_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_exportacion_colegio_plataforma FOREIGN KEY (plataforma_id) REFERENCES colegio (id),
    CONSTRAINT fk_exportacion_colegio_cambio FOREIGN KEY (cambio_estado_id, colegio_id)
        REFERENCES cambio_estado_colegio (id, colegio_id),
    CONSTRAINT ck_exportacion_colegio_motivo CHECK (
        (motivo IN ('SUSPENSION', 'CIERRE') AND cambio_estado_id IS NOT NULL)
        OR (motivo = 'PEDIDO' AND cambio_estado_id IS NULL)),
    CONSTRAINT ck_exportacion_colegio_estado CHECK (estado IN ('GENERANDO', 'LISTA', 'ENTREGADA', 'VENCIDA', 'FALLIDA')
        AND ((estado IN ('GENERANDO', 'LISTA') AND vigente IS NOT NULL AND vigente)
            OR (estado IN ('ENTREGADA', 'VENCIDA', 'FALLIDA') AND vigente IS NULL))),
    CONSTRAINT ck_exportacion_colegio_huellas CHECK (
        (estado IN ('GENERANDO', 'FALLIDA') AND sha256_zip IS NULL AND sha256_manifiesto IS NULL)
        OR (estado IN ('LISTA', 'ENTREGADA', 'VENCIDA') AND sha256_zip IS NOT NULL AND CHAR_LENGTH(sha256_zip) = 64
            AND sha256_manifiesto IS NOT NULL AND CHAR_LENGTH(sha256_manifiesto) = 64 AND archivo IS NOT NULL
            AND generada_en IS NOT NULL AND vence_en IS NOT NULL)),
    CONSTRAINT ck_exportacion_colegio_fallida CHECK (estado <> 'FALLIDA'
        OR (detalle_fallo IS NOT NULL AND CHAR_LENGTH(TRIM(detalle_fallo)) >= 10)),
    CONSTRAINT ck_exportacion_colegio_entregada CHECK (
        (estado = 'ENTREGADA' AND entregada_en IS NOT NULL AND descargas >= 1)
        OR (estado IN ('GENERANDO', 'LISTA', 'FALLIDA') AND entregada_en IS NULL)
        OR estado = 'VENCIDA'),
    CONSTRAINT ck_exportacion_colegio_contadores CHECK (descargas BETWEEN 0 AND 3 AND intentos_fallidos BETWEEN 0 AND 5),
    CONSTRAINT ck_exportacion_colegio_clave CHECK ((clave_cifrada IS NULL) OR (clave_version IS NOT NULL
        AND estado IN ('GENERANDO', 'LISTA', 'ENTREGADA')))
);
CREATE INDEX ix_exportacion_colegio_estado ON exportacion_colegio (estado, vence_en);

-- 3. descarga_exportacion (AP2-13): cada descarga del ZIP, hasta 3 (solo inserción). La IP truncada como la bitácora.
CREATE TABLE descarga_exportacion (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exportacion_id  BIGINT       NOT NULL,
    colegio_id      BIGINT       NOT NULL,
    numero          INT          NOT NULL,   -- 1, 2 o 3
    ip              VARCHAR(45),
    creado_en       DATETIME(6)  NOT NULL,
    creado_por      VARCHAR(60)  NOT NULL,
    actualizado_en  DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_descarga_exportacion_numero UNIQUE (exportacion_id, numero),
    CONSTRAINT fk_descarga_exportacion_exportacion FOREIGN KEY (exportacion_id, colegio_id)
        REFERENCES exportacion_colegio (id, colegio_id),
    CONSTRAINT ck_descarga_exportacion_numero CHECK (numero BETWEEN 1 AND 3)
);

-- 4. cambio_estado_colegio: el CIERRE (SUSPENDIDO → CERRADO), con su exportación. Se copian las listas de V39.
ALTER TABLE cambio_estado_colegio ADD COLUMN exportacion_id BIGINT;
ALTER TABLE cambio_estado_colegio ADD CONSTRAINT fk_cambio_estado_colegio_exportacion FOREIGN KEY (exportacion_id, colegio_id)
    REFERENCES exportacion_colegio (id, colegio_id);
ALTER TABLE cambio_estado_colegio DROP CONSTRAINT ck_cambio_estado_colegio_tipo;
ALTER TABLE cambio_estado_colegio ADD CONSTRAINT ck_cambio_estado_colegio_tipo CHECK (
    (tipo = 'BAJA' AND estado_origen = 'PREPARACION' AND estado_destino = 'BAJA'
        AND motivo_tipo IN ('PRUEBA', 'NO_ARRANCO', 'PEDIDO_COLEGIO', 'DUPLICADO', 'OTRO'))
    OR (tipo = 'SUSPENSION' AND estado_origen = 'PRODUCCION' AND estado_destino = 'SUSPENDIDO'
        AND motivo_tipo IN ('FALTA_PAGO', 'FIN_CONTRATO', 'PEDIDO_COLEGIO', 'OTRO')
        AND referencia_aviso IS NOT NULL AND CHAR_LENGTH(TRIM(referencia_aviso)) >= 5)
    OR (tipo = 'REACTIVACION' AND estado_origen = 'SUSPENDIDO' AND estado_destino = 'PRODUCCION'
        AND motivo_tipo IN ('PAGO_RECIBIDO', 'ACUERDO', 'OTRO'))
    OR (tipo IN ('BLOQUEO_FACULTAD', 'DESBLOQUEO_FACULTAD') AND estado_origen = estado_destino
        AND estado_origen IN ('PREPARACION', 'PRODUCCION') AND motivo_tipo IN ('SEGURIDAD', 'PEDIDO_COLEGIO', 'OTRO'))
    OR (tipo = 'CIERRE' AND estado_origen = 'SUSPENDIDO' AND estado_destino = 'CERRADO'
        AND motivo_tipo IN ('FIN_CONTRATO', 'FALTA_PAGO', 'PEDIDO_COLEGIO', 'OTRO') AND exportacion_id IS NOT NULL));
ALTER TABLE cambio_estado_colegio DROP CONSTRAINT ck_cambio_estado_colegio_ambito;
ALTER TABLE cambio_estado_colegio ADD CONSTRAINT ck_cambio_estado_colegio_ambito CHECK (
    (ambito = 'FACULTAD' AND tipo IN ('BLOQUEO_FACULTAD', 'DESBLOQUEO_FACULTAD'))
    OR (ambito = 'ESTADO' AND tipo IN ('BAJA', 'SUSPENSION', 'REACTIVACION', 'CIERRE')));

-- 5. acta_supresion (3.11; AP-14, AP2-5, AP2-8, AP2-10 y AP2-12). Una por colegio. La PROPONE un operador, la APRUEBA
--    otro; el DBA la pasa a EJECUTANDO (la base guarda su conexión, el vencimiento de 4 horas y la huella de los eventos
--    del colegio, encadenada por secuencia), corre suprimir.sql y la pasa a EJECUTADA con los conteos.
CREATE TABLE acta_supresion (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT        NOT NULL,
    plataforma_id         BIGINT        NOT NULL,
    tipo                  VARCHAR(8)    NOT NULL,   -- BAJA o CIERRE
    estado                VARCHAR(10)   NOT NULL,   -- PROPUESTA, APROBADA, EJECUTANDO, EJECUTADA
    motivo                VARCHAR(500)  NOT NULL,
    propuesto_por         VARCHAR(60)   NOT NULL,
    operador_id           BIGINT        NOT NULL,
    aprobado_por          VARCHAR(60),
    aprobador_id          BIGINT,
    aprobado_en           DATETIME(6),              -- lo pone la base
    inicio_plazo          DATETIME(6)   NOT NULL,   -- AP2-12: el aplicado_en de la SUSPENSION o la BAJA (la base)
    respaldos_vencen_el   DATE          NOT NULL,   -- cuándo vence el último respaldo con datos del colegio
    cadena_verificada_en  DATETIME(6),              -- AP2-8: la verificación completa de sistema.auditoria
    ejecutado_por         VARCHAR(60),
    conexion_id           BIGINT,                   -- AP2-5: el CONNECTION_ID() de quien la ejecuta (la base)
    ejecutando_desde      DATETIME(6),
    ejecutando_vence_en   DATETIME(6),              -- + 4 horas (la base)
    huella_eventos        VARCHAR(64),              -- AP2-8: la calcula la base al pasar a EJECUTANDO
    ultima_secuencia      BIGINT,
    filas_suprimidas      VARCHAR(8000),            -- JSON: filas por tabla
    eventos_tachados      INT,
    ejecutada_en          DATETIME(6),              -- lo pone la base
    creado_en             DATETIME(6)   NOT NULL,
    creado_por            VARCHAR(60)   NOT NULL,
    actualizado_en        DATETIME(6)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_acta_supresion_colegio UNIQUE (colegio_id),
    CONSTRAINT uk_acta_supresion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_acta_supresion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_acta_supresion_plataforma FOREIGN KEY (plataforma_id) REFERENCES colegio (id),
    CONSTRAINT fk_acta_supresion_operador FOREIGN KEY (operador_id, plataforma_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT fk_acta_supresion_aprobador FOREIGN KEY (aprobador_id, plataforma_id) REFERENCES usuario (id, colegio_id),
    CONSTRAINT ck_acta_supresion_tipo CHECK (tipo IN ('BAJA', 'CIERRE')),
    CONSTRAINT ck_acta_supresion_estado CHECK (estado IN ('PROPUESTA', 'APROBADA', 'EJECUTANDO', 'EJECUTADA')),
    CONSTRAINT ck_acta_supresion_motivo CHECK (CHAR_LENGTH(TRIM(motivo)) BETWEEN 20 AND 500),
    -- Dos operadores: quien aprueba no es quien propone.
    CONSTRAINT ck_acta_supresion_otro CHECK (aprobado_por IS NULL OR aprobado_por <> propuesto_por),
    CONSTRAINT ck_acta_supresion_aprobada CHECK (
        (estado = 'PROPUESTA' AND aprobado_por IS NULL AND aprobador_id IS NULL AND aprobado_en IS NULL)
        OR (estado <> 'PROPUESTA' AND aprobado_por IS NOT NULL AND aprobador_id IS NOT NULL AND aprobado_en IS NOT NULL)),
    CONSTRAINT ck_acta_supresion_ejecucion CHECK (
        (estado IN ('PROPUESTA', 'APROBADA') AND ejecutado_por IS NULL AND ejecutando_desde IS NULL
            AND huella_eventos IS NULL AND ejecutada_en IS NULL AND filas_suprimidas IS NULL)
        OR (estado = 'EJECUTANDO' AND ejecutado_por IS NOT NULL AND ejecutada_en IS NULL)
        OR (estado = 'EJECUTADA' AND ejecutado_por IS NOT NULL AND filas_suprimidas IS NOT NULL
            AND eventos_tachados IS NOT NULL)),
    -- AP2-12: los respaldos con datos del colegio no pasan de 24 meses desde el inicio del plazo.
    CONSTRAINT ck_acta_supresion_respaldos CHECK (respaldos_vencen_el IS NOT NULL AND inicio_plazo IS NOT NULL
        AND respaldos_vencen_el <= CAST(TIMESTAMPADD(MONTH, 24, inicio_plazo) AS DATE)),
    CONSTRAINT ck_acta_supresion_actor CHECK (creado_por = propuesto_por AND propuesto_por NOT LIKE 'sistema%')
);
ALTER TABLE colegio ADD CONSTRAINT fk_colegio_acta_supresion FOREIGN KEY (acta_supresion_id, id)
    REFERENCES acta_supresion (id, colegio_id);

-- 6. Mensajes de la entrega (AP2-4): el enlace, solo por correo (EXPORTACION_ENLACE: la base no lo ve; lo deriva el envío)
--    y el código, solo por WhatsApp (CODIGO_EXPORTACION: guarda solo su SHA-256 y la huella de la exportación). Se copian
--    las listas de V40.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA',
    'CONECTOR_VIGENTE', 'COLEGIO_EN_PRODUCCION', 'AVISO_ALTA', 'CORREO_CONTROL', 'ASISTENCIA_DIA',
    'ASISTENCIA_CORREGIDA', 'COMUNICADOS_RESUMEN', 'COMUNICADO_URGENTE', 'LIBRETA_DISPONIBLE', 'LIBRETA_CORREGIDA',
    'CODIGO_FACULTAD', 'CODIGO_PROMOTORIA', 'CODIGO_CONTACTO', 'EXPORTACION_ENLACE', 'CODIGO_EXPORTACION'));
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_codigo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_codigo CHECK (
    (tipo IN ('CODIGO_PROMOTORIA', 'CODIGO_CONTACTO', 'CODIGO_EXPORTACION') AND codigo_sha256 IS NOT NULL
        AND CHAR_LENGTH(codigo_sha256) = 64 AND huella_accion IS NOT NULL AND CHAR_LENGTH(huella_accion) = 64)
    OR (tipo NOT IN ('CODIGO_PROMOTORIA', 'CODIGO_CONTACTO', 'CODIGO_EXPORTACION') AND codigo_sha256 IS NULL
        AND huella_accion IS NULL));
