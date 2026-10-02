-- La clave temporal (alta o restablecimiento) vence; y queda quién restableció la clave y cuándo,
-- para avisar al titular en su página de inicio.
ALTER TABLE usuario ADD COLUMN clave_temporal_hasta DATETIME(6);
ALTER TABLE usuario ADD COLUMN clave_restablecida_por VARCHAR(60);
ALTER TABLE usuario ADD COLUMN clave_restablecida_en DATETIME(6);
