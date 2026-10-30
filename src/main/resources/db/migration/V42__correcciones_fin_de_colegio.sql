-- Sprint 11, correcciones de la auditoría del código (docs/arquitectura/sprint-11/correcciones.md): el retiro de la
-- autorización de conservación ya no es inmediato (A2) y la entrega de la exportación lleva un total de intentos que solo
-- sube (B1). Compatible con MySQL 8 y con H2 en modo MySQL. Sin triggers nuevos (los conteos no cambian).

-- A2: el titular PIDE el retiro con su firma y el código de WhatsApp de esa acción; la base lo aplica a las 24 horas
-- (sistema.plataforma) y un operador puede frenarlo antes. Las fechas las pone la base.
ALTER TABLE colegio ADD COLUMN conservacion_retiro_por VARCHAR(60);
ALTER TABLE colegio ADD COLUMN conservacion_retiro_pedido_en DATETIME(6);
ALTER TABLE colegio ADD COLUMN conservacion_retiro_aplica_desde DATETIME(6);
ALTER TABLE colegio ADD COLUMN conservacion_codigo_mensaje_id BIGINT;
ALTER TABLE colegio ADD COLUMN conservacion_codigo_sha256 VARCHAR(64);
ALTER TABLE colegio ADD CONSTRAINT uk_colegio_conservacion_codigo UNIQUE (conservacion_codigo_mensaje_id);
ALTER TABLE colegio ADD CONSTRAINT ck_colegio_conservacion_retiro CHECK (
    (conservacion_retiro_por IS NULL AND conservacion_retiro_pedido_en IS NULL AND conservacion_retiro_aplica_desde IS NULL)
    OR (conservacion_retiro_por IS NOT NULL AND conservacion_retiro_pedido_en IS NOT NULL
        AND conservacion_retiro_aplica_desde IS NOT NULL AND conservacion_autorizada_por IS NOT NULL
        AND conservacion_retiro_aplica_desde >= conservacion_retiro_pedido_en));

-- B1: los intentos de CADA enlace vuelven a 0 con un enlace nuevo (QA-S11-01); el total de la exportación solo sube.
ALTER TABLE exportacion_colegio ADD COLUMN intentos_totales INT NOT NULL DEFAULT 0;
ALTER TABLE exportacion_colegio ADD CONSTRAINT ck_exportacion_colegio_intentos_totales CHECK (
    intentos_totales >= intentos_fallidos AND intentos_totales >= 0);
