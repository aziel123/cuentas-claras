-- Correcciones del sprint 10 (docs/arquitectura/sprint-10/correcciones.md): auditoría del código sobre 83928f0 y pendientes
-- del QA. Compatible con MySQL 8 y con H2 en modo MySQL. No agrega triggers: cambian trg_lote_calificacion_nace,
-- trg_calificacion_nace, trg_libreta_emitida_nace y trg_matricula_estado (03), así que siguen 112 triggers y 128 huellas.

-- Hallazgo 1 (alto): la conexión de la base que guardó el lote (la pone trg_lote_calificacion_nace con CONNECTION_ID()).
-- Cada nota del lote la exige: nadie agrega notas a un lote ajeno desde otra conexión. NULL en H2 y en los lotes de V36.
ALTER TABLE lote_calificacion ADD COLUMN conexion_id BIGINT;

-- Hallazgo 2 (alto): el contenido canónico de los NIVELES de la libreta (una línea por periodo y competencia, y la anual),
-- para que la reemisión vuelva a emitir lo emitido y Promotoría vea qué cambió en la base. NULL en las libretas de V37.
ALTER TABLE libreta_emitida ADD COLUMN niveles VARCHAR(6000);

-- Hallazgo 8 y P-1, P-6 y P-7 del QA: la historia de la matrícula que escribe la base (trg_matricula_estado) cada vez que
-- cambia su sección o su estado, con la hora de Lima de la base. La libreta de un periodo es de la sección y el estado que
-- tenía la matrícula a su cerrado_en: la primera fila posterior al cierre dice cómo estaba; sin filas, como está hoy.
-- SOLO la base escribe (cc_negocio y cc_sistema solo leen): nadie la fabrica para mover una libreta de sección.
CREATE TABLE matricula_historial (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id            BIGINT        NOT NULL,
    matricula_id          BIGINT        NOT NULL,
    seccion_anterior_id   BIGINT        NOT NULL,
    seccion_nueva_id      BIGINT        NOT NULL,
    estado_anterior       VARCHAR(20)   NOT NULL,
    estado_nuevo          VARCHAR(20)   NOT NULL,
    cambiado_bd           DATETIME(6)   NOT NULL,
    creado_en             DATETIME(6)   NOT NULL,
    creado_por            VARCHAR(60)   NOT NULL,
    actualizado_en        DATETIME(6)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_matricula_historial_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_matricula_historial_matricula FOREIGN KEY (matricula_id, colegio_id) REFERENCES matricula (id, colegio_id),
    CONSTRAINT fk_matricula_historial_anterior FOREIGN KEY (seccion_anterior_id, colegio_id) REFERENCES seccion (id, colegio_id),
    CONSTRAINT fk_matricula_historial_nueva FOREIGN KEY (seccion_nueva_id, colegio_id) REFERENCES seccion (id, colegio_id)
);

CREATE INDEX ix_matricula_historial_matricula ON matricula_historial (matricula_id, cambiado_bd);
