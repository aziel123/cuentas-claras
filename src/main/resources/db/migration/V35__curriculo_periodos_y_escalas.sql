-- Sprint 10 · tanda 1 (docs/arquitectura/sprint-10-notas-libretas-siagie.md, secciones 3.2 a 3.4, 3.13 y 5). Catálogo
-- nacional versionado (CNEB 2016 con la RVM 094-2020 y la RVM 048-2024): SIN colegio_id y solo lo escribe la migración
-- (cc_migrador); cc_negocio no tiene GRANT de escritura (1142). Corregirlo es una versión NUEVA (otra migración de solo
-- inserción, con reemplaza_id): ninguna nota se toca. Su huella la verifica VerificadorCatalogoCurriculo al arrancar y a
-- diario contra src/main/resources/curriculo/{codigo}.sha256 (M10). Configuración de evaluación y periodos por colegio y
-- año (solo Dirección, con su firma: triggers de 03); áreas de cada asignación docente, que se aprueban con ella (A2).
-- Nada se borra. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Lección de V32 a V34: un CHECK que evalúa NULL PASA; toda columna que admite NULL se compara con IS NOT NULL.
-- B13: ningún texto del catálogo lleva la barra vertical (CHAR(124), el separador de la huella) ni saltos de línea.

CREATE TABLE curriculo_version (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(40)   NOT NULL,
    nombre          VARCHAR(200)  NOT NULL,
    normas          VARCHAR(500)  NOT NULL,
    vigente_desde   DATE          NOT NULL,
    reemplaza_id    BIGINT,                   -- NULL: primera versión; si no, la que corrige (una sola sucesora)
    nota            VARCHAR(500),
    creado_en       DATETIME(6)   NOT NULL,
    CONSTRAINT uk_curriculo_version_codigo UNIQUE (codigo),
    CONSTRAINT uk_curriculo_version_reemplaza UNIQUE (reemplaza_id),
    CONSTRAINT fk_curriculo_version_reemplaza FOREIGN KEY (reemplaza_id) REFERENCES curriculo_version (id),
    CONSTRAINT ck_curriculo_version_codigo CHECK (REGEXP_LIKE(codigo, '^[A-Z0-9.-]{4,40}$', 'c')
        AND CHAR_LENGTH(TRIM(nombre)) >= 5 AND CHAR_LENGTH(TRIM(normas)) >= 5)
);

CREATE TABLE area_curricular (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_id   BIGINT        NOT NULL,
    nivel        VARCHAR(12)   NOT NULL,
    codigo       VARCHAR(8)    NOT NULL,
    nombre       VARCHAR(80)   NOT NULL,
    orden        INT           NOT NULL,
    transversal  BOOLEAN       NOT NULL DEFAULT FALSE,
    exonerable   BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_area_curricular_codigo UNIQUE (version_id, nivel, codigo),
    CONSTRAINT uk_area_curricular_orden UNIQUE (version_id, nivel, orden),
    CONSTRAINT uk_area_curricular_id_version UNIQUE (id, version_id),
    CONSTRAINT fk_area_curricular_version FOREIGN KEY (version_id) REFERENCES curriculo_version (id),
    CONSTRAINT ck_area_curricular_valores CHECK (nivel IN ('INICIAL', 'PRIMARIA', 'SECUNDARIA')
        AND REGEXP_LIKE(codigo, '^[A-Z0-9]{2,8}$', 'c') AND orden BETWEEN 1 AND 99
        AND NOT (transversal AND exonerable)),
    CONSTRAINT ck_area_curricular_texto CHECK (CHAR_LENGTH(TRIM(nombre)) >= 3 AND INSTR(nombre, CHAR(124)) = 0
        AND INSTR(nombre, CHAR(10)) = 0 AND INSTR(nombre, CHAR(13)) = 0)
);

