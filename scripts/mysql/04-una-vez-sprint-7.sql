-- Cuentas Claras · paso único del sprint 7 para una base que YA EXISTE (creada con el 01-usuarios.sql de un sprint
-- anterior). Ejecutar UNA vez como administrador, con la aplicación detenida, ANTES de 02-permisos-tablas.sql y
-- 03-triggers.sql. Es idempotente. Reemplaza __CLAVE_RESPALDO__ con una clave del gestor de secretos.
--
-- Tanda 1 (respaldos): el usuario cc_respaldo. Sus GRANT (SELECT, SHOW VIEW e INSERT en respaldo) los da 02.
-- (La tanda 2 agrega aquí cc_sistema y el rol cc_negocio.)
CREATE USER IF NOT EXISTS 'cc_respaldo'@'%' IDENTIFIED BY '__CLAVE_RESPALDO__';
