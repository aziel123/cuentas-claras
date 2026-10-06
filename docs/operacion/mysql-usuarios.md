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
| `cuota` | INSERT y UPDATE **solo** de `estado, monto_pagado, monto_descuento, obligacion, anulacion_*, anulada_en, anulacion_solicitud_id, actualizado_en, version` | El monto, la fecha de vencimiento, el alumno, el origen y la clave no se cambian ni por SQL (error 1143). Lo pagado y lo descontado solo pueden ser la suma de su libro; se anula solo con su solicitud APROBADA enlazada por id (trigger) |
| `serie_comprobante` | INSERT y UPDATE **solo** de `ultimo_numero` | La serie no cambia; el número avanza de uno en uno (trigger) |
| `comprobante` | INSERT y UPDATE **solo** del envío al OSE (`estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf`) | Serie, número, receptor y total no cambian (1143); el número es el siguiente de la serie; el resultado del envío se registra una vez, desde PENDIENTE, y ACEPTADO exige hash y respuesta (trigger). Con el OSE real, los envíos los registrará un usuario de proceso aparte |
| `comprobante_linea`, `aplicacion_pago` | INSERT | **Solo inserción**: el libro de pagos no se edita ni se borra (1142) |
| `caja_diaria` | INSERT y UPDATE **solo** de `estado, cierres, conteos, primer_conteo, reapertura_solicitud_id` | Cajero, fecha y fondo fijo no cambian (1143). Se cierra solo con su cierre registrado, el primer conteo no se reescribe y se reabre solo con SU solicitud de reapertura APROBADA (enlazada por id, una vez), resuelta el día de la caja por otra persona y sin depósito (trigger) |
| `pago` | INSERT y UPDATE **solo** de `estado, operacion_vigente` | Familia, caja, medio, operación, total, vuelto y comprobante no cambian (1143); nace VIGENTE con su boleta por el mismo total (trigger) |
| `anulacion_pago`, `ajuste_cuota` | INSERT | **Solo inserción** (tanda 2): la anulación aprobada de un pago y cada ajuste de un descuento no se editan ni se borran (1142) |
| `descuento` | INSERT y UPDATE **solo** de `estado, resuelto_por, resuelto_en` | Alumno, tipo, valor, cuotas, total, motivo y sustento no cambian (1143); nace SOLICITADO y, resuelto, no cambia (trigger) |
| `cierre_caja` | INSERT y UPDATE **solo** de `estado, revisado_por, revisado_en, comentario_revision` | El conteo, el esperado y la diferencia no cambian (1143); el esperado es el del libro y lo registra la cajera de una caja abierta; revisado, no cambia (trigger) |
| `deposito_caja`, `verificacion_bancaria` | INSERT | **Solo inserción** (tanda 3): el depósito y la verificación contra el banco no se editan ni se borran (1142); verifica alguien que no cobró ni depositó, y «Encontrado» exige lo visto en el banco (operación, fecha y monto) que coincida (trigger) |
| `reembolso` | INSERT | **Solo inserción** (correcciones del sprint 3): el reembolso de una devolución, por su monto y el medio del pago; no lo registra la cajera del pago (CHECK y trigger) |
| `triggers_instalados()` (función) | EXECUTE | Nombres de los triggers del esquema (`SQL SECURITY DEFINER`; una vista no sirve porque MySQL 8 filtra `information_schema` con los permisos de quien consulta): el arranque en prod comprueba que estén todos |
| `apoderado` (RUC) | (INSERT, UPDATE de la fila) | El RUC y la razón social solo cambian con SU solicitud `DATOS_FACTURACION` aprobada (trigger) |
| `archivo_cargado` | INSERT | **Solo inserción** (sprint 4, tanda 2): el archivo original del banco, con su SHA-256, es evidencia (1142) |
| `lote_recaudacion` | INSERT y UPDATE **solo** de estado, confirmación a ciegas, intentos, aplicación y rechazo | El archivo, su SHA-256, el banco, las fechas, la cantidad de líneas y el total no cambian (1143). Lo confirma otra persona con el total a ciegas igual al del archivo (CHECK); los intentos solo suben de uno en uno y se aplica completo (trigger) |
| `linea_recaudacion` | INSERT y UPDATE **solo** de estado, motivo de la excepción y devolución | Monto, fecha, código, alumno, cuota y operación no cambian (1143); entra PENDIENTE a un lote CARGADO y en sus fechas; APLICADA exige su pago; DEVUELTA exige la devolución aprobada por otra persona (trigger) |

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
- Sprint 3, tanda 3 (cierre, depósito y verificación, requiere V11): la caja se cierra solo con su `cierre_caja`
  registrado; el primer conteo a ciegas no se reescribe ni se reinician los intentos; el cierre lo registra la cajera de
  la caja abierta con el esperado del libro (fondo + efectivo VIGENTE); verifica contra el banco alguien que no cobró ni
  depositó, y solo pagos digitales o depósitos.
