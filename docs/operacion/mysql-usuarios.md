# MySQL en producción: usuarios y permisos

La plataforma usa **cuatro usuarios de MySQL y un rol** (sprint 7, tanda 2):

| Usuario | Lo usa | Permisos |
|---|---|---|
| `cc_migrador` | `java -jar cuentas-claras.jar migrar` y `03-triggers.sql` | Todo sobre la base `cuentasclaras`: crea y cambia tablas, triggers y funciones |
| `cc_app` | Las peticiones de las **personas** (`DB_USUARIO`) | El rol `cc_negocio`: lectura de todo y escritura **solo** donde hace falta. Sobre `evento_auditoria` solo puede **insertar**. **No** crea cuentas, no cambia claves ni roles, no abre sesiones y no escribe como un actor `sistema.*` |
| `cc_sistema` | Los **procesos** `sistema.*` y la **identidad** (ingreso, sesiones, claves, roles y altas) (`DB_SISTEMA_USUARIO`) | El rol `cc_negocio` más lo exclusivo: `usuario`, `usuario_rol`, `sesion_usuario`, la semilla y la muestra, la foto del resumen, las huellas, las liquidaciones, los avisos de la pasarela y el envío de comprobantes y mensajes. Los triggers lo reconocen por `SESSION_USER()` (`cc_es_sistema()`) |
| `cc_respaldo` | El respaldo diario (`scripts/respaldo/respaldar.sh`) | `SELECT` y `SHOW VIEW` del esquema e `INSERT` en `respaldo`. Nada más (ver [respaldos.md](respaldos.md)) |
| rol `cc_negocio` | `cc_app` y `cc_sistema` (rol por defecto) | Lo que tenía `cc_app` hasta el sprint 6, salvo la identidad y lo que escriben solo los procesos |

Así, aunque alguien robe la clave de la aplicación (`cc_app`) o encuentre una falla en el código, no puede editar ni
borrar la bitácora, ni crear una cuenta de Promotoría, ni darse un rol, ni aprobar a nombre de otra persona (falta la
firma de su sesión), ni escribir como el sistema. Y si alguien la toca con otro usuario, la cadena HMAC lo detecta
(pantalla Bitácora → «Verificar integridad»). La clave de `cc_sistema` va solo al proceso de la aplicación, nunca a una
persona ni a una herramienta de consulta.

## Instalación (una sola vez)
Los scripts están en `scripts/mysql/`. Son los mismos que usa el job `mysql` del CI.

1. **Usuarios y base**, como administrador. Reemplaza `__CLAVE_MIGRADOR__`, `__CLAVE_APP__`, `__CLAVE_SISTEMA__` y `__CLAVE_RESPALDO__` por claves del gestor de secretos (cuatro claves distintas) y **no guardes el archivo modificado**:
   ```bash
   sed -e 's/__CLAVE_MIGRADOR__/<clave-migrador>/' -e 's/__CLAVE_APP__/<clave-app>/' \
       -e 's/__CLAVE_SISTEMA__/<clave-sistema>/' -e 's/__CLAVE_RESPALDO__/<clave-respaldo>/' \
       scripts/mysql/01-usuarios.sql | mysql -h <host> -u root -p
   ```
   Crea la base `cuentasclaras` (utf8mb4), `cc_migrador` con todos los permisos sobre ella, `cc_app` y `cc_sistema` solo
   con SELECT (fase 1), `cc_respaldo` sin permisos y el rol `cc_negocio` (sus GRANT van en el paso 3).

   **Base creada antes del sprint 7:** aplica una vez `scripts/mysql/04-una-vez-sprint-7.sql` (crea `cc_respaldo`,
   `cc_sistema` y el rol `cc_negocio`; es idempotente) antes de volver a aplicar `02` y `03`:
   ```bash
   sed -e 's/__CLAVE_RESPALDO__/<clave-respaldo>/' -e 's/__CLAVE_SISTEMA__/<clave-sistema>/' \
       scripts/mysql/04-una-vez-sprint-7.sql | mysql -h <host> -u root -p
   ```
