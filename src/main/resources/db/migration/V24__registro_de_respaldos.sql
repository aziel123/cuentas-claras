-- Sprint 7 · tanda 1. Registro de cada respaldo diario cifrado (lo escribe SOLO cc_respaldo: GRANT y
-- trg_respaldo_registro de scripts/mysql/03-triggers.sql). Tabla técnica de toda la base: sin colegio_id y sin datos
-- personales (solo nombres de archivo, huellas SHA-256, conteos y anclas de la bitácora). De solo inserción: nadie la
-- edita ni la borra (cc_app solo la lee; cc_respaldo solo inserta).
-- Ancla la bitácora (secuencia y hash antes y después del volcado) y guarda los conteos de las tablas de solo inserción
-- para que el respaldo siguiente detecte filas borradas (comparacion = FALTAN_FILAS, con la lista en diferencias).
CREATE TABLE respaldo (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inicio              DATETIME(6)   NOT NULL,   -- hora de Lima según la base
    fin                 DATETIME(6)   NOT NULL,
    archivo             VARCHAR(120)  NOT NULL,   -- cc-AAAAMMDD-HHMMSS.sql.gz.age
    sha256              VARCHAR(64)   NOT NULL,   -- del archivo CIFRADO
    bytes               BIGINT        NOT NULL,
    version_esquema     VARCHAR(20)   NOT NULL,   -- última versión de Flyway
    secuencia_antes     BIGINT        NOT NULL,
    hash_antes          VARCHAR(64)   NOT NULL,
    secuencia_despues   BIGINT        NOT NULL,
    hash_despues        VARCHAR(64)   NOT NULL,
    conteos             VARCHAR(4000) NOT NULL,   -- JSON {tabla: [filas, id máximo]} de las tablas de solo inserción
    huella_objetos      VARCHAR(64),              -- SHA-256 de huellas_objetos() (desde la tanda 2)
    destino             VARCHAR(60)   NOT NULL,   -- «simulado» o el nombre del remoto de rclone (sin credenciales)
    comparacion         VARCHAR(20)   NOT NULL,   -- con el manifiesto anterior: PRIMERO, IGUAL o FALTAN_FILAS
    diferencias         VARCHAR(1000),            -- tablas con filas faltantes o el ancla perdida (sin datos personales)
    creado_en           DATETIME(6)   NOT NULL,
    creado_por          VARCHAR(60)   NOT NULL,
    CONSTRAINT uk_respaldo_archivo UNIQUE (archivo),
    CONSTRAINT ck_respaldo_archivo CHECK (REGEXP_LIKE(archivo, '^cc-[0-9]{8}-[0-9]{6}[.]sql[.]gz[.]age$', 'c')),
    CONSTRAINT ck_respaldo_sha CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_respaldo_hashes CHECK (REGEXP_LIKE(hash_antes, '^[0-9a-f]{64}$', 'c')
        AND REGEXP_LIKE(hash_despues, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT ck_respaldo_destino CHECK (REGEXP_LIKE(destino, '^[A-Za-z0-9_-]{1,60}$', 'c')),
    CONSTRAINT ck_respaldo_orden CHECK (fin >= inicio AND bytes > 0 AND secuencia_antes >= 0
        AND secuencia_despues >= secuencia_antes),
    CONSTRAINT ck_respaldo_comparacion CHECK ((comparacion IN ('PRIMERO', 'IGUAL') AND diferencias IS NULL)
        OR (comparacion = 'FALTAN_FILAS' AND diferencias IS NOT NULL)),
    CONSTRAINT ck_respaldo_actor CHECK (creado_por = 'cc_respaldo')
);
CREATE INDEX ix_respaldo_fin ON respaldo (fin);