CREATE TABLE competencia (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_id   BIGINT        NOT NULL,
    area_id      BIGINT        NOT NULL,
    codigo       VARCHAR(16)   NOT NULL,   -- PRI-MAT-1: no se renombra (lo usan las tandas 2 y 3 y el SIAGIE)
    nombre       VARCHAR(300)  NOT NULL,
    orden        INT           NOT NULL,
    solo_grados  VARCHAR(60),              -- NULL: todos los grados del nivel; si no, lista: 'INICIAL_5'
    CONSTRAINT uk_competencia_codigo UNIQUE (version_id, codigo),
    CONSTRAINT uk_competencia_orden UNIQUE (area_id, orden),
    CONSTRAINT uk_competencia_id_version UNIQUE (id, version_id),
    CONSTRAINT fk_competencia_area FOREIGN KEY (area_id, version_id) REFERENCES area_curricular (id, version_id),
    CONSTRAINT ck_competencia_valores CHECK (REGEXP_LIKE(codigo, '^(INI|PRI|SEC)-[A-Z0-9]{2,8}-[0-9]{1,2}$', 'c')
        AND orden BETWEEN 1 AND 99
        AND (solo_grados IS NULL OR REGEXP_LIKE(solo_grados,
            '^(INICIAL_[3-5]|PRIMARIA_[1-6]|SECUNDARIA_[1-5])(,(INICIAL_[3-5]|PRIMARIA_[1-6]|SECUNDARIA_[1-5]))*$', 'c'))),
    CONSTRAINT ck_competencia_texto CHECK (CHAR_LENGTH(TRIM(nombre)) >= 5 AND INSTR(nombre, CHAR(124)) = 0
        AND INSTR(nombre, CHAR(10)) = 0 AND INSTR(nombre, CHAR(13)) = 0)
);

CREATE TABLE escala_logro (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_id        BIGINT        NOT NULL,
    nivel             VARCHAR(12)   NOT NULL,
    valor             VARCHAR(2)    NOT NULL,
    nombre            VARCHAR(40)   NOT NULL,
    descripcion       VARCHAR(400)  NOT NULL,
    orden             INT           NOT NULL,
    exige_conclusion  BOOLEAN       NOT NULL,
    CONSTRAINT uk_escala_logro UNIQUE (version_id, nivel, valor),
    CONSTRAINT fk_escala_logro_version FOREIGN KEY (version_id) REFERENCES curriculo_version (id),
    CONSTRAINT ck_escala_logro_valores CHECK (nivel IN ('INICIAL', 'PRIMARIA', 'SECUNDARIA')
        AND valor IN ('AD', 'A', 'B', 'C') AND orden BETWEEN 1 AND 9),
    CONSTRAINT ck_escala_logro_texto CHECK (CHAR_LENGTH(TRIM(nombre)) >= 3 AND CHAR_LENGTH(TRIM(descripcion)) >= 10
        AND INSTR(nombre, CHAR(124)) = 0 AND INSTR(nombre, CHAR(10)) = 0 AND INSTR(nombre, CHAR(13)) = 0
        AND INSTR(descripcion, CHAR(124)) = 0 AND INSTR(descripcion, CHAR(10)) = 0 AND INSTR(descripcion, CHAR(13)) = 0)
);

-- Semilla de la versión CNEB-2016.1: 29 áreas (7 de Inicial, 10 de Primaria y 12 de Secundaria, con «Competencias
-- transversales» como un área marcada transversal), 75 competencias (14 + 30 + 31) y 12 valores de escala (4 por nivel).
-- Los nombres, como la tabla de la sección 3.2 del diseño (anexos 02 a 04 de la RVM 094-2020, que transcriben el CNEB). Un
-- INSERT ... SELECT por fila, con el id de la versión por su código (H2 y MySQL). Nada de || para concatenar (en MySQL es
-- OR): cada texto va en un solo literal. La huella de estas filas está en src/main/resources/curriculo/CNEB-2016.1.sha256.
INSERT INTO curriculo_version (codigo, nombre, normas, vigente_desde, creado_en)
VALUES ('CNEB-2016.1', 'Currículo Nacional de la Educación Básica 2016',
        'RM 281-2016-MINEDU; RVM 094-2020-MINEDU, modificada por RVM 048-2024-MINEDU', '2027-01-01', CURRENT_TIMESTAMP(6));