2. **Primera migración** (ver «Despliegue»): crea las tablas con `cc_migrador`.
3. **Permisos por tabla**, después de esa primera migración. MySQL no acepta un GRANT sobre una tabla que todavía no existe:
   ```bash
   mysql -h <host> -u root -p < scripts/mysql/02-permisos-tablas.sql
   ```
   **Sprint 7, tanda 2: `02` es la fuente única.** Empieza quitándolo todo (`REVOKE ALL` de `cc_app`, `cc_sistema` y
   `cc_respaldo`, y del rol `cc_negocio`) y vuelve a dar solo lo suyo: un GRANT dado a mano no sobrevive a la siguiente
   aplicación de `02`. Los permisos de negocio van al rol `cc_negocio` (rol por defecto de `cc_app` y `cc_sistema`) y lo
   exclusivo de los procesos y la identidad, solo a `cc_sistema`. La tabla de abajo dice lo que tiene `cc_negocio` (es
   decir, `cc_app`); la sección «Identidad, sesiones y firmas» dice lo que es solo de `cc_sistema`. Empieza con
   `SET NAMES utf8mb4` (el cliente `mysql` de un contenedor usa latin1 por defecto y guardaría mal las tildes de las
   funciones).

| Tabla | Permisos de `cc_app` | Por qué |
|---|---|---|
| (todas) | SELECT | Leer |
| `usuario`, `usuario_rol`, `sesion_usuario` | Ninguno (solo el SELECT general) | **Sprint 7, tanda 2:** las cuentas, las claves, los roles y las sesiones los escribe solo `cc_sistema` (ruta de identidad). Con `cc_app`: 1142 |
| `firma_operacion` | INSERT | **Solo inserción**: la firma de una aprobación con el secreto de la sesión de quien aprueba; `trg_firma_operacion_nace` la valida contra su sesión ABIERTA y borra el secreto |
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
| `comprobante` | INSERT | El comprobante nace con el pago (la persona). **Sprint 7, tanda 2:** el envío al OSE (`estado_envio, intentos, enviado_en, respuesta, codigo_hash, enlace_pdf`) lo escribe solo `cc_sistema` (UPDATE por columna); con `cc_app`, 1142. Serie, número, receptor y total no cambian (1143 también para `cc_sistema`) |
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
| `reembolso_pasarela` | INSERT | **Solo inserción** (correcciones del sprint 4, V16): la devolución de un pago en línea por la API de la pasarela (al mismo medio de origen), una por anulación aprobada de tipo DEVOLUCION y nunca con contracargo (trigger `trg_reembolso_pasarela_registro`). `reembolso` rechaza los pagos de la pasarela (trigger) |
| `enlace_activacion` | INSERT y UPDATE **solo** de `usado_en, usado_ip, anulado_en, actualizado_en, version` | Enlace de un solo uso para que el apoderado elija su clave (S4-M2). El hash del token, el usuario, el vencimiento y la IP de quien lo creó no cambian (1143); no se borra (1142) |
| `resumen_diario` | Ninguno (sprint 7: solo `cc_sistema`, INSERT) | **Solo inserción** (sprint 6, tanda 2): la foto del resumen de las 19:30. La escribe solo `sistema.panel` y cada cifra debe ser la suma de `pago` y `cuota` en ese momento (trigger `trg_resumen_diario_registro`); desde V23, además, es de HOY (Lima), su corte es de ahora, los conteos de cajas, cierres, solicitudes y avisos salen de las tablas y su texto (`parametros`, el que exige cada mensaje RESUMEN_DIARIO) dice esas cifras; nadie la corrige ni la borra (1142) |
| `llamada_control` | INSERT | **Solo inserción** (sprint 6, tanda 3): el resultado de la llamada de control semanal. **Sprint 7, tanda 2:** lleva la firma de la sesión de quien la registra y el segundo intento exige un «No contesta» de hace una hora con la hora de la BASE (`registrada_bd`, la pone el trigger). Desde V23 la registra una persona activa de Promotoría (Dirección, solo con la semana delegada y marcando `por_delegacion`), para el lunes de la semana en curso (hora de Lima), a una familia de la muestra congelada que no fue reemplazada, y el segundo intento solo tras un «No contesta» (trigger `trg_llamada_control_registro`); no se corrige ni se borra (1142) |
| `muestra_llamada` | Ninguno (sprint 7: solo `cc_sistema`, INSERT) | **Solo inserción** (correcciones del sprint 6, V23): la muestra congelada de la semana. **Sprint 7, tanda 2 (E10):** la fija solo `sistema.panel` con `cc_sistema` (el lunes a las 00:10 o en la primera consulta de la semana), para la semana en curso, con familias que pagaron en efectivo o tienen deuda vencida; un reemplazo solo de quien no contestó dos veces (trigger `trg_muestra_llamada_registro`) |
| `delegacion_llamada` | INSERT | **Solo inserción** (V23): Promotoría delega a Dirección las llamadas de la semana en curso (trigger `trg_delegacion_llamada_registro`) |
| `configuracion_colegio` | Ninguno (solo el SELECT general) | La escribe solo el DBA (V23, QA-S6-6): `resumen_correo_externo` y, desde V25, `huella_correo_externo`, por colegio. INSERT, UPDATE y DELETE dan 1142 |
| `semilla_muestreo`, `huella_bitacora`, `huella_hora`, `liquidacion_pasarela`, `liquidacion_linea`, `evento_pasarela`, `mensaje` (envío) | Sprint 7, tanda 2: solo `cc_sistema` | Los escriben solo los procesos (`sistema.muestreo`, `sistema.auditoria`, `sistema.pasarela`, `sistema.mensajeria`). `cc_app` sigue insertando los mensajes que nacen con un pago (el outbox) pero no los marca enviados ni entregados (1142) |

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

