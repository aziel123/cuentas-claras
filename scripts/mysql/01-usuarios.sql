-- Cuentas Claras · usuarios de MySQL (paso 1 de 3). Ejecutar como administrador ANTES de la primera migración.
-- Reemplaza __CLAVE_MIGRADOR__, __CLAVE_APP__, __CLAVE_SISTEMA__ y __CLAVE_RESPALDO__ con claves del gestor de secretos
-- (nunca las subas al repositorio). Para una base que ya existe (creada antes del sprint 7) aplica
-- 04-una-vez-sprint-7.sql. Los permisos de cc_app, cc_sistema y cc_respaldo los da (y los quita) SOLO
-- 02-permisos-tablas.sql.
CREATE DATABASE IF NOT EXISTS cuentasclaras CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Migrador: solo lo usa Flyway (java -jar ... migrar) y aplica 03-triggers.sql. Puede crear y cambiar tablas.
CREATE USER IF NOT EXISTS 'cc_migrador'@'%' IDENTIFIED BY '__CLAVE_MIGRADOR__';
GRANT ALL PRIVILEGES ON cuentasclaras.* TO 'cc_migrador'@'%';

-- Aplicación: las peticiones de las personas. Hasta que se aplique 02 solo lee (así la fase 1 del despliegue valida el
-- esquema con Hibernate); 02 empieza con REVOKE ALL y le da el rol cc_negocio.
CREATE USER IF NOT EXISTS 'cc_app'@'%' IDENTIFIED BY '__CLAVE_APP__';
GRANT SELECT ON cuentasclaras.* TO 'cc_app'@'%';

-- Sprint 7, tanda 2: los procesos sistema.* y la identidad (ingreso, sesiones, claves, roles y altas). El nombre es FIJO:
-- los triggers lo reconocen con cc_es_sistema() y el verificador de prod lo exige. Igual que cc_app, solo lee hasta 02.
CREATE USER IF NOT EXISTS 'cc_sistema'@'%' IDENTIFIED BY '__CLAVE_SISTEMA__';
GRANT SELECT ON cuentasclaras.* TO 'cc_sistema'@'%';

-- Sprint 7, tanda 1: respaldos (scripts/respaldo/respaldar.sh). Solo lectura más el registro del respaldo; sus GRANT
-- van en 02-permisos-tablas.sql. El nombre es FIJO: trg_respaldo_registro lo reconoce con SESSION_USER().
CREATE USER IF NOT EXISTS 'cc_respaldo'@'%' IDENTIFIED BY '__CLAVE_RESPALDO__';

-- Sprint 7, tanda 2: el rol con los permisos de negocio (02 se los da; lo reciben cc_app y cc_sistema).
CREATE ROLE IF NOT EXISTS 'cc_negocio';