-- Inicial: 7 áreas
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'PS', 'Personal Social', 1, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'PSI', 'Psicomotriz', 2, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'COM', 'Comunicación', 3, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'C2L', 'Castellano como segunda lengua', 4, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'MAT', 'Matemática', 5, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'CYT', 'Ciencia y Tecnología', 6, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'INICIAL', 'TRV', 'Competencias transversales', 7, TRUE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
-- Primaria: 10 áreas
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'PS', 'Personal Social', 1, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'EF', 'Educación Física', 2, FALSE, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'COM', 'Comunicación', 3, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'AYC', 'Arte y Cultura', 4, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'C2L', 'Castellano como segunda lengua', 5, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'ING', 'Inglés como lengua extranjera', 6, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'MAT', 'Matemática', 7, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'CYT', 'Ciencia y Tecnología', 8, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'REL', 'Educación Religiosa', 9, FALSE, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'PRIMARIA', 'TRV', 'Competencias transversales', 10, TRUE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
-- Secundaria: 12 áreas
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'DPCC', 'Desarrollo Personal, Ciudadanía y Cívica', 1, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'CCSS', 'Ciencias Sociales', 2, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'EPT', 'Educación para el Trabajo', 3, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'EF', 'Educación Física', 4, FALSE, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'COM', 'Comunicación', 5, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'AYC', 'Arte y Cultura', 6, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'C2L', 'Castellano como segunda lengua', 7, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'ING', 'Inglés como lengua extranjera', 8, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'MAT', 'Matemática', 9, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'CYT', 'Ciencia y Tecnología', 10, FALSE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'REL', 'Educación Religiosa', 11, FALSE, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO area_curricular (version_id, nivel, codigo, nombre, orden, transversal, exonerable)
SELECT v.id, 'SECUNDARIA', 'TRV', 'Competencias transversales', 12, TRUE, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
-- Competencias de Inicial: 14
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-PS-1', 'Construye su identidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-PS-2', 'Convive y participa democráticamente en la búsqueda del bien común', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-PS-3', 'Construye su identidad como persona humana, amada por Dios, digna, libre y trascendente, comprendiendo la doctrina de su propia religión, abierto al diálogo con las que le son cercanas', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-PSI-1', 'Se desenvuelve de manera autónoma a través de su motricidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'PSI';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-COM-1', 'Se comunica oralmente en su lengua materna', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-COM-2', 'Lee diversos tipos de textos escritos en su lengua materna', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-COM-3', 'Escribe diversos tipos de textos en su lengua materna', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-COM-4', 'Crea proyectos desde los lenguajes artísticos', 4, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-C2L-1', 'Se comunica oralmente en castellano como segunda lengua', 1, 'INICIAL_5'
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-MAT-1', 'Resuelve problemas de cantidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-MAT-2', 'Resuelve problemas de forma, movimiento y localización', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-CYT-1', 'Indaga mediante métodos científicos para construir sus conocimientos', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-TRV-1', 'Se desenvuelve en entornos virtuales generados por las TIC', 1, 'INICIAL_5'
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'TRV';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'INI-TRV-2', 'Gestiona su aprendizaje de manera autónoma', 2, 'INICIAL_5'
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'INICIAL' AND a.codigo = 'TRV';
-- Competencias de Primaria: 30
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-PS-1', 'Construye su identidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-PS-2', 'Convive y participa democráticamente en la búsqueda del bien común', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-PS-3', 'Construye interpretaciones históricas', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-PS-4', 'Gestiona responsablemente el espacio y el ambiente', 4, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-PS-5', 'Gestiona responsablemente los recursos económicos', 5, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'PS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-EF-1', 'Se desenvuelve de manera autónoma a través de su motricidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-EF-2', 'Asume una vida saludable', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-EF-3', 'Interactúa a través de sus habilidades sociomotrices', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-COM-1', 'Se comunica oralmente en su lengua materna', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-COM-2', 'Lee diversos tipos de textos escritos en su lengua materna', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-COM-3', 'Escribe diversos tipos de textos en su lengua materna', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-AYC-1', 'Aprecia de manera crítica manifestaciones artístico-culturales', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'AYC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-AYC-2', 'Crea proyectos desde los lenguajes artísticos', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'AYC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-C2L-1', 'Se comunica oralmente en castellano como segunda lengua', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-C2L-2', 'Lee diversos tipos de textos escritos en castellano como segunda lengua', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-C2L-3', 'Escribe diversos tipos de textos en castellano como segunda lengua', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-ING-1', 'Se comunica oralmente en inglés como lengua extranjera', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-ING-2', 'Lee diversos tipos de textos escritos en inglés como lengua extranjera', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-ING-3', 'Escribe diversos tipos de textos en inglés como lengua extranjera', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-MAT-1', 'Resuelve problemas de cantidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-MAT-2', 'Resuelve problemas de regularidad, equivalencia y cambio', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-MAT-3', 'Resuelve problemas de forma, movimiento y localización', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-MAT-4', 'Resuelve problemas de gestión de datos e incertidumbre', 4, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-CYT-1', 'Indaga mediante métodos científicos para construir sus conocimientos', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-CYT-2', 'Explica el mundo físico basándose en conocimientos sobre los seres vivos, materia y energía, biodiversidad, Tierra y universo', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-CYT-3', 'Diseña y construye soluciones tecnológicas para resolver problemas de su entorno', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-REL-1', 'Construye su identidad como persona humana, amada por Dios, digna, libre y trascendente, comprendiendo la doctrina de su propia religión, abierto al diálogo con las que le son cercanas', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'REL';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-REL-2', 'Asume la experiencia del encuentro personal y comunitario con Dios en su proyecto de vida en coherencia con su creencia religiosa', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'REL';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-TRV-1', 'Se desenvuelve en entornos virtuales generados por las TIC', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'TRV';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'PRI-TRV-2', 'Gestiona su aprendizaje de manera autónoma', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'PRIMARIA' AND a.codigo = 'TRV';
-- Competencias de Secundaria: 31
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-DPCC-1', 'Construye su identidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'DPCC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-DPCC-2', 'Convive y participa democráticamente en la búsqueda del bien común', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'DPCC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CCSS-1', 'Construye interpretaciones históricas', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CCSS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CCSS-2', 'Gestiona responsablemente el espacio y el ambiente', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CCSS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CCSS-3', 'Gestiona responsablemente los recursos económicos', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CCSS';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-EPT-1', 'Gestiona proyectos de emprendimiento económico o social', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'EPT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-EF-1', 'Se desenvuelve de manera autónoma a través de su motricidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-EF-2', 'Asume una vida saludable', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-EF-3', 'Interactúa a través de sus habilidades sociomotrices', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'EF';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-COM-1', 'Se comunica oralmente en su lengua materna', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-COM-2', 'Lee diversos tipos de textos escritos en su lengua materna', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-COM-3', 'Escribe diversos tipos de textos en su lengua materna', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'COM';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-AYC-1', 'Aprecia de manera crítica manifestaciones artístico-culturales', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'AYC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-AYC-2', 'Crea proyectos desde los lenguajes artísticos', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'AYC';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-C2L-1', 'Se comunica oralmente en castellano como segunda lengua', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-C2L-2', 'Lee diversos tipos de textos escritos en castellano como segunda lengua', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-C2L-3', 'Escribe diversos tipos de textos en castellano como segunda lengua', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'C2L';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-ING-1', 'Se comunica oralmente en inglés como lengua extranjera', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-ING-2', 'Lee diversos tipos de textos escritos en inglés como lengua extranjera', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-ING-3', 'Escribe diversos tipos de textos en inglés como lengua extranjera', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'ING';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-MAT-1', 'Resuelve problemas de cantidad', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-MAT-2', 'Resuelve problemas de regularidad, equivalencia y cambio', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-MAT-3', 'Resuelve problemas de forma, movimiento y localización', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-MAT-4', 'Resuelve problemas de gestión de datos e incertidumbre', 4, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'MAT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CYT-1', 'Indaga mediante métodos científicos para construir sus conocimientos', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CYT-2', 'Explica el mundo físico basándose en conocimientos sobre los seres vivos, materia y energía, biodiversidad, Tierra y universo', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-CYT-3', 'Diseña y construye soluciones tecnológicas para resolver problemas de su entorno', 3, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'CYT';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-REL-1', 'Construye su identidad como persona humana, amada por Dios, digna, libre y trascendente, comprendiendo la doctrina de su propia religión, abierto al diálogo con las que le son cercanas', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'REL';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-REL-2', 'Asume la experiencia del encuentro personal y comunitario con Dios en su proyecto de vida en coherencia con su creencia religiosa', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'REL';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-TRV-1', 'Se desenvuelve en entornos virtuales generados por las TIC', 1, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'TRV';
INSERT INTO competencia (version_id, area_id, codigo, nombre, orden, solo_grados)
SELECT a.version_id, a.id, 'SEC-TRV-2', 'Gestiona su aprendizaje de manera autónoma', 2, NULL
FROM area_curricular a JOIN curriculo_version v ON v.id = a.version_id
WHERE v.codigo = 'CNEB-2016.1' AND a.nivel = 'SECUNDARIA' AND a.codigo = 'TRV';
-- Escala de calificación (RVM 094-2020, 5.1.1.3.6; exige_conclusion según la RVM 048-2024)
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'INICIAL', 'AD', 'Logro destacado',
    'Cuando el estudiante evidencia un nivel superior a lo esperado respecto a la competencia. Esto quiere decir que demuestra aprendizajes que van más allá del nivel esperado.',
    1, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'INICIAL', 'A', 'Logro esperado',
    'Cuando el estudiante evidencia el nivel esperado respecto a la competencia, demostrando manejo satisfactorio en todas las tareas propuestas y en el tiempo programado.',
    2, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'INICIAL', 'B', 'En proceso',
    'Cuando el estudiante está próximo o cerca al nivel esperado respecto a la competencia, para lo cual requiere acompañamiento durante un tiempo razonable para lograrlo.',
    3, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'INICIAL', 'C', 'En inicio',
    'Cuando el estudiante muestra un progreso mínimo en una competencia de acuerdo al nivel esperado. Evidencia con frecuencia dificultades en el desarrollo de las tareas, por lo que necesita mayor tiempo de acompañamiento e intervención del docente.',
    4, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'PRIMARIA', 'AD', 'Logro destacado',
    'Cuando el estudiante evidencia un nivel superior a lo esperado respecto a la competencia. Esto quiere decir que demuestra aprendizajes que van más allá del nivel esperado.',
    1, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'PRIMARIA', 'A', 'Logro esperado',
    'Cuando el estudiante evidencia el nivel esperado respecto a la competencia, demostrando manejo satisfactorio en todas las tareas propuestas y en el tiempo programado.',
    2, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'PRIMARIA', 'B', 'En proceso',
    'Cuando el estudiante está próximo o cerca al nivel esperado respecto a la competencia, para lo cual requiere acompañamiento durante un tiempo razonable para lograrlo.',
    3, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'PRIMARIA', 'C', 'En inicio',
    'Cuando el estudiante muestra un progreso mínimo en una competencia de acuerdo al nivel esperado. Evidencia con frecuencia dificultades en el desarrollo de las tareas, por lo que necesita mayor tiempo de acompañamiento e intervención del docente.',
    4, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'SECUNDARIA', 'AD', 'Logro destacado',
    'Cuando el estudiante evidencia un nivel superior a lo esperado respecto a la competencia. Esto quiere decir que demuestra aprendizajes que van más allá del nivel esperado.',
    1, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'SECUNDARIA', 'A', 'Logro esperado',
    'Cuando el estudiante evidencia el nivel esperado respecto a la competencia, demostrando manejo satisfactorio en todas las tareas propuestas y en el tiempo programado.',
    2, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'SECUNDARIA', 'B', 'En proceso',
    'Cuando el estudiante está próximo o cerca al nivel esperado respecto a la competencia, para lo cual requiere acompañamiento durante un tiempo razonable para lograrlo.',
    3, FALSE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';
