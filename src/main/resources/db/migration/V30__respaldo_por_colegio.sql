-- Sprint 8 · tanda 3 (docs/arquitectura/sprint-8-lista-para-vender.md, secciones 3.8 y 5; decisión 131). «Faltan filas»
-- por colegio en una instalación compartida (H3): el respaldo dice qué colegios tienen filas faltantes y la Promotoría de
-- CADA colegio afectado resuelve la suya. Compatible con MySQL 8.0.19+ y con H2 en modo MySQL. Nada se borra.
--
-- respaldo.colegios_faltan: arreglo JSON de ids de colegio («[2,5]», sin espacios, cada id una vez) cuando la comparación
-- es FALTAN_FILAS; NULL si no falta nada. Lo escribe solo cc_respaldo (respaldar.sh) con el INSERT de siempre (sin GRANT
-- nuevos). Las filas de las tablas sin colegio, el ancla de la bitácora y las del colegio de plataforma afectan a TODOS
-- los colegios. Un FALTAN_FILAS anterior a V30 (colegios_faltan NULL) sigue siendo «de toda la base»: lo cierra cualquier
-- resolución posterior, como en el sprint 7. En MySQL lo exigen trg_respaldo_registro y trg_resolucion_respaldo_registro.
ALTER TABLE respaldo ADD COLUMN colegios_faltan VARCHAR(1000);
-- Sin corchetes dentro de la expresión regular (MySQL e H2 los escapan distinto): se comprueban con LEFT y RIGHT.
ALTER TABLE respaldo ADD CONSTRAINT ck_respaldo_colegios_faltan CHECK (colegios_faltan IS NULL
    OR (comparacion = 'FALTAN_FILAS' AND LEFT(colegios_faltan, 1) = '[' AND RIGHT(colegios_faltan, 1) = ']'
        AND REGEXP_LIKE(SUBSTRING(colegios_faltan, 2, CHAR_LENGTH(colegios_faltan) - 2), '^[0-9]{1,19}(,[0-9]{1,19})*$', 'c')));

-- Una resolución por respaldo Y colegio (antes, una por respaldo). El índice nuevo empieza por respaldo_id: se crea ANTES
-- de quitar el anterior (MySQL no deja quitar el índice de una FK sin otro que lo reemplace). La FK al respaldo se quita y
-- se vuelve a crear igual: H2 la apoyaba en el índice único anterior y lo conservaba (seguía exigiendo uno por respaldo).
ALTER TABLE resolucion_respaldo ADD CONSTRAINT uk_resolucion_respaldo_colegio UNIQUE (respaldo_id, colegio_id);
ALTER TABLE resolucion_respaldo DROP CONSTRAINT fk_resolucion_respaldo_respaldo;
ALTER TABLE resolucion_respaldo DROP CONSTRAINT uk_resolucion_respaldo;
ALTER TABLE resolucion_respaldo ADD CONSTRAINT fk_resolucion_respaldo_respaldo FOREIGN KEY (respaldo_id) REFERENCES respaldo (id);
