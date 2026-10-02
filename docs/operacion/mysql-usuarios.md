# MySQL en producción: usuarios y permisos

La aplicación usa **dos usuarios de MySQL**:

| Usuario | Lo usa | Permisos |
|---|---|---|
| `cc_migrador` | Flyway, al arrancar (`spring.flyway.user`) | Todo sobre la base `cuentasclaras`: crea y cambia tablas |
| `cc_app` | La aplicación (`spring.datasource.username`) | Lectura de todo y escritura **solo** donde hace falta. Sobre `evento_auditoria` solo puede **insertar**: ni UPDATE ni DELETE |

Así, aunque alguien robe la clave de la aplicación o encuentre una falla en el código, no puede editar ni borrar la bitácora de auditoría. Y si alguien la toca con otro usuario, la cadena HMAC lo detecta (pantalla Bitácora → «Verificar integridad»).

## Instalación (una sola vez)
Los scripts están en `scripts/mysql/`. Son los mismos que usa el job `mysql` del CI.

1. **Usuarios y base**, como administrador. Reemplaza `__CLAVE_MIGRADOR__` y `__CLAVE_APP__` por claves del gestor de secretos y **no guardes el archivo modificado**:
   ```bash
   sed -e 's/__CLAVE_MIGRADOR__/<clave-migrador>/' -e 's/__CLAVE_APP__/<clave-app>/' \
       scripts/mysql/01-usuarios.sql | mysql -h <host> -u root -p
   ```
   Crea la base `cuentasclaras` (utf8mb4), `cc_migrador` con todos los permisos sobre ella y `cc_app` solo con SELECT.
2. **Primera migración** (ver «Despliegue»): crea las tablas con `cc_migrador`.
3. **Permisos por tabla**, después de esa primera migración. MySQL no acepta un GRANT sobre una tabla que todavía no existe:
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
| `anio_escolar`, `seccion`, `familia`, `apoderado`, `alumno`, `matricula` | INSERT, UPDATE | Nada se borra: se desactiva, se retira o se mueve |
| `importacion_alumnos` | INSERT | **Solo inserción** |
| `plan_pension` | INSERT y UPDATE **por columna** (estado, envío, aprobación, reemplazo; montos y fechas) | Año, nivel, versión y motivo inmutables. Los montos y fechas solo cambian en BORRADOR: lo impide un trigger |
| `lote_saldo_inicial` | INSERT y UPDATE **por columna** (estado, envío, confirmación, devolución, descarte) | Corte, referencia y total declarado inmutables |
| `linea_saldo_inicial` | INSERT y UPDATE **solo** de `quitada` | Una deuda no se edita: se quita (y solo con el lote en preparación: trigger) |
| `solicitud_cambio` | INSERT y UPDATE **solo** de su resolución | Tipo, datos, motivo y solicitante inmutables |
| `cuota` | INSERT y UPDATE **solo** de `estado, monto_pagado, monto_descuento, obligacion, anulacion_*, anulada_en, actualizado_en, version` | El monto, la fecha de vencimiento, el alumno, el origen y la clave no se cambian ni por SQL (error 1143). Lo pagado y lo descontado solo pueden ser la suma de su libro (trigger) |
| `serie_comprobante` | INSERT y UPDATE **solo** de `ultimo_numero` | La serie no cambia; el número avanza de uno en uno (trigger) |
| `comprobante` | INSERT y UPDATE **solo** del envío al OSE (`estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf`) | Serie, número, receptor y total no cambian (1143); el número es el siguiente de la serie (trigger) |
| `comprobante_linea`, `aplicacion_pago` | INSERT | **Solo inserción**: el libro de pagos no se edita ni se borra (1142) |
| `caja_diaria` | INSERT y UPDATE **solo** de `estado, cierres, conteos, primer_conteo` | Cajero, fecha y fondo fijo no cambian (1143). Hasta la tanda 3 (cierre) la caja no se cierra por SQL (trigger) |
| `pago` | INSERT y UPDATE **solo** de `estado, operacion_vigente` | Familia, caja, medio, operación, total, vuelto y comprobante no cambian (1143); nace VIGENTE con su boleta por el mismo total (trigger) |
| `anulacion_pago`, `ajuste_cuota` | INSERT | **Solo inserción** (tanda 2): la anulación aprobada de un pago y cada ajuste de un descuento no se editan ni se borran (1142) |
| `descuento` | INSERT y UPDATE **solo** de `estado, resuelto_por, resuelto_en` | Alumno, tipo, valor, cuotas, total, motivo y sustento no cambian (1143); nace SOLICITADO y, resuelto, no cambia (trigger) |