INSERT INTO escala_logro (version_id, nivel, valor, nombre, descripcion, orden, exige_conclusion)
SELECT v.id, 'SECUNDARIA', 'C', 'En inicio',
    'Cuando el estudiante muestra un progreso mínimo en una competencia de acuerdo al nivel esperado. Evidencia con frecuencia dificultades en el desarrollo de las tareas, por lo que necesita mayor tiempo de acompañamiento e intervención del docente.',
    4, TRUE FROM curriculo_version v WHERE v.codigo = 'CNEB-2016.1';

-- Configuración de evaluación de un año (S1, S3, S9, S11 y H6): solo Dirección, con su firma
-- (configuracion_evaluacion:{anio}:GUARDADA:{version}; trg_configuracion_evaluacion_nace y _cambio). La versión del
-- currículo, el tipo de periodo y los días de cierre anticipado quedan fijos desde que abre un periodo del año (M7).
CREATE TABLE configuracion_evaluacion (
    id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id              BIGINT        NOT NULL,
    anio_escolar_id         BIGINT        NOT NULL,
    curriculo_version_id    BIGINT        NOT NULL,
    tipo_periodo            VARCHAR(10)   NOT NULL DEFAULT 'BIMESTRE',
    numero_periodos         INT           NOT NULL DEFAULT 4,
    conclusion_max          INT           NOT NULL DEFAULT 500,
    conclusion_siempre      BOOLEAN       NOT NULL DEFAULT FALSE,
    dias_cierre_anticipado  INT           NOT NULL DEFAULT 7,
    lengua_materna          VARCHAR(30)   NOT NULL DEFAULT 'Castellano',
    dre                     VARCHAR(80),
    ugel                    VARCHAR(80),
    actualizado_por         VARCHAR(60)   NOT NULL,
    creado_en               DATETIME(6)   NOT NULL,
    creado_por              VARCHAR(60)   NOT NULL,
    actualizado_en          DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuracion_evaluacion_anio UNIQUE (colegio_id, anio_escolar_id),
    CONSTRAINT uk_configuracion_evaluacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_configuracion_evaluacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_configuracion_evaluacion_anio FOREIGN KEY (anio_escolar_id, colegio_id)
        REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT fk_configuracion_evaluacion_version FOREIGN KEY (curriculo_version_id) REFERENCES curriculo_version (id),
    CONSTRAINT ck_configuracion_evaluacion_periodos CHECK ((tipo_periodo = 'BIMESTRE' AND numero_periodos = 4)
        OR (tipo_periodo = 'TRIMESTRE' AND numero_periodos = 3) OR (tipo_periodo = 'SEMESTRE' AND numero_periodos = 2)),
    CONSTRAINT ck_configuracion_evaluacion_valores CHECK (conclusion_max BETWEEN 100 AND 1000
        AND dias_cierre_anticipado BETWEEN 0 AND 15 AND CHAR_LENGTH(TRIM(lengua_materna)) >= 3
        AND (dre IS NULL OR CHAR_LENGTH(TRIM(dre)) >= 3) AND (ugel IS NULL OR CHAR_LENGTH(TRIM(ugel)) >= 3)),
    CONSTRAINT ck_configuracion_evaluacion_actor CHECK (actualizado_por NOT LIKE 'sistema%' AND creado_por NOT LIKE 'sistema%')
);

