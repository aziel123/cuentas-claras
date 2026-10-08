-- Sprint 6 · tanda 2. Foto del resumen diario (solo inserción; la escribe sistema.panel y, en MySQL, el trigger
-- trg_resumen_diario_registro la compara con los libros), mensajes nuevos para Promotoría (el resumen y las alertas al
-- celular) y contacto del personal que solo cambia con su solicitud aprobada (trg_usuario_contacto).
CREATE TABLE resumen_diario (
    id                      BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id              BIGINT         NOT NULL,
    fecha                   DATE           NOT NULL,
    cortado_en              DATETIME(6)    NOT NULL,
    cobrado_total           DECIMAL(12,2)  NOT NULL,
    pagos_cantidad          INT            NOT NULL,
    cobrado_efectivo        DECIMAL(12,2)  NOT NULL,
    pagos_efectivo          INT            NOT NULL,
    cobrado_mes             DECIMAL(12,2)  NOT NULL,
    deuda_vencida           DECIMAL(12,2)  NOT NULL,
    familias_morosas        INT            NOT NULL,
    cajas_sin_cerrar        INT            NOT NULL,
    cierres_con_diferencia  INT            NOT NULL,
    solicitudes_pendientes  INT            NOT NULL,
    alertas_criticas        INT            NOT NULL,
    avisos_familias         INT            NOT NULL,
    avisos_entregados       INT            NOT NULL,
    huella_secuencia        BIGINT,
    huella_codigo           VARCHAR(16),
    creado_en               DATETIME(6)    NOT NULL,
    creado_por              VARCHAR(60)    NOT NULL,
    actualizado_en          DATETIME(6)    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_resumen_diario UNIQUE (colegio_id, fecha),
    CONSTRAINT uk_resumen_diario_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_resumen_diario_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_resumen_diario_actor CHECK (creado_por = 'sistema.panel'),
    CONSTRAINT ck_resumen_diario_cifras CHECK (cobrado_total >= 0 AND cobrado_efectivo >= 0
        AND cobrado_efectivo <= cobrado_total AND cobrado_total <= cobrado_mes AND deuda_vencida >= 0
        AND pagos_cantidad >= 0 AND pagos_efectivo >= 0 AND pagos_efectivo <= pagos_cantidad
        AND familias_morosas >= 0 AND cajas_sin_cerrar >= 0 AND cierres_con_diferencia >= 0
        AND solicitudes_pendientes >= 0 AND alertas_criticas >= 0
        AND avisos_familias >= 0 AND avisos_entregados >= 0 AND avisos_entregados <= avisos_familias),
    CONSTRAINT ck_resumen_diario_huella CHECK ((huella_secuencia IS NULL AND huella_codigo IS NULL)
        OR (huella_secuencia IS NOT NULL AND huella_secuencia >= 1 AND huella_codigo IS NOT NULL
            AND REGEXP_LIKE(huella_codigo, '^[0-9a-f]{16}$', 'c')))
);

-- Mensajes nuevos: el resumen diario y las alertas a Promotoría (y a Dirección, las anulaciones por aprobar).
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_tipo;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('PAGO_REGISTRADO', 'PAGO_ANULADO',
    'DESCUENTO_APROBADO', 'CONTACTO_CAMBIADO', 'ACTIVACION_CUENTA', 'HUELLA_BITACORA', 'RECORDATORIO_VENCIMIENTO',
    'CUOTA_VENCIDA', 'RENOVACION_MATRICULA', 'RENOVACION_REGISTRADA', 'AVISO_ATENDIDO', 'VERIFICACION_CONTACTO',
    'APODERADO_AGREGADO', 'CONTACTO_POR_VERIFICAR', 'FERIADO_PROPUESTO', 'RESUMEN_DIARIO', 'ALERTA_PROMOTORIA'));
-- El correo externo (lo escribe solo el DBA en configuracion_bd) recibe la huella y, desde ahora, el resumen.
ALTER TABLE mensaje DROP CONSTRAINT ck_mensaje_destinatario;
ALTER TABLE mensaje ADD CONSTRAINT ck_mensaje_destinatario CHECK (
    (destinatario_tipo = 'APODERADO' AND apoderado_id IS NOT NULL AND familia_id IS NOT NULL AND usuario_id IS NULL)
    OR (destinatario_tipo = 'USUARIO' AND usuario_id IS NOT NULL AND apoderado_id IS NULL AND familia_id IS NULL)
    OR (destinatario_tipo = 'EXTERNO' AND usuario_id IS NULL AND apoderado_id IS NULL AND familia_id IS NULL
        AND tipo IN ('HUELLA_BITACORA', 'RESUMEN_DIARIO') AND canal = 'CORREO'));
CREATE INDEX ix_mensaje_usuario ON mensaje (colegio_id, usuario_id, tipo, creado_en);

-- Hallazgo 5: el celular o el correo del personal cambian solo con SU solicitud aprobada, una vez.
ALTER TABLE usuario ADD COLUMN contacto_solicitud_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_contacto_solicitud UNIQUE (colegio_id, contacto_solicitud_id);
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_contacto_solicitud FOREIGN KEY (contacto_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);
