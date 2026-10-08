-- Correcciones del sprint 6 tras la auditoría antifraude y QA (V21 y V22 no se editan). En MySQL, los triggers de esta
-- tanda (03-triggers.sql: 63) exigen lo que el GRANT no distingue.

-- S6-M1. La foto guarda el TEXTO exacto que sale en el resumen (los parámetros de cc_resumen_diario, separados por un
-- salto de línea, como mensaje.parametros). trg_mensaje_nace exige que cada RESUMEN_DIARIO lleve ESE texto, y
-- trg_resumen_diario_registro, que el texto diga las cifras de la foto. Las fotos anteriores a V23 quedan con NULL.
ALTER TABLE resumen_diario ADD COLUMN parametros VARCHAR(1000);

-- QA-S6-6. Configuración que escribe SOLO el DBA, por colegio (configuracion_bd no tiene colegio_id: el correo del
-- contador de un colegio recibía el resumen de todos). cc_app no tiene GRANT de escritura (1142).
--   ('resumen_correo_externo', '<correo del contador de ESE colegio>')
CREATE TABLE configuracion_colegio (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id  BIGINT        NOT NULL,
    clave       VARCHAR(40)   NOT NULL,
    valor       VARCHAR(100)  NOT NULL,
    creado_en   DATETIME(6)   NOT NULL,
    CONSTRAINT uk_configuracion_colegio UNIQUE (colegio_id, clave),
    CONSTRAINT fk_configuracion_colegio_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT ck_configuracion_colegio_clave CHECK (clave IN ('resumen_correo_externo'))
);

-- S6-A2 y S6-B3 (QA-S6-2). La muestra de la llamada de control se CONGELA la primera vez que se consulta en la semana
-- (solo inserción: 1142). Motivo: EFECTIVO (pagó en efectivo en las 5 semanas anteriores), DEUDA (deuda vencida: el
-- efectivo que nunca se registró no deja pago, pero sí deuda) o REEMPLAZO (de una familia que no contestó dos veces).
-- En MySQL, trg_muestra_llamada_registro exige la semana en curso, una familia candidata y, para un reemplazo, los dos
-- «No contesta» de la familia reemplazada.
CREATE TABLE muestra_llamada (
    id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id             BIGINT        NOT NULL,
    semana                 DATE          NOT NULL,   -- lunes de la semana
    familia_id             BIGINT        NOT NULL,
    motivo                 VARCHAR(20)   NOT NULL,
    reemplaza_familia_id   BIGINT,
    creado_en              DATETIME(6)   NOT NULL,
    creado_por             VARCHAR(60)   NOT NULL,
    actualizado_en         DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_muestra_llamada UNIQUE (colegio_id, semana, familia_id),
    CONSTRAINT fk_muestra_llamada_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_muestra_llamada_familia FOREIGN KEY (familia_id, colegio_id) REFERENCES familia (id, colegio_id),
    CONSTRAINT fk_muestra_llamada_reemplaza FOREIGN KEY (reemplaza_familia_id, colegio_id)
        REFERENCES familia (id, colegio_id),
    CONSTRAINT ck_muestra_llamada_motivo CHECK (motivo IN ('EFECTIVO', 'DEUDA', 'REEMPLAZO')),
    CONSTRAINT ck_muestra_llamada_reemplazo CHECK ((motivo = 'REEMPLAZO' AND reemplaza_familia_id IS NOT NULL
            AND reemplaza_familia_id <> familia_id)
        OR (motivo <> 'REEMPLAZO' AND reemplaza_familia_id IS NULL))
);

-- S6-M2. Las llamadas las registra Promotoría; Dirección, solo la semana que Promotoría se las delega (una fila por
-- semana, solo inserción: la delegación no se revoca ni se reescribe). En MySQL lo exige trg_delegacion_llamada_registro
-- (la delega una persona activa de Promotoría, para la semana en curso) y trg_llamada_control_registro.
CREATE TABLE delegacion_llamada (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id      BIGINT        NOT NULL,
    semana          DATE          NOT NULL,
    creado_en       DATETIME(6)   NOT NULL,
    creado_por      VARCHAR(60)   NOT NULL,
    actualizado_en  DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_delegacion_llamada UNIQUE (colegio_id, semana),
    CONSTRAINT fk_delegacion_llamada_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id)
);

-- S6-M2. «No contesta» no cierra la plaza: se reintenta una vez (intento 2). Si tampoco contesta, la familia se
-- reemplaza en la muestra. CONFIRMA y NO_CONFIRMA cierran la plaza. La UNIQUE nueva va antes de quitar la anterior
-- (las FK de colegio_id necesitan un índice que empiece por esa columna).
ALTER TABLE llamada_control ADD COLUMN intento INT NOT NULL DEFAULT 1;
-- S6-M2. TRUE si la registró Dirección por delegación (quien la registra no es de Promotoría): Promotoría recibe el aviso.
ALTER TABLE llamada_control ADD COLUMN por_delegacion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE llamada_control ADD CONSTRAINT uk_llamada_control_intento UNIQUE (colegio_id, semana, familia_id, intento);
ALTER TABLE llamada_control DROP CONSTRAINT uk_llamada_control;
ALTER TABLE llamada_control ADD CONSTRAINT ck_llamada_control_intento CHECK (intento IN (1, 2));
