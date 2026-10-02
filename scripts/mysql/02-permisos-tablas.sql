-- Cuentas Claras · permisos por tabla de cc_app (paso 2 de 2). Ejecutar como administrador DESPUÉS de que Flyway
-- cree las tablas (MySQL no acepta un GRANT por tabla sobre una tabla que no existe).
-- Cada tabla nueva: agrega aquí su GRANT. Tablas financieras (pago, cuota...): nunca DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.usuario TO 'cc_app'@'%';
GRANT INSERT, UPDATE, DELETE ON cuentasclaras.usuario_rol TO 'cc_app'@'%';  -- @ElementCollection reescribe filas
GRANT INSERT ON cuentasclaras.evento_auditoria TO 'cc_app'@'%';              -- SIN UPDATE NI DELETE
GRANT UPDATE ON cuentasclaras.auditoria_cadena TO 'cc_app'@'%';              -- también lo exige SELECT ... FOR UPDATE

-- Sprint 2 · tanda 1: estructura académica, familias, apoderados, alumnos y matrículas.
-- Nada se borra (se desactiva, se retira o se mueve de sección): sin DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.anio_escolar TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.seccion TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.familia TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.apoderado TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.alumno TO 'cc_app'@'%';
GRANT INSERT, UPDATE ON cuentasclaras.matricula TO 'cc_app'@'%';

-- Sprint 2 · tanda 2: registro de importaciones de alumnos desde Excel. SOLO inserción (sin UPDATE ni DELETE).
GRANT INSERT ON cuentasclaras.importacion_alumnos TO 'cc_app'@'%';
