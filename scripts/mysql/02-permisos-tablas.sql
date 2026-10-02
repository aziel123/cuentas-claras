-- Cuentas Claras · permisos por tabla de cc_app (paso 2 de 2). Ejecutar como administrador DESPUÉS de que Flyway
-- cree las tablas (MySQL no acepta un GRANT por tabla sobre una tabla que no existe).
-- Cada tabla nueva: agrega aquí su GRANT. Tablas financieras (pago, cuota...): nunca DELETE.
GRANT INSERT, UPDATE ON cuentasclaras.usuario TO 'cc_app'@'%';
GRANT INSERT, UPDATE, DELETE ON cuentasclaras.usuario_rol TO 'cc_app'@'%';  -- @ElementCollection reescribe filas
GRANT INSERT ON cuentasclaras.evento_auditoria TO 'cc_app'@'%';              -- SIN UPDATE NI DELETE
GRANT UPDATE ON cuentasclaras.auditoria_cadena TO 'cc_app'@'%';              -- también lo exige SELECT ... FOR UPDATE