- Correcciones del sprint 3 (requiere V12; `docs/arquitectura/sprint-3-correcciones.md`):
  - **Reapertura** (corrige lo que decía antes este documento: el trigger aceptaba cualquier solicitud PENDIENTE de la
    caja, sin probar que alguien la aprobara): la bandeja aprueba la solicitud ANTES de aplicarla y la caja guarda su
    id en `reapertura_solicitud_id`; el trigger exige que esa solicitud sea `REAPERTURA_CAJA` de esta caja, esté
    APROBADA, se haya resuelto el día de la caja por alguien que no es su cajero y no se haya usado antes (UNIQUE).
  - **Anulación de cuota**: exige su solicitud APROBADA (`ANULACION_CUOTA` de la cuota o el ingreso tardío de su
    matrícula) enlazada en `anulacion_solicitud_id`, resuelta el mismo día por quien figura como aprobador.
  - **Resoluciones inmutables**: una solicitud, un descuento y un cierre revisado no cambian su estado ni quién, cuándo y
    con qué comentario se resolvieron.
  - **Reemplazo**: solo de un pago anulado por CORRECCIÓN (nunca por devolución).
  - **Verificación bancaria**: solo pagos VIGENTES; «Encontrado» exige la operación, la fecha y el monto vistos en el
    banco, y que coincidan.
  - **Reembolso** y **RUC del apoderado**: ver la tabla de permisos.
  - **Envío al OSE**: el resultado se registra una vez, desde PENDIENTE.
  - Límite: `cc_app` escribe los nombres de usuario como texto, así que la base no puede probar QUIÉN aprobó; ese
    riesgo lo cubren la bitácora con HMAC y el aislamiento de credenciales.
- **Los triggers del sprint 3 van por tanda** (`docs/arquitectura/sprint-3-caja.md`, sección 6.3): un trigger que nombra
  una tabla que aún no existe hace fallar con 1146 todo UPDATE sobre su tabla. El script del repositorio es siempre el de
  la última migración publicada: aplícalo DESPUÉS de migrar, nunca antes.

Si `log_bin_trust_function_creators` no puede activarse, aplica el script como administrador.
La aplicación en `prod` **no arranca** si falta alguno:
- compara lo que devuelve la función `cuentasclaras.triggers_instalados()` (la crea `02-permisos-tablas.sql`) con la lista completa de
  `03-triggers.sql` (`VerificadorPermisosBaseDatos.TRIGGERS_ESPERADOS`; una prueba exige que coincidan). Así detecta
  también los BEFORE UPDATE, que un INSERT imposible no prueba. Sin la función, tampoco arranca (salvo en la fase 1,
  cuando `cc_app` todavía solo puede leer: entonces no hay nada que un trigger deba frenar);
- además prueba varios con un INSERT imposible que el trigger rechaza (1644). Acepta 1142 (cc_app sin INSERT en esa
  tabla): sin permiso de escritura no hay nada que el trigger deba frenar. Cualquier otro código (la FK o un CHECK, que
  MySQL evalúa después del trigger) significa que falta el trigger.
Un trigger nuevo se agrega en `03-triggers.sql` y en `TRIGGERS_ESPERADOS`.

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

