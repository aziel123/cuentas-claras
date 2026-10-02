-- Cuentas Claras · usuarios de MySQL (paso 1 de 2). Ejecutar como administrador ANTES de la primera migración.
-- Reemplaza __CLAVE_MIGRADOR__ y __CLAVE_APP__ con claves del gestor de secretos (nunca las subas al repositorio).
CREATE DATABASE IF NOT EXISTS cuentasclaras CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Migrador: solo lo usa Flyway (spring.flyway.user). Puede crear y cambiar tablas.
CREATE USER IF NOT EXISTS 'cc_migrador'@'%' IDENTIFIED BY '__CLAVE_MIGRADOR__';
GRANT ALL PRIVILEGES ON cuentasclaras.* TO 'cc_migrador'@'%';

-- Aplicación: solo lee en general. Los permisos de escritura van por tabla (02-permisos-tablas.sql).
CREATE USER IF NOT EXISTS 'cc_app'@'%' IDENTIFIED BY '__CLAVE_APP__';
GRANT SELECT ON cuentasclaras.* TO 'cc_app'@'%';
