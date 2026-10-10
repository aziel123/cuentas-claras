-- Cuentas Claras · paso único del sprint 7 para una base que YA EXISTE (creada con el 01-usuarios.sql de un sprint
-- anterior). Ejecutar UNA vez como administrador, con la aplicación detenida, ANTES de 02-permisos-tablas.sql y
-- 03-triggers.sql. Es idempotente. Reemplaza __CLAVE_SISTEMA__ y __CLAVE_RESPALDO__ con claves del gestor de secretos.
--
-- Tanda 1 (respaldos): el usuario cc_respaldo. Sus GRANT (SELECT, SHOW VIEW, INSERT en respaldo y EXECUTE de
-- huellas_objetos) los da 02.
CREATE USER IF NOT EXISTS 'cc_respaldo'@'%' IDENTIFIED BY '__CLAVE_RESPALDO__';
-- Tanda 2 (base de datos endurecida): el usuario cc_sistema (procesos sistema.* e identidad) y el rol cc_negocio. 02
-- empieza con REVOKE ALL: el SELECT general y los GRANT que cc_app tenía directos pasan al rol, y cc_app pierde la
-- escritura de usuario, usuario_rol, semilla_muestreo, muestra_llamada, resumen_diario, las huellas, la pasarela y el
-- UPDATE del envío al OSE y de los mensajes.
CREATE USER IF NOT EXISTS 'cc_sistema'@'%' IDENTIFIED BY '__CLAVE_SISTEMA__';
CREATE ROLE IF NOT EXISTS 'cc_negocio';
-- Si las cuentas se restringen por host en prod (por ejemplo 'cc_app'@'10.0.%'), usa el mismo host aquí y en 02.