## Identidad, sesiones y firmas (sprint 7, tanda 2)
- **Dos conexiones en la aplicación.** Las peticiones de las personas usan `cc_app`; los procesos `sistema.*` y la
  identidad (ingreso, cierre de sesión, claves, roles, altas) usan `cc_sistema`. La conexión se elige al pedirla
  (`FuenteDatosEnrutada`), y un proceso llamado dentro de la transacción de una persona abre SU transacción con
  `cc_sistema` (`EjecucionComoSistema`, `REQUIRES_NEW`).
- **Sesión de la base.** Al ingresar se abre una fila en `sesion_usuario` con el SHA-256 de un secreto de 32 bytes que
  vive solo en la sesión HTTP (memoria del servidor). Se cierra al salir, al vencer la sesión HTTP, al ingresar en otro
  equipo, al cambiar la clave, los roles o el contacto, al desactivar la cuenta y al reiniciar la aplicación; vence a
  las 10 horas como máximo (`cuentasclaras.sesion.vigencia-maxima`, 12 horas como tope en la base).
- **Firma.** Cada aprobación o resolución (solicitudes, descuentos, cierres, planes, lotes, extractos, partidas,
  feriados, avisos, renovaciones, verificaciones manuales, llamadas y delegaciones) inserta antes una fila en
  `firma_operacion` con su clave canónica (por ejemplo `solicitud_cambio:<id>:APROBADA`) y el secreto. El trigger de la
  tabla resuelta exige esa firma, a nombre de quien figura como aprobador y de los últimos 5 minutos
  (`cc_firma_valida`). Con la clave de `cc_app` no se aprueba a nombre de otra persona: falta el secreto de su sesión.
- **Roles.** Dar o quitar PROMOTOR o DIRECTOR exige una solicitud `CAMBIO_ROLES` aprobada y firmada por otra persona
  (ni quien la pidió ni el titular); el colegio nunca se queda sin Promotoría activa; CAJA no se combina con PROMOTOR,
  DIRECTOR ni ADMINISTRACION, ni DIRECTOR con ADMINISTRACION (`trg_usuario_rol_alta|baja`). Excepciones de arranque: el
  primer PROMOTOR de un colegio sin Promotoría y el primer DIRECTOR de un colegio sin Dirección y con una sola Promotoría.
- **Muestreo.** La semilla la planta `sistema.muestreo` (solo la de hoy o la de esta semana) y la muestra la usa
  derivada con la clave HMAC del servidor: leer `semilla_muestreo` no basta para calcularla.
- **Correo externo de la huella diaria, por colegio** (antes en `configuracion_bd`, que ya no se usa; V25 lo copió si
  había un solo colegio y el verificador avisa si la fila vieja sigue):
  ```sql
  INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en)
  VALUES (<id del colegio>, 'huella_correo_externo', 'contador@estudio.pe', NOW(6));
  ```
