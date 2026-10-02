# MySQL en producción: usuarios y permisos

La aplicación usa **dos usuarios de MySQL**:

| Usuario | Lo usa | Permisos |
|---|---|---|
| `cc_migrador` | Flyway, al arrancar (`spring.flyway.user`) | Todo sobre la base `cuentasclaras`: crea y cambia tablas |
| `cc_app` | La aplicación (`spring.datasource.username`) | Lectura de todo y escritura **solo** donde hace falta. Sobre `evento_auditoria` solo puede **insertar**: ni UPDATE ni DELETE |

Así, aunque alguien robe la clave de la aplicación o encuentre una falla en el código, no puede editar ni borrar la bitácora de auditoría. Y si alguien la toca con otro usuario, la cadena HMAC lo detecta (pantalla Bitácora → «Verificar integridad»).

## Instalación (una sola vez)
Los scripts están en `scripts/mysql/`. Son los mismos que usa el job `mysql` del CI.

1. **Antes de la primera migración**, como administrador. Reemplaza `__CLAVE_MIGRADOR__` y `__CLAVE_APP__` por claves del gestor de secretos y **no guardes el archivo modificado**:
   ```bash
   sed -e 's/__CLAVE_MIGRADOR__/<clave-migrador>/' -e 's/__CLAVE_APP__/<clave-app>/' \
       scripts/mysql/01-usuarios.sql | mysql -h <host> -u root -p
   ```
   Crea la base `cuentasclaras` (utf8mb4), `cc_migrador` con todos los permisos sobre ella y `cc_app` solo con SELECT.
2. **Arranca la aplicación una vez.** Flyway crea las tablas con `cc_migrador`. La aplicación arranca con `cc_app` solo leyendo. El `VerificadorPermisosBaseDatos` ya comprueba que no puede editar la bitácora.
3. **Después de esa primera migración**, aplica los permisos por tabla. MySQL no acepta un GRANT sobre una tabla que todavía no existe:
   ```bash
   mysql -h <host> -u root -p < scripts/mysql/02-permisos-tablas.sql
   ```

| Tabla | Permisos de `cc_app` | Por qué |
|---|---|---|
| (todas) | SELECT | Leer |
| `usuario` | INSERT, UPDATE | Crear usuarios, intentos de ingreso, desactivar (nunca se borran) |
| `usuario_rol` | INSERT, UPDATE, DELETE | La colección de roles se reescribe al cambiarlos |
| `evento_auditoria` | INSERT | **Solo inserción** |
| `auditoria_cadena` | UPDATE | Avanzar el eslabón; MySQL también lo exige para `SELECT ... FOR UPDATE` |

## Cada migración nueva
- Si crea una tabla, agrega su GRANT en `scripts/mysql/02-permisos-tablas.sql` y aplícalo después de migrar.
- **Tablas financieras** (pago, cuota, comprobante, cierre de caja): INSERT y UPDATE (para anular con estado), **nunca DELETE**.
- El CI (job `mysql`) corre las migraciones y las pruebas con estos mismos permisos: si falta un GRANT, falla.

## Variables de entorno (perfil `prod`)
| Variable | Contenido |
|---|---|
| `DB_URL` | `jdbc:mysql://<host>:3306/cuentasclaras` |
| `DB_USUARIO`, `DB_CLAVE` | `cc_app` y su clave |
| `DB_MIGRADOR_USUARIO`, `DB_MIGRADOR_CLAVE` | `cc_migrador` y su clave |
| `AUDITORIA_CLAVE_HMAC` | Clave de la cadena de auditoría, de 32 caracteres o más. Si se pierde, la bitácora no se puede verificar |
| `CC_COLEGIO_ID`, `CC_PROMOTOR_USUARIO`, `CC_PROMOTOR_NOMBRE`, `CC_PROMOTOR_CLAVE` | Primer PROMOTOR, solo si no hay usuarios. Retira `CC_PROMOTOR_CLAVE` después del primer ingreso |

## Cómo comprobarlo
Conectado como `cc_app`, estas dos sentencias deben fallar con **ERROR 1142** (comando denegado):
```sql
UPDATE evento_auditoria SET ip = ip WHERE 1 = 0;
DELETE FROM evento_auditoria WHERE 1 = 0;
```
La aplicación lo comprueba sola al arrancar en `prod` (`VerificadorPermisosBaseDatos`). Si `cc_app` puede ejecutarlas, **no arranca** y el log dice qué revisar. Para desactivar esta comprobación en un entorno de pruebas existe `cuentasclaras.auditoria.exigir-permisos-restringidos=false`; nunca lo uses en producción.

Si la bitácora queda bloqueada por un evento falso, sigue `incidente-auditoria.md`.
