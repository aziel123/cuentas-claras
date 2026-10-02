-- Sprint 2 · correcciones tras la auditoría antifraude (docs/arquitectura/sprint-2-correcciones.md).
-- No se editan V5–V7: todo cambio de esquema va aquí. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL.

-- C1. La deuda de una línea PENSION o MATRICULA lleva su año real; la obligación se calcula con él.
ALTER TABLE linea_saldo_inicial ADD COLUMN anio_deuda INT;
UPDATE linea_saldo_inicial SET anio_deuda = (SELECT a.anio FROM lote_saldo_inicial l
        JOIN anio_escolar a ON a.id = l.anio_escolar_id WHERE l.id = linea_saldo_inicial.lote_id)
    WHERE concepto IN ('PENSION', 'MATRICULA');
ALTER TABLE linea_saldo_inicial ADD CONSTRAINT ck_linea_saldo_inicial_anio CHECK (
    (concepto IN ('PENSION', 'MATRICULA') AND anio_deuda IS NOT NULL AND anio_deuda BETWEEN 2000 AND 2100)
    OR (concepto = 'OTRO' AND anio_deuda IS NULL));

-- A2 (a). Total del informe que escribe a ciegas quien confirma: debe ser igual al declarado.
ALTER TABLE lote_saldo_inicial ADD COLUMN total_confirmado DECIMAL(10,2);
UPDATE lote_saldo_inicial SET total_confirmado = total_declarado WHERE estado = 'CONFIRMADO';
ALTER TABLE lote_saldo_inicial ADD CONSTRAINT ck_lote_saldo_inicial_total_confirmado CHECK (estado <> 'CONFIRMADO'
    OR (total_confirmado IS NOT NULL AND total_confirmado = total_declarado));

-- A1 y M1. El plan se envía (bloqueado) antes de aprobarse; se guardan TODOS los editores («,a,b,»).
ALTER TABLE plan_pension ADD COLUMN enviado_por VARCHAR(60);
ALTER TABLE plan_pension ADD COLUMN enviado_en DATETIME(6);
ALTER TABLE plan_pension ADD COLUMN devuelto_por VARCHAR(60);
ALTER TABLE plan_pension ADD COLUMN devuelto_en DATETIME(6);
ALTER TABLE plan_pension ADD COLUMN motivo_devolucion VARCHAR(500);
ALTER TABLE plan_pension ADD COLUMN editores VARCHAR(1000) NOT NULL DEFAULT ',';
UPDATE plan_pension SET editores = CONCAT(',', creado_por, ',', editado_por, ',');

ALTER TABLE plan_pension DROP CONSTRAINT ck_plan_pension_estado;
ALTER TABLE plan_pension ADD CONSTRAINT ck_plan_pension_estado
    CHECK (estado IN ('BORRADOR', 'ENVIADO', 'APROBADO', 'REEMPLAZADO', 'DESCARTADO'));
ALTER TABLE plan_pension DROP CONSTRAINT ck_plan_pension_aprobacion;
ALTER TABLE plan_pension ADD CONSTRAINT ck_plan_pension_aprobacion CHECK (estado IN ('BORRADOR', 'ENVIADO', 'DESCARTADO')
    OR (aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL AND aprobado_por <> creado_por
        AND aprobado_por <> editado_por AND editores NOT LIKE CONCAT('%,', aprobado_por, ',%')
        AND (enviado_por IS NULL OR aprobado_por <> enviado_por)));
ALTER TABLE plan_pension ADD CONSTRAINT ck_plan_pension_envio CHECK (estado <> 'ENVIADO'
    OR (enviado_por IS NOT NULL AND enviado_en IS NOT NULL));
ALTER TABLE plan_pension ADD CONSTRAINT ck_plan_pension_editores CHECK (editores LIKE CONCAT('%,', editado_por, ',%'));
-- DS 005-2021-MINEDU: la matrícula no supera una pensión (también en la base).
ALTER TABLE plan_pension ADD CONSTRAINT ck_plan_pension_matricula_tope CHECK (monto_matricula <= monto_pension);

-- A3, A4, A6 (y anulaciones). Solicitudes de cambio que aprueba otra persona.
CREATE TABLE solicitud_cambio (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    tipo             VARCHAR(40)   NOT NULL,
    entidad          VARCHAR(40)   NOT NULL,
    entidad_id       BIGINT        NOT NULL,
    resumen          VARCHAR(300)  NOT NULL,
    datos            VARCHAR(2000) NOT NULL,
    motivo           VARCHAR(500)  NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    pendiente        BOOLEAN,
    solicitado_por   VARCHAR(60)   NOT NULL,
    resuelto_por     VARCHAR(60),
    resuelto_en      DATETIME(6),
    comentario       VARCHAR(500),
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_solicitud_cambio_pendiente UNIQUE (colegio_id, tipo, entidad, entidad_id, pendiente),
    CONSTRAINT uk_solicitud_cambio_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_solicitud_cambio_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_solicitud_cambio_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    CONSTRAINT ck_solicitud_cambio_solicitante CHECK (solicitado_por = creado_por),
    CONSTRAINT ck_solicitud_cambio_resolucion CHECK (
        (estado = 'PENDIENTE' AND pendiente IS NOT NULL AND pendiente = TRUE AND resuelto_por IS NULL
            AND resuelto_en IS NULL)
        OR (estado IN ('APROBADA', 'RECHAZADA') AND pendiente IS NULL AND resuelto_por IS NOT NULL
            AND resuelto_en IS NOT NULL AND resuelto_por <> solicitado_por)),
    CONSTRAINT ck_solicitud_cambio_rechazo CHECK (estado <> 'RECHAZADA' OR comentario IS NOT NULL)
);
CREATE INDEX ix_solicitud_cambio_estado ON solicitud_cambio (colegio_id, estado, tipo);