- **TLS.** En prod las dos conexiones van cifradas (`sslMode=VERIFY_IDENTITY` o `REQUIRED` en `DB_URL`): si no, la
  aplicación no arranca. Solo la instalación local en Docker, con la base en el mismo servidor, lo apaga con
  `CC_EXIGIR_TLS_BD=false`.
- **Registro general de MySQL apagado** (`general_log = OFF`, el valor por defecto): con él encendido, el secreto de la
  firma quedaría escrito en el log del servidor al insertarse.
- **Huellas de los triggers y funciones.** `02` crea `huellas_objetos()` (DEFINER, la ejecutan `cc_negocio` y
  `cc_respaldo`): el SHA-256 del cuerpo normalizado de cada trigger y función (sin comentarios, con los escapes de los
  literales procesados y los espacios colapsados). El arranque en prod compara cada huella con la de `03-triggers.sql`
  empaquetado en el jar: un trigger debilitado con el mismo nombre, una función reemplazada o un objeto de más no dejan
  arrancar. También rechaza cualquier privilegio que `02` no da (`SHOW GRANTS` de `cc_app` y `cc_sistema`).

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
2. **Arrancar** la aplicación con `SPRING_PROFILES_ACTIVE=prod`, `DB_USUARIO=cc_app` / `DB_CLAVE` y
   `DB_SISTEMA_USUARIO=cc_sistema` / `DB_SISTEMA_CLAVE` (sprint 7, tanda 2: sin el segundo usuario, o con el mismo para
   los dos, no arranca). Antes de aceptar peticiones comprueba que no falten migraciones, que `cc_app` no pueda editar ni borrar la bitácora ni borrar cuotas (error 1142) y que no pueda cambiar el monto de una cuota ni las columnas inmutables de planes, lotes, líneas y solicitudes (error
1143, o 1142 si no tiene ningún UPDATE sobre la tabla), además de los triggers del paso 3. Si algo falla, **no arranca**.

**Sprint 7 (orden nuevo del despliegue):** respaldo → detener la aplicación → `migrar` → `04` (solo la primera vez) →
`02` → `03` → arrancar → verificador. Detalle y desastre en [respaldos.md](respaldos.md). En la tanda 2, después de
`04`: configura `DB_SISTEMA_USUARIO` y `DB_SISTEMA_CLAVE` en el entorno de la aplicación, mueve la fila
`huella_correo_externo` a `configuracion_colegio` si V25 no lo hizo (más de un colegio) y confirma que `DB_URL` pide TLS.

## Respaldos (sprint 7, tanda 1)
- La tabla `respaldo` (V24) la escribe **solo** `cc_respaldo` (GRANT de INSERT y `trg_respaldo_registro`, que exige
  `SESSION_USER()` = `cc_respaldo`, que se registre al terminar y que las anclas sean eventos reales de la bitácora).
  `cc_app` solo la lee: 1142 al insertar, editar o borrar. `cc_respaldo` tampoco edita ni borra (1142).
- Fila de `configuracion_bd` que solo escribe el DBA, **nunca en prod**: `('respaldo_simulado', 'PERMITIDA')` admite el
  destino «simulado» (una carpeta local) en dev, CI y piloto. Sin ella, `trg_respaldo_registro` rechaza el respaldo
  simulado (1644), y en prod la aplicación no arranca si la fila existe.
- `cc_respaldo` vuelca con `mysqldump --single-transaction --no-tablespaces --skip-triggers`: no necesita `LOCK TABLES`,
  `PROCESS`, `RELOAD` ni `TRIGGER` (comprobado en MySQL 8.4.11).

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
- Correcciones del sprint 4 (S4-M1): el piloto con la pasarela simulada exige además `cuentasclaras.entorno.nombre:
  PILOTO` (ya viene en `application-piloto.yaml`): todas las páginas muestran la franja «PILOTO · Entorno de prueba» y un
  pago simulado queda **por revisar** (`SIMULADA_EN_PILOTO`): no registra pago ni comprobante, no marca cuotas pagadas y
  no se puede aplicar. Aun así, el piloto se hace con familias de prueba o con familias reales que saben que es una
  prueba: el pago real sigue siendo en caja.
