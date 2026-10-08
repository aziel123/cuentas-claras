-- Sprint 3 · correcciones tras la auditoría antifraude y QA (docs/arquitectura/sprint-3-correcciones.md).
-- No se editan V9–V11: todo cambio de esquema va aquí. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL.

-- C1. El número de operación se guarda en su forma canónica (letras y dígitos en mayúsculas, sin guiones ni ceros a
-- la izquierda) y es único para TODOS los medios digitales juntos mientras el pago está vigente (operacion_vigente es
-- NULL en efectivo y en los anulados). Los depósitos, igual entre sí.
ALTER TABLE pago ADD CONSTRAINT uk_pago_operacion_canonica UNIQUE (colegio_id, operacion_vigente);
ALTER TABLE pago ADD CONSTRAINT ck_pago_operacion_canonica CHECK (numero_operacion IS NULL
    OR REGEXP_LIKE(numero_operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c'));
ALTER TABLE deposito_caja ADD CONSTRAINT uk_deposito_caja_operacion_canonica UNIQUE (colegio_id, numero_operacion);
ALTER TABLE deposito_caja ADD CONSTRAINT ck_deposito_caja_operacion_canonica CHECK (
    REGEXP_LIKE(numero_operacion, '^[A-Z1-9][A-Z0-9]{3,29}$', 'c'));

-- C1 y A4. Verificación a ciegas: lo que Administración vio en el banco (operación, fecha y monto). ENCONTRADO exige
-- esos datos; el trigger en MySQL exige además que coincidan con el pago o el depósito.
ALTER TABLE verificacion_bancaria ADD COLUMN banco_operacion VARCHAR(30);
ALTER TABLE verificacion_bancaria ADD COLUMN banco_fecha DATE;
ALTER TABLE verificacion_bancaria ADD COLUMN banco_monto DECIMAL(10,2);
ALTER TABLE verificacion_bancaria ADD CONSTRAINT ck_verificacion_bancaria_evidencia CHECK (resultado <> 'ENCONTRADO'
    OR (banco_operacion IS NOT NULL AND banco_fecha IS NOT NULL AND banco_monto IS NOT NULL));

-- A1 y A2. Reembolso de una devolución aprobada: lo registra Administración (nunca la cajera del pago), por el mismo
-- medio del pago (un digital vuelve a la cuenta de origen, con su número de operación; el efectivo, contra la firma y
-- el documento de quien lo recibe). Uno por anulación. SOLO INSERCIÓN.
ALTER TABLE anulacion_pago ADD CONSTRAINT uk_anulacion_pago_id_colegio UNIQUE (id, colegio_id);
CREATE TABLE reembolso (
    id                       BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id               BIGINT         NOT NULL,
    anulacion_pago_id        BIGINT         NOT NULL,
    medio                    VARCHAR(20)    NOT NULL,
    numero_operacion         VARCHAR(30),
    recibido_por_nombre      VARCHAR(150),
    recibido_por_documento   VARCHAR(20),
    monto                    DECIMAL(10,2)  NOT NULL,
    fecha                    DATE           NOT NULL,
    cajero_pago              VARCHAR(60)    NOT NULL,
    creado_en                DATETIME(6)    NOT NULL,
    creado_por               VARCHAR(60)    NOT NULL,
    actualizado_en           DATETIME(6)    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_reembolso_anulacion UNIQUE (colegio_id, anulacion_pago_id),
    CONSTRAINT uk_reembolso_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_reembolso_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_reembolso_anulacion FOREIGN KEY (anulacion_pago_id, colegio_id)
        REFERENCES anulacion_pago (id, colegio_id),
    CONSTRAINT ck_reembolso_monto CHECK (monto > 0),
    CONSTRAINT ck_reembolso_medio CHECK ((medio = 'EFECTIVO' AND numero_operacion IS NULL
            AND recibido_por_nombre IS NOT NULL AND recibido_por_documento IS NOT NULL)
        OR (medio IN ('YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA') AND numero_operacion IS NOT NULL)),
    CONSTRAINT ck_reembolso_segregacion CHECK (creado_por <> cajero_pago)
);

-- M1. La reapertura de una caja y la anulación de una cuota quedan enlazadas a SU solicitud aprobada. Una reapertura
-- se usa una sola vez (UNIQUE); una cuota se anula con su solicitud de anulación o con el ingreso tardío aprobado de su
-- matrícula (que anula varias cuotas a la vez): el trigger exige que la solicitud corresponda a esa cuota o matrícula.
ALTER TABLE caja_diaria ADD COLUMN reapertura_solicitud_id BIGINT;
ALTER TABLE caja_diaria ADD CONSTRAINT uk_caja_diaria_reapertura UNIQUE (colegio_id, reapertura_solicitud_id);
ALTER TABLE caja_diaria ADD CONSTRAINT fk_caja_diaria_reapertura FOREIGN KEY (reapertura_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);
ALTER TABLE cuota ADD COLUMN anulacion_solicitud_id BIGINT;
ALTER TABLE cuota ADD CONSTRAINT fk_cuota_anulacion_solicitud FOREIGN KEY (anulacion_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);

-- Hallazgo 4 de QA. Un cierre después de una reapertura no es ciego (la cajera ya vio el esperado): queda marcado.
ALTER TABLE cierre_caja ADD COLUMN tras_reapertura BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cierre_caja ADD CONSTRAINT ck_cierre_caja_tras_reapertura CHECK ((numero = 1 AND tras_reapertura = FALSE)
    OR (numero > 1 AND tras_reapertura = TRUE));

-- B2. Datos de facturación del apoderado (para emitir factura solo con un RUC registrado de la familia). Se registran
-- o cambian con una solicitud DATOS_FACTURACION aprobada (enlazada por id; en MySQL lo exige un trigger).
ALTER TABLE apoderado ADD COLUMN ruc VARCHAR(11);
ALTER TABLE apoderado ADD COLUMN razon_social VARCHAR(150);
ALTER TABLE apoderado ADD COLUMN facturacion_solicitud_id BIGINT;
ALTER TABLE apoderado ADD CONSTRAINT ck_apoderado_facturacion CHECK ((ruc IS NULL AND razon_social IS NULL)
    OR (ruc IS NOT NULL AND razon_social IS NOT NULL AND facturacion_solicitud_id IS NOT NULL));
ALTER TABLE apoderado ADD CONSTRAINT uk_apoderado_facturacion UNIQUE (colegio_id, facturacion_solicitud_id);
ALTER TABLE apoderado ADD CONSTRAINT fk_apoderado_facturacion FOREIGN KEY (facturacion_solicitud_id, colegio_id)
    REFERENCES solicitud_cambio (id, colegio_id);
