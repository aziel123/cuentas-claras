# Incidente: la bitácora de auditoría quedó bloqueada

Qué hacer si alguien insertó directamente en la base un evento falso con la secuencia siguiente (N+1) de `evento_auditoria`. La aplicación no puede borrarlo (no tiene permiso de DELETE) y, mientras exista, **ninguna operación auditada funciona**. Eso incluye el inicio de sesión. Es un fallo cerrado: el sistema se detiene antes que seguir sin auditoría.

## 1. Cómo detectarlo
- **Síntoma:** nadie puede ingresar. Todo intento de ingreso, con la clave correcta o no, y toda operación que se audita terminan en la página «Algo salió mal».
- **En el log de la aplicación:** errores repetidos con `uk_evento_auditoria_secuencia` ("Duplicate entry" en MySQL o "Unique index or primary key violation" en H2). Si alguien corrió la verificación de integridad, aparece también `ALERTA: la bitácora de auditoría fue alterada`.
- **Confirmación en la base** (solo lectura):
  ```sql
  SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena;
  SELECT * FROM evento_auditoria
   WHERE secuencia > (SELECT ultima_secuencia FROM auditoria_cadena);
  ```
  Si la segunda consulta devuelve filas, son eventos que la aplicación nunca registró.

## 2. A quién avisar (de inmediato)
1. **Promotoría** (dueña del colegio) y **Dirección**.
2. El **responsable técnico** de la plataforma.
3. No informes al personal con acceso a caja ni a quien tenga credenciales de la base hasta preservar la evidencia: podría ser quien lo hizo.
4. Si se sospecha fraude, el **asesor legal** del colegio.

## 3. Preservar la evidencia ANTES de tocar nada
- No borres ni edites ninguna fila todavía.
- Exporta las tablas completas con un usuario de solo lectura:
  `mysqldump --single-transaction cuentasclaras evento_auditoria auditoria_cadena usuario usuario_rol > evidencia-AAAAMMDD-HHMM.sql`
- Calcula su huella (`sha256sum evidencia-*.sql`) y anótala en el acta.
- Guarda una copia de los logs de la aplicación y, si existen, del binlog y del log general de MySQL del periodo.
- Anota quién tiene credenciales de base de datos (`cc_migrador`, DBA, proveedor de hosting) y los accesos recientes.
- Guarda las copias fuera del servidor (por ejemplo, un USB en custodia de Promotoría), con fecha, hora y firma de quien las tomó.

## 4. Restablecer el servicio
Solo con un **acta firmada** por Promotoría y el responsable técnico, y con el usuario `cc_migrador`, nunca con `cc_app`:
1. Mueve los eventos ajenos a una tabla de cuarentena. No se pierden: quedan como evidencia.
   ```sql
   CREATE TABLE evento_auditoria_cuarentena AS
     SELECT * FROM evento_auditoria WHERE secuencia > (SELECT ultima_secuencia FROM auditoria_cadena);
   DELETE FROM evento_auditoria WHERE secuencia > (SELECT ultima_secuencia FROM auditoria_cadena);
   ```
2. Si además alteraron `auditoria_cadena`, devuélvela al último evento legítimo, que es el último cuyo hash verifica. El responsable técnico lo identifica con la verificación de integridad.
3. Cambia las claves de `cc_migrador` y `cc_app` y revisa los permisos (ver `mysql-usuarios.md`, paso 9 del sprint 1).
4. Comprueba que se puede ingresar y ejecuta **Verificar integridad**, que debe salir "íntegra". La pantalla llega en el paso 9; mientras tanto la verificación la corre el responsable técnico.
5. Registra el incidente en el acta: hora de detección, evidencia tomada, filas puestas en cuarentena y acciones siguientes.
