-- Cuentas Claras · usuarios de MySQL (paso 1 de 3). Ejecutar como administrador ANTES de la primera migración.
-- Reemplaza __CLAVE_MIGRADOR__, __CLAVE_APP__ y __CLAVE_RESPALDO__ con claves del gestor de secretos (nunca las subas
-- al repositorio). Para una base que ya existe (creada antes del sprint 7) aplica 04-una-vez-sprint-7.sql.
CREATE DATABASE IF NOT EXISTS cuentasclaras CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Migrador: solo lo usa Flyway (spring.flyway.user). Puede crear y cambiar tablas.
CREATE USER IF NOT EXISTS 'cc_migrador'@'%' IDENTIFIED BY '__CLAVE_MIGRADOR__';
GRANT ALL PRIVILEGES ON cuentasclaras.* TO 'cc_migrador'@'%';

-- Aplicación: solo lee en general. Los permisos de escritura van por tabla (02-permisos-tablas.sql).
CREATE USER IF NOT EXISTS 'cc_app'@'%' IDENTIFIED BY '__CLAVE_APP__';
GRANT SELECT ON cuentasclaras.* TO 'cc_app'@'%';

-- Sprint 7, tanda 1: respaldos (scripts/respaldo/respaldar.sh). Solo lectura más el registro del respaldo; sus GRANT
-- van en 02-permisos-tablas.sql. El nombre es FIJO: trg_respaldo_registro lo reconoce con SESSION_USER().
CREATE USER IF NOT EXISTS 'cc_respaldo'@'%' IDENTIFIED BY '__CLAVE_RESPALDO__';