-- Periodos del año (sección 3.3). Las transiciones y quién las hace: trg_periodo_evaluacion_nace y _cambio.
--   PROGRAMADO --(sistema.academico el día de inicio, o Dirección con su firma antes)--> ABIERTO
--   ABIERTO --(Dirección con su firma, desde fin - dias_cierre_anticipado, con el anterior CERRADO)--> CERRADO
--   CERRADO: final, no se reabre. En ABIERTO el fin solo se extiende (M7). El número y el año no cambian (1143).
CREATE TABLE periodo_evaluacion (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    anio_escolar_id  BIGINT        NOT NULL,
    numero           INT           NOT NULL,
    nombre           VARCHAR(30)   NOT NULL,   -- «I bimestre»
    inicio           DATE          NOT NULL,
    fin              DATE          NOT NULL,
    estado           VARCHAR(10)   NOT NULL,   -- PROGRAMADO | ABIERTO | CERRADO
    abierto_por      VARCHAR(60),
    abierto_en       DATETIME(6),
    cerrado_por      VARCHAR(60),
    cerrado_en       DATETIME(6),
    actualizado_por  VARCHAR(60)   NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_periodo_evaluacion_numero UNIQUE (colegio_id, anio_escolar_id, numero),
    CONSTRAINT uk_periodo_evaluacion_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT uk_periodo_evaluacion_id_anio UNIQUE (id, anio_escolar_id),
    CONSTRAINT fk_periodo_evaluacion_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_periodo_evaluacion_anio FOREIGN KEY (anio_escolar_id, colegio_id) REFERENCES anio_escolar (id, colegio_id),
    CONSTRAINT ck_periodo_evaluacion_valores CHECK (numero BETWEEN 1 AND 4 AND inicio < fin
        AND CHAR_LENGTH(TRIM(nombre)) >= 3),
    CONSTRAINT ck_periodo_evaluacion_estado CHECK (estado IN ('PROGRAMADO', 'ABIERTO', 'CERRADO')
        AND ((estado = 'PROGRAMADO' AND abierto_por IS NULL AND abierto_en IS NULL)
            OR (estado <> 'PROGRAMADO' AND abierto_por IS NOT NULL AND abierto_en IS NOT NULL))
        AND ((estado = 'CERRADO' AND cerrado_por IS NOT NULL AND cerrado_en IS NOT NULL AND cerrado_por NOT LIKE 'sistema%')
            OR (estado <> 'CERRADO' AND cerrado_por IS NULL AND cerrado_en IS NULL))),
    CONSTRAINT ck_periodo_evaluacion_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_periodo_evaluacion_estado ON periodo_evaluacion (colegio_id, estado, inicio);

-- Áreas del catálogo de una asignación TUTOR o DOCENTE. SOLO INSERCIÓN y solo mientras la asignación está PROPUESTA y
-- todavía sin su solicitud enlazada (A2), por quien la propuso: se aprueban junto con ella (trg_asignacion_area_nace y la
-- versión nueva de trg_asignacion_docente_estado). Valen mientras la asignación esté VIGENTE.
CREATE TABLE asignacion_area (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    colegio_id       BIGINT        NOT NULL,
    asignacion_id    BIGINT        NOT NULL,
    area_id          BIGINT        NOT NULL,
    creado_en        DATETIME(6)   NOT NULL,
    creado_por       VARCHAR(60)   NOT NULL,
    actualizado_en   DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_asignacion_area UNIQUE (asignacion_id, area_id),
    CONSTRAINT uk_asignacion_area_id_colegio UNIQUE (id, colegio_id),
    CONSTRAINT fk_asignacion_area_colegio FOREIGN KEY (colegio_id) REFERENCES colegio (id),
    CONSTRAINT fk_asignacion_area_asignacion FOREIGN KEY (asignacion_id, colegio_id)
        REFERENCES asignacion_docente (id, colegio_id),
    CONSTRAINT fk_asignacion_area_area FOREIGN KEY (area_id) REFERENCES area_curricular (id),
    CONSTRAINT ck_asignacion_area_actor CHECK (creado_por NOT LIKE 'sistema%')
);
CREATE INDEX ix_asignacion_area_area ON asignacion_area (colegio_id, area_id);

-- Ley 29733 (H7): quién vio las notas de una sección, una libreta o la exportación para el SIAGIE. Los tres tipos de una
-- vez (las tandas 2 y 3 no vuelven a tocar este CHECK). Listas copiadas de V32, la última que las cambió (V33 y V34 no).
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_tipo;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_tipo CHECK (tipo IN ('FICHA_FAMILIA', 'FICHA_ALUMNO',
    'BUSQUEDA', 'MOROSOS', 'LLAMADA_CONTROL', 'IMPORTACION', 'APROBACION_CONTACTO', 'COBRO', 'LISTA_SECCION',
    'REPORTE_ASISTENCIA', 'NOMINA_SIAGIE', 'NOTAS_SECCION', 'LIBRETA', 'CALIFICACIONES_SIAGIE'));
ALTER TABLE acceso_dato_personal DROP CONSTRAINT ck_acceso_dato_objeto;
ALTER TABLE acceso_dato_personal ADD CONSTRAINT ck_acceso_dato_objeto CHECK (
    (tipo IN ('FICHA_FAMILIA', 'APROBACION_CONTACTO', 'LLAMADA_CONTROL', 'COBRO') AND familia_id IS NOT NULL)
    OR (tipo IN ('FICHA_ALUMNO', 'LIBRETA') AND alumno_id IS NOT NULL)
    OR tipo IN ('BUSQUEDA', 'MOROSOS', 'IMPORTACION', 'LISTA_SECCION', 'REPORTE_ASISTENCIA', 'NOMINA_SIAGIE',
        'NOTAS_SECCION', 'CALIFICACIONES_SIAGIE'));