- Pasarela real: `PASARELA_PROVEEDOR` (por defecto `NINGUNA`: sin pago en línea). Hoy solo se acepta `CULQI` con
  `PASARELA_LLAVE_SECRETA` (`sk_live_` en prod, `sk_test_` fuera de prod), `PASARELA_WEBHOOK_USUARIO` y
  `PASARELA_WEBHOOK_CLAVE`, y su adaptador todavía no está integrado: mientras tanto, déjalo en `NINGUNA`.
- OSE real: `COMPROBANTES_PROVEEDOR=NUBEFACT` con `NUBEFACT_RUTA` (https de `api.nubefact.com`) y `NUBEFACT_TOKEN`. Fuera
  de prod solo con `cuentasclaras.comprobantes.permitir-real-fuera-de-prod: true` (cuenta DEMO).

## Mensajería a las familias (sprint 5, tanda 1)
- Cada pago, anulación y descuento crea su aviso (WhatsApp, con correo de respaldo) en la misma transacción; lo envía
  `sistema.mensajeria`. El destino es siempre el contacto registrado (trigger `trg_mensaje_nace`) y el mensaje no cambia
  su destino ni su texto (1143).
- **Producción no arranca sin un canal real** (decisión 40): `MENSAJERIA_WHATSAPP_PROVEEDOR=WHATSAPP_CLOUD` con
  `WHATSAPP_NUMERO_ID`, `WHATSAPP_TOKEN`, `WHATSAPP_SECRETO_APP` y `WHATSAPP_TOKEN_VERIFICACION`, o
  `MENSAJERIA_CORREO_PROVEEDOR=SMTP` con `SPRING_MAIL_HOST` (y `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
  `SPRING_MAIL_PASSWORD`) y `CORREO_REMITENTE`. `CC_URL_PUBLICA` es la dirección del portal en los mensajes.
- La mensajería **SIMULADA** solo existe en dev, test y piloto. En MySQL, un mensaje simulado solo pasa a ENVIADO si el
  DBA registró la fila (nunca en prod; el verificador de prod exige que no exista y el del piloto, que exista):
  ```sql
  INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('mensajeria_simulada', 'PERMITIDA', NOW(6));
  ```
- Opcional en prod (decisión 49): el correo externo del contador que recibe la huella diaria de la bitácora. Desde el
  sprint 7 (tanda 2, V25) es **por colegio**, en `configuracion_colegio` (ver «Identidad, sesiones y firmas»); la fila
  de `configuracion_bd` ya no se usa.
- Opcional en prod (sprint 6, decisión 69): el correo externo del contador que recibe también el resumen diario de las
  19:30 (sin la fila, el resumen sale solo a Promotoría). Correcciones del sprint 6 (QA-S6-6, V23): la fila es **por
  colegio**, en `configuracion_colegio` (cc_app tampoco la escribe: 1142); la de `configuracion_bd` ya no se usa:
  ```sql
  INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en)
  VALUES (<id del colegio>, 'resumen_correo_externo', 'contador@estudio.pe', NOW(6));
  ```
- Webhook de WhatsApp: `https://<dominio>/webhooks/whatsapp/<colegioId>` (GET para la verificación de Meta, POST firmado
  con `X-Hub-Signature-256`).