## Triggers (paso 3, después de los permisos)
Los aplica `cc_migrador` (no van en Flyway: H2 no los soporta):
```bash
# Una sola vez por servidor, como administrador (el binlog está activo):
mysql -u root -p -e "SET PERSIST log_bin_trust_function_creators = 1"
mysql -h <host> -u cc_migrador -p cuentasclaras < scripts/mysql/03-triggers.sql
```
- Un plan fuera de BORRADOR no cambia montos, fechas ni editores; un plan cerrado no cambia de estado.
- Planes y lotes nacen en BORRADOR; un lote confirmado o descartado no cambia.
- Las líneas solo se agregan o se quitan con su lote en preparación.
- Sprint 3 (caja): una cuota nace PENDIENTE y lo pagado es siempre la suma de `aplicacion_pago` (**no existe una cuota
  PAGADA sin pago**); las series nacen en 0 y avanzan de uno en uno; el comprobante usa el número que la serie acaba de
  asignar; la caja nace ABIERTA; el pago nace VIGENTE con su boleta o factura por el mismo total; una aplicación solo va a
  cuotas de la familia del pago y no supera su total.
- Sprint 3, tanda 2 (anulaciones y descuentos, requiere V10): lo descontado de una cuota es siempre la suma de
  `ajuste_cuota`; un ajuste solo nace de un descuento APROBADO que incluye esa cuota; un pago solo pasa a ANULADO con su
  `anulacion_pago` registrada (y nunca vuelve a VIGENTE); una reversión solo existe con esa anulación y por el monto
  exacto de la aplicación original; la anulación corresponde al pago vigente, a su cajero y a una nota de crédito que
  anula su comprobante por el mismo total.
- **Los triggers del sprint 3 van por tanda** (`docs/arquitectura/sprint-3-caja.md`, sección 6.3): un trigger que nombra
  una tabla que aún no existe hace fallar con 1146 todo UPDATE sobre su tabla. El script del repositorio es siempre el de
  la última migración publicada: aplícalo DESPUÉS de migrar, nunca antes.

Si `log_bin_trust_function_creators` no puede activarse, aplica el script como administrador.
La aplicación en `prod` **no arranca** si faltan (los comprueba con un INSERT imposible que el trigger rechaza: 1644).
También acepta 1142 (cc_app sin INSERT en esa tabla, como en la fase 1 antes del paso 2): sin permiso de escritura no
hay nada que el trigger deba frenar. Cualquier otro código (la FK o un CHECK, que MySQL evalúa después del trigger)
significa que falta el trigger.

## Despliegue (cada versión)
La aplicación **no migra** en producción (`spring.flyway.enabled: false`) y **nunca** recibe las credenciales de `cc_migrador`. Cada despliegue tiene dos pasos separados:

1. **Migrar**: el mismo jar en modo migración. Aplica Flyway con `cc_migrador` y termina, sin servidor web:
   ```bash
   DB_URL="jdbc:mysql://<host>:3306/cuentasclaras" \
   DB_MIGRADOR_USUARIO="cc_migrador" DB_MIGRADOR_CLAVE="..." \
   java -jar cuentas-claras.jar migrar
   ```
   Si una migración crea una tabla, aplica después su GRANT (paso 3 de la instalación) y vuelve a aplicar
   `03-triggers.sql` (es idempotente).
2. **Arrancar** la aplicación con `SPRING_PROFILES_ACTIVE=prod` y **solo** `DB_USUARIO=cc_app` / `DB_CLAVE`. Antes de aceptar peticiones comprueba que no falten migraciones, que `cc_app` no pueda editar ni borrar la bitácora ni borrar cuotas (error 1142) y que no pueda cambiar el monto de una cuota ni las columnas inmutables de planes, lotes, líneas y solicitudes (error
1143, o 1142 si no tiene ningún UPDATE sobre la tabla), además de los triggers del paso 3. Si algo falla, **no arranca**.

