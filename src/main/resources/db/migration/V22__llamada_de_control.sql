-- Sprint 6 · tanda 3. Llamada de control semanal (decisión 77; cierra el residual del sprint 5 «familia con un solo
-- apoderado y sin portal»: nadie avisaría de un efectivo no registrado). Cada lunes el sistema elige, con la semilla
-- SECRETA de semilla_muestreo (ámbito LLAMADA_CONTROL), unas familias que pagaron en efectivo; Promotoría o Dirección
-- llama, pregunta primero cuánto y cuándo pagaron y después compara con lo registrado. Aquí queda SOLO el resultado.
-- Solo inserción (sin GRANT de UPDATE ni DELETE: 1142). En MySQL, trg_llamada_control_registro exige que la registre
-- una persona activa de Promotoría o Dirección, para el lunes de la semana en curso (hora de Lima) y sobre una familia
-- que pagó en efectivo en las 5 semanas anteriores o en esa semana.
CREATE TABLE llamada_control (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT         NOT NULL,
    semana          DATE           NOT NULL,   -- lunes de la semana
    familia_id      BIGINT         NOT NULL,
    resultado       VARCHAR(20)    NOT NULL,
    nota            VARCHAR(300),
    creado_en       DATETIME(6)    NOT NULL,
    creado_por      VARCHAR(60)    NOT NULL,
    actualizado_en  DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_llamada_control UNIQUE (colegio_id, semana, familia_id),
    CONSTRAINT fk_llamada_control_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_llamada_control_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT ck_llamada_control_resultado CHECK (resultado IN ('CONFIRMA', 'NO_CONFIRMA', 'NO_CONTESTA')),
    -- «No confirma» es CRÍTICA: lleva qué dijo la familia (10 caracteres como mínimo, como un motivo).
    CONSTRAINT ck_llamada_control_nota CHECK (resultado <> 'NO_CONFIRMA'
        OR (nota IS NOT NULL AND CHAR_LENGTH(nota) >= 10))
);
CREATE INDEX ix_llamada_control_resultado ON llamada_control (colegio_id, resultado, creado_en);

-- La semilla secreta del muestreo admite el ámbito nuevo (V19 solo admitía CAJA).
ALTER TABLE semilla_muestreo DROP CONSTRAINT ck_semilla_muestreo_ambito;
ALTER TABLE semilla_muestreo ADD CONSTRAINT ck_semilla_muestreo_ambito CHECK (ambito IN ('CAJA', 'LLAMADA_CONTROL'));