## Variables de entorno (perfil `prod`)
| Variable | Contenido |
|---|---|
| `DB_URL` | `jdbc:mysql://<host>:3306/cuentasclaras` |
| `DB_USUARIO`, `DB_CLAVE` | `cc_app` y su clave |
| `DB_SISTEMA_USUARIO`, `DB_SISTEMA_CLAVE` | `cc_sistema` y su clave (sprint 7, tanda 2): procesos e identidad. Distinto de `DB_USUARIO` |
| `CC_EXIGIR_TLS_BD` | `true` por defecto: la conexión a MySQL debe ir cifrada. `false` solo en la instalación local en Docker |
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
Sprint 4, tanda 3 (V15, extracto y conciliación): `DELETE` sobre `cuenta_bancaria`, `extracto_bancario`,
`movimiento_bancario`, `partida_conciliacion`, `liquidacion_pasarela` y `liquidacion_linea` da 1142; `UPDATE
movimiento_bancario|liquidacion_pasarela|liquidacion_linea SET version = version` y `UPDATE verificacion_bancaria SET
origen = origen` dan 1142 (solo inserción); `UPDATE extracto_bancario SET saldo_final = saldo_final`, `UPDATE
partida_conciliacion SET monto_movimiento = monto_movimiento` y `UPDATE cuenta_bancaria SET numero = numero` dan 1143; un
extracto que nace CONFIRMADO, un movimiento de un extracto inexistente, una partida sobre un movimiento inexistente y una
verificación AUTOMATICA sin partida confirmada dan 1644. `trg_verificacion_bancaria_registro` pasa a su versión final
(la AUTOMATICA solo la inserta `sistema.conciliacion` con una partida CONFIRMADA sobre un extracto CONFIRMADO): 40
triggers en total.
Correcciones del sprint 4 (V16): `UPDATE extracto_bancario SET muestra = muestra`, `UPDATE extracto_bancario SET
semilla_muestreo = semilla_muestreo`, `UPDATE lote_recaudacion SET muestra = muestra`, `UPDATE partida_conciliacion SET
linea_recaudacion_id = linea_recaudacion_id` y `UPDATE enlace_activacion SET hash_token|vence_en|usuario_id = ...` dan
1143; `DELETE` y `UPDATE ... SET version = version` sobre `reembolso_pasarela` y `DELETE` sobre `enlace_activacion` dan
1142; un reembolso por la pasarela sin anulación aprobada da 1644. Cambian (versión final) `trg_partida_conciliacion_estado`
(objeto vigente intocable fuera de PROPUESTA, quien subió el lote no confirma, la MANUAL exige su solicitud
`PARTIDA_MANUAL` aprobada por quien confirma, la EXPLICADA de un cargo no la confirma quien subió el extracto),
`trg_verificacion_bancaria_registro` (el monto del banco es el del movimiento), `trg_extracto_bancario_nace|estado`
(muestra y semilla obligatorias; cada extracto se confirma con SU saldo), `trg_lote_recaudacion_nace` (muestra),
`trg_partida_conciliacion_registro` (objeto LINEA_RECAUDACION), `trg_linea_recaudacion_estado` (devuelve otra persona,
con destino), `trg_anulacion_pago_registro` (CONTRACARGO), `trg_reembolso_registro` (sin pagos de la pasarela) y
`trg_orden_pago_estado` (contracargo una vez; con contracargo no se aplica ni se devuelve); se agrega
`trg_reembolso_pasarela_registro`: 41 triggers en total.
Sprint 5, tanda 2 (V18, renovación de matrícula y avisos de las familias): `DELETE` sobre `renovacion_matricula` y
`aviso_familia` da 1142; `UPDATE renovacion_matricula SET alumno_id|familia_id|anio_destino_id|matricula_origen_id|
deuda_al_proponer|vence_en = ...` y `UPDATE aviso_familia SET texto|tipo|familia_id|apoderado_id|pago_id|cuota_id = ...`
dan 1143; una renovación que nace CONFIRMADA (o de un alumno inexistente), una matrícula ACTIVA en un año inexistente y
una RESERVADA ya activada dan 1644. Se agregan `trg_renovacion_matricula_nace|estado`, `trg_matricula_nace|estado` y
`trg_aviso_familia_estado`: 51 triggers en total (46 de la tanda 1). Con un año EN CURSO, la matrícula de un año
PLANIFICADO solo nace RESERVADA y pasa a ACTIVA cuando su cuota de matrícula está pagada, y solo `sistema.matricula`.
Sprint 5, tanda 3 (V19, feriados, semilla del muestreo y cierre bancario mensual): `DELETE` sobre `feriado`,
`semilla_muestreo` y `cierre_mensual_banco` y `UPDATE semilla_muestreo ...` dan 1142; `UPDATE feriado SET
fecha|descripcion = ...` y `UPDATE cierre_mensual_banco SET total_abonos|total_cargos|saldo_final|cuenta_id|anio|mes = ...`
dan 1143; un feriado de hoy o del pasado, un cierre que nace CUADRADO y un cierre con totales que no salen de los
extractos CONFIRMADOS del mes dan 1644. Se agregan `trg_feriado_registro|anulacion` y
`trg_cierre_mensual_banco_nace|estado`: 55 triggers en total.
Correcciones del sprint 5 (V20, `docs/arquitectura/sprint-5-correcciones.md`): `DELETE` sobre `verificacion_contacto` y
`huella_hora` y `UPDATE huella_hora ...` dan 1142; `UPDATE verificacion_contacto SET contacto|hash_token|mensaje_id|
apoderado_id|vence_en = ...` da 1143; una verificación sin su mensaje, una huella de la hora que no coincide (o que
retrocede), un apoderado que nace verificado y un feriado que nace aprobado dan 1644. Se agregan
`trg_verificacion_contacto_nace|uso` y `trg_huella_hora_registro`, y cambian `trg_apoderado_nace|facturacion`
(contacto verificado solo con su enlace usado; contacto aprobado solo con su solicitud), `trg_mensaje_nace` (contacto del
personal comparado NORMALIZADO con la función `cc_contacto_normal` y aprobado para ESE contacto; contacto del apoderado
verificado), `trg_mensaje_envio`, `trg_huella_bitacora_registro` (no retrocede), `trg_aviso_familia_estado` (no lo
cierra quien participó) y `trg_feriado_registro|anulacion` (propuesto, aprobado por otra persona, 3 por mes, no 3
seguidos): 58 triggers en total. `03-triggers.sql` crea además la función `cc_contacto_normal` (necesita
`log_bin_trust_function_creators = 1`, como `triggers_instalados`).
Sprint 6, tanda 2 (V21, resumen diario y contacto del personal): `DELETE` y `UPDATE` sobre `resumen_diario` dan 1142;
una foto con cifras que no son las de los libros, de otro actor, con un corte de otro día o con una huella inventada da
1644; el resumen y las alertas a Promotoría solo los crea `sistema.panel` y solo a Promotoría (y a Dirección, las
alertas); el celular o el correo de una persona del personal solo cambian con SU `CAMBIO_CONTACTO_PERSONAL` aprobada
(1644). Se agregan `trg_resumen_diario_registro` y `trg_usuario_contacto` y cambia `trg_mensaje_nace`: 60 triggers.
Sprint 6, tanda 3 (V22, llamada de control): `DELETE` y `UPDATE` sobre `llamada_control` dan 1142; una llamada que no
registra una persona activa de Promotoría o Dirección, que no es del lunes de la semana en curso en Lima
(`DATE(UTC_TIMESTAMP() - INTERVAL 5 HOUR)`) o a una familia sin pagos en efectivo en esas semanas da 1644; dos llamadas a
la misma familia en la semana dan 1062 y «No confirma» sin nota, 3819. V22 recrea `ck_semilla_muestreo_ambito` con el
ámbito `LLAMADA_CONTROL`. Se agrega `trg_llamada_control_registro`: 61 triggers en total.
Sprint 7 (V24 y V25): `trg_respaldo_registro` (tanda 1, 64 en total) y, en la tanda 2, `trg_firma_operacion_nace`,
`trg_sesion_usuario_nace|cierre`, `trg_evento_auditoria_actor`, `trg_semilla_muestreo_registro`, `trg_usuario_nace`,
`trg_usuario_identidad` y `trg_usuario_rol_alta|baja`, con versión nueva de 25 triggers (firma de sesión y actor de
sistema) y las funciones `cc_es_sistema` y `cc_firma_valida`: **73 triggers en total**. Con `cc_app`: `INSERT` en
`usuario`, `usuario_rol` o `sesion_usuario`, `UPDATE usuario`, `DELETE FROM usuario_rol` y `UPDATE comprobante|mensaje`
dan 1142; un evento, un pago o una verificación AUTOMATICA a nombre de un actor `sistema.*` dan 1644; una aprobación sin
la firma de la sesión de quien aprueba da 1644. Con `cc_sistema`: editar la bitácora da 1142 y una firma o una sesión
imposibles, 1644.
La aplicación lo comprueba sola al arrancar en `prod` (`VerificadorPermisosBaseDatos`), antes de aceptar peticiones. Si `cc_app` puede ejecutarlas, **no arranca** y el log dice qué revisar. Esta comprobación no se puede desactivar.

Si la bitácora queda bloqueada por un evento falso, sigue `incidente-auditoria.md`.