## Cada migración nueva
- Si crea una tabla, agrega su GRANT en `scripts/mysql/02-permisos-tablas.sql` y aplícalo después de migrar.
- **Tablas financieras** (pago, cuota, comprobante, cierre de caja): INSERT y UPDATE (para anular con estado), **nunca DELETE**.
- El CI (job `mysql`) corre las migraciones y las pruebas con estos mismos permisos: si falta un GRANT, falla.

## Variables de entorno (perfil `prod`)
| Variable | Contenido |
|---|---|
| `DB_URL` | `jdbc:mysql://<host>:3306/cuentasclaras` |
| `DB_USUARIO`, `DB_CLAVE` | `cc_app` y su clave |
| `DB_MIGRADOR_USUARIO`, `DB_MIGRADOR_CLAVE` | `cc_migrador` y su clave. **Solo** para `java -jar cuentas-claras.jar migrar`, nunca en el entorno de la aplicación |
| `AUDITORIA_CLAVE_HMAC` | Clave de la cadena de auditoría, de 32 caracteres o más. Custodia: `custodia-clave-auditoria.md` |
| `CC_PROXIES_INTERNOS` | Expresión regular con las IP de tus proxies inversos. Solo de ellos se acepta la cabecera X-Forwarded-For. Por defecto: loopback y redes privadas (10.x, 172.16-31.x, 192.168.x) |
| `CC_COLEGIO_ID`, `CC_PROMOTOR_USUARIO`, `CC_PROMOTOR_NOMBRE`, `CC_PROMOTOR_CLAVE` | Primer PROMOTOR, solo si no hay usuarios. Retira `CC_PROMOTOR_CLAVE` después del primer ingreso |

## Cómo comprobarlo
Conectado como `cc_app`, estas sentencias deben fallar con **ERROR 1142** (comando denegado):
```sql
UPDATE evento_auditoria SET ip = ip WHERE 1 = 0;
DELETE FROM evento_auditoria WHERE 1 = 0;
DELETE FROM cuota WHERE 1 = 0;
```
Y estas, con **ERROR 1143** (columna denegada: el GRANT es por columna):
```sql
UPDATE cuota SET monto = monto WHERE 1 = 0;
UPDATE plan_pension SET numero_version = numero_version WHERE 1 = 0;
UPDATE lote_saldo_inicial SET total_declarado = total_declarado WHERE 1 = 0;
UPDATE linea_saldo_inicial SET monto = monto WHERE 1 = 0;
UPDATE solicitud_cambio SET datos = datos WHERE 1 = 0;
```
Sprint 3 (caja): `DELETE` sobre `serie_comprobante`, `comprobante`, `comprobante_linea`, `caja_diaria`, `pago` y
`aplicacion_pago` da 1142; `UPDATE comprobante_linea|aplicacion_pago SET version = version` da 1142 (solo inserción);
`UPDATE pago SET total = total`, `UPDATE comprobante SET numero = numero`, `UPDATE serie_comprobante SET serie = serie`
y `UPDATE caja_diaria SET fecha = fecha` dan 1143; y `UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto` da
1644 (trigger `trg_cuota_libro`).
Tanda 2: `DELETE` sobre `anulacion_pago`, `descuento` y `ajuste_cuota` da 1142; `UPDATE anulacion_pago|ajuste_cuota
SET version = version` da 1142; `UPDATE descuento SET valor = valor` (o `cuotas`, `total_estimado`) da 1143; un
`descuento` que nace APROBADO y un `ajuste_cuota` sin descuento aprobado dan 1644.
La aplicación lo comprueba sola al arrancar en `prod` (`VerificadorPermisosBaseDatos`), antes de aceptar peticiones. Si `cc_app` puede ejecutarlas, **no arranca** y el log dice qué revisar. Esta comprobación no se puede desactivar.

Si la bitácora queda bloqueada por un evento falso, sigue `incidente-auditoria.md`.