## Pasarela simulada y perfil `piloto` (sprint 4)
- La tabla `configuracion_bd` (V13) la escribe **solo el DBA**; `cc_app` solo la lee. En **producción no existe** la fila
  `pasarela_simulada`: el trigger `trg_orden_pago_nace` rechaza toda orden de la pasarela simulada y la aplicación en
  `prod` no arranca si la fila existe o si la base admite esa orden.
- En la base del **piloto** (otra base, otro despliegue, perfil `piloto`) el DBA la registra con `cc_migrador`:
  ```sql
  INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('pasarela_simulada', 'PERMITIDA', NOW(6));
  ```
  Sin esa fila, el perfil `piloto` con la pasarela simulada no arranca. El piloto exige además
  `PASARELA_SIMULADA_SECRETO` propio (no el de desarrollo) y nunca se combina con `prod`, `dev` ni `test`.
- Pasarela real: `PASARELA_PROVEEDOR` (por defecto `NINGUNA`: sin pago en línea). Hoy solo se acepta `CULQI` con
  `PASARELA_LLAVE_SECRETA` (`sk_live_` en prod, `sk_test_` fuera de prod), `PASARELA_WEBHOOK_USUARIO` y
  `PASARELA_WEBHOOK_CLAVE`, y su adaptador todavía no está integrado: mientras tanto, déjalo en `NINGUNA`.
- OSE real: `COMPROBANTES_PROVEEDOR=NUBEFACT` con `NUBEFACT_RUTA` (https de `api.nubefact.com`) y `NUBEFACT_TOKEN`. Fuera
  de prod solo con `cuentasclaras.comprobantes.permitir-real-fuera-de-prod: true` (cuenta DEMO).

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
Tanda 3: `DELETE` sobre `cierre_caja`, `deposito_caja` y `verificacion_bancaria` da 1142; `UPDATE deposito_caja|
verificacion_bancaria SET version = version` da 1142; `UPDATE cierre_caja SET contado = contado` da 1143; un
`cierre_caja` de una caja inexistente y una `verificacion_bancaria` de un pago inexistente dan 1644.
Correcciones: `DELETE FROM reembolso` y `UPDATE reembolso SET version = version` dan 1142; un `reembolso` de una
anulación inexistente da 1644.
Sprint 4, tanda 1 (V13, pagos en línea y outbox del OSE): `DELETE` sobre `orden_pago`, `orden_pago_cuota`,
`evento_pasarela` y `configuracion_bd` da 1142; `UPDATE orden_pago_cuota SET version = version` da 1142 (solo
inserción); `INSERT` o `UPDATE` sobre `configuracion_bd` da 1142 (la escribe solo el DBA); `UPDATE orden_pago SET monto =
monto`, `UPDATE evento_pasarela SET orden_pago_id = orden_pago_id`, `UPDATE caja_diaria SET canal = canal`, `UPDATE pago
SET orden_pago_id = orden_pago_id` y `UPDATE comprobante SET reemplaza_id = reemplaza_id` dan 1143; una orden que nace
PAGADA, una cuota de una orden inexistente y (sin la fila del DBA) una orden de la pasarela `SIMULADA` dan 1644.
Sprint 4, tanda 2 (V14, recaudación bancaria): `DELETE` sobre `archivo_cargado`, `lote_recaudacion` y
`linea_recaudacion` da 1142; `UPDATE archivo_cargado SET version = version` da 1142 (solo inserción); `UPDATE
lote_recaudacion SET total = total`, `UPDATE linea_recaudacion SET monto = monto` y `UPDATE pago SET
linea_recaudacion_id = linea_recaudacion_id` dan 1143; un lote que nace APLICADO y una línea de un lote inexistente dan
1644. `trg_pago_registro` pasa a su versión final (con la rama RECAUDACION): 35 triggers en total.
La aplicación lo comprueba sola al arrancar en `prod` (`VerificadorPermisosBaseDatos`), antes de aceptar peticiones. Si `cc_app` puede ejecutarlas, **no arranca** y el log dice qué revisar. Esta comprobación no se puede desactivar.

Si la bitácora queda bloqueada por un evento falso, sigue `incidente-auditoria.md`.
