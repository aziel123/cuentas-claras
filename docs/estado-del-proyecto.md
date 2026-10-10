# Estado del proyecto · Cuentas Claras

> Última actualización: 9 de octubre de 2026 (correcciones del sprint 7: versión 1 terminada).
> Resumen para retomar el trabajo: qué está hecho, cómo probarlo y qué hay que decidir con el colegio.

## Avance

| Sprint | Estado | Rama | Pruebas |
|---|---|---|---|
| 0 · Arranque | ✅ Terminado | `main` | 3 |
| 1 · Fundaciones (seguridad, roles, auditoría) | ✅ Terminado, auditado y corregido | `claude/sprint-1-fundaciones` | 317 |
| 2 · Datos del colegio (alumnos, Excel, pensiones, saldo inicial) | ✅ Terminado, auditado y corregido | `claude/sprint-2-datos-colegio` | 713 |
| 3 · Caja (pagos, comprobantes, anulaciones, descuentos, cierre ciego, conciliación) | ✅ Terminado, auditado y corregido | `claude/sprint-3-correcciones` | 967 |
| 4 · Cero digitación (pago en línea, comprobante automático, recaudación bancaria, conciliación automática) | ✅ Terminado, auditado y corregido | `claude/sprint-4-cero-digitacion` | 1429 |
| 5 · Familias y matrícula 2027 (avisos por WhatsApp, acceso directo al titular, huella diaria, portal, renovación 2027, feriados, cierre mensual) | ✅ Terminado, auditado y corregido | `claude/sprint-5-familias` | 1694 (más las de MySQL real, que corren en el CI) |
| 6 · Panel de la promotora (panel, reportes y Excel, resumen diario, alertas y aprobaciones en el celular, contacto del personal, llamada de control) | Implementado en 3 tandas, auditado y corregido (`docs/arquitectura/sprint-6-correcciones.md`, V23, 63 triggers) | `main` (PR #4) | 1883 (92 de MySQL real, que se omiten sin `CC_PRUEBA_MYSQL` y corren en el CI) |
| 7 · Endurecimiento y entrega (respaldos cifrados y restauración probada, monitoreo, base de datos endurecida, seguridad web, Ley 29733, manuales y capacitación) | ✅ Terminado, auditado y corregido (`docs/arquitectura/sprint-7-correcciones.md`, V24 a V27, 75 triggers) | `claude/sprint-7-endurecimiento` | 2226 (120 de MySQL real, que se omiten sin `CC_PRUEBA_MYSQL` y corren en el CI) |

El sprint 4 se implementó en 3 tandas verificadas, cada una con su migración y probada también contra MySQL 8 real, y luego se corrigió todo lo que encontraron la auditoría antifraude y QA:

| Tanda | Qué trae | Migración | Pruebas al cerrarla |
|---|---|---|---|
| 1 | Comprobante automático (envío al OSE con reintentos) y pago en línea con la pasarela simulada | V13 | 1026 |
| 2 | Recaudación bancaria: archivo del banco confirmado a ciegas y aplicado por el sistema | V14 | 1101 |
| 3 | Extracto bancario encadenado y conciliación automática | V15 | 1175 (44 de MySQL real, que se omiten sin `CC_PRUEBA_MYSQL`) |
| Correcciones | Los 11 hallazgos de la auditoría y los 6 de QA (`docs/arquitectura/sprint-4-correcciones.md`) | V16 | 1429 (46 de MySQL real) |

El sprint 7 va en 3 tandas (`docs/arquitectura/sprint-7-endurecimiento.md`, con la sección «Implementación» de cada una):

| Tanda | Qué trae | Migración | Triggers | Pruebas al cerrarla |
|---|---|---|---|---|
| 1 | Respaldo diario cifrado fuera del servidor, restauración automática en el CI, logs JSON enmascarados, health checks, alertas técnicas y vigilante externo | V24 | 64 | 1920 (93 de MySQL real) |
| 2 | Base de datos endurecida: usuario `cc_sistema` para los procesos y la identidad, firma de sesión en cada aprobación, roles de Promotoría y Dirección solo con solicitud, huellas de triggers y funciones | V25 | 73 | 1971 (105 de MySQL real) |
| 3 | Seguridad web (cabeceras, cookie `__Host-`, límite de ingresos por conexión, sesión de 10 horas, IDOR, CSRF, archivos hostiles, dependencias), Ley 29733 (registro de accesos, «Mis datos», pedidos con plazo, aviso de privacidad) y documentos de entrega | V26 | 73 | 2035 (107 de MySQL real) |
| Correcciones | La auditoría (S7-A1: anular un pago sin firma; S7-M1: rol anidado; S7-B1: registro de la caja) y los 8 hallazgos y 2 observaciones de QA (`docs/arquitectura/sprint-7-correcciones.md`) | V27 | 75 | 2226 (120 de MySQL real) |

Las ramas están **apiladas**: cada una parte de la anterior y contiene todo su trabajo. La más completa es `claude/sprint-5-familias`. Los sprints 1 a 4 están en revisión en el PR #1 hacia `main` (CI en verde, también el job de MySQL 8). El sprint 5 pasó el CI completo en un PR en borrador que se cerró sin merge; se propone a `main` cuando se una el PR #1.

Cada sprint siguió el mismo flujo:
1. `arquitecto-software` diseña.
2. `backend-spring` implementa en tandas verificadas.
3. `auditor-seguridad-antifraude` y `qa-tester` revisan en paralelo.
4. `backend-spring` corrige todos los hallazgos.

Los diseños están en `docs/arquitectura/`.

## Qué hace hoy la plataforma

### Seguridad y control
- Inicio de sesión: tras 5 intentos fallidos con un usuario desde una conexión, esa conexión espera 15 minutos (sprint 7); la cuenta se bloquea con 15 intentos fallidos desde varias conexiones. Los intentos en paralelo no evitan el bloqueo.
- 6 roles con permisos verificados en el servidor y combinaciones de roles prohibidas (Caja no se combina con Dirección, Promotoría ni Administración).
- Cada colegio ve solo sus datos. La base de datos también impide referencias cruzadas entre colegios.
- **Bitácora de auditoría inmutable** con sello criptográfico encadenado y botón "Verificar integridad". La promotora puede anotar la "huella" para detectar si la bitácora fue recortada.
- **Bandeja de aprobaciones**: retiros, matrículas tardías y cambios de contacto o de responsable de pago los aprueba otra persona. Quien pide no aprueba; lo exige el código y también la base de datos.
- Solo Promotoría crea cuentas de Caja y Administración o les restablece la clave.
- En MySQL, la aplicación no puede borrar datos financieros ni modificar montos o fechas de cuotas. Si alguien le da permisos de más, la aplicación se niega a arrancar.

### Datos del colegio y deudas
- Años escolares, secciones, alumnos, apoderados, familias (con hermanos) y matrículas.
- Importación desde Excel en 3 pasos, protegida contra archivos maliciosos, idempotente y de todo o nada.
- Planes de pensión con doble aprobación y versiones. Si el plan cambia mientras se revisa, la aprobación se rechaza.
- Cronograma de cuotas generado por el sistema: la cajera nunca decide montos.
- Saldo inicial con doble control. Quien confirma escribe a ciegas el total del informe del contador.

### Caja (sprint 3)
- Cobro en segundos: la cajera elige cuotas y nunca escribe montos. Boleta simulada con numeración sin huecos.
- Libro de pagos de solo inserción. En MySQL es imposible tener una cuota pagada sin pago.
- Anulaciones, devoluciones, correcciones, descuentos, becas y reaperturas solo con aprobación de otra persona. Las devoluciones en efectivo exigen haber hablado con el apoderado.
- Cierre de caja a ciegas con un solo reconteo, aprobación del cierre y depósito. El faltante llega como alerta a la promotora el mismo día.
- Conciliación a ciegas: Administración escribe el número, la fecha y el monto que ve en el banco. Un número de operación no se puede reutilizar cambiando su formato.
- Alertas críticas por pagos digitales sin verificar, efectivo sin depositar, devoluciones pendientes y comprobantes inconsistentes.
- 28 triggers en MySQL. Si falta alguno, la aplicación no arranca.
- Usuarios demo adicionales: `caja2`, `caja.b`.

### Cero digitación (sprint 4)
Todo lo que entra lo registra un actor de sistema (`sistema.pasarela`, `sistema.recaudacion`), nunca una persona, y el dinero solo cuenta cuando lo confirma una fuente independiente de quien lo carga.
- **Pago en línea** de los padres (Yape, Plin o tarjeta) con la pasarela simulada: el pago se registra solo cuando la consulta a la pasarela lo confirma, con su boleta. La simulada no puede marcar pagos en producción, y en el piloto sus pagos quedan por revisar sin tocar las cuotas (franja «PILOTO» en todas las páginas).
- **Cuenta en línea del apoderado:** nadie ve su clave. Quien la crea entrega un enlace de un solo uso que vence en 48 horas, con el que el apoderado elige su clave. Solo Promotoría restablece el acceso. La activación queda en la bitácora con su IP.
- **Comprobante automático**: cola de envío al OSE (simulado por ahora) con reintentos y alerta antes del plazo legal. Un comprobante rechazado se reemite con número nuevo, sin huecos.
- **Recaudación bancaria**: Administración sube el archivo del banco; otra persona escribe a ciegas el total que ve en el banco y recién entonces el sistema aplica los pagos. Lo que no cuadra queda por revisar y lo resuelve otra persona.
- **Extracto y conciliación automática** (tanda 3):
  - Administración sube el extracto del banco (CSV o Excel en formato genérico; los formatos de cada banco se agregan como adaptadores). Se guarda el archivo original con su SHA-256.
  - Cada extracto **continúa al anterior**: empieza el día siguiente y con su saldo final. Un día ya cargado que vuelve distinto se rechaza y alerta a Promotoría («el banco no cambia el pasado»).
  - Promotoría o Dirección **escribe a ciegas el saldo final** de cada extracto que ve en su app del banco. Mientras espera, nadie ve sus montos, y la muestra que se compara es fija y sin montos. Quien subió el extracto no lo confirma. Dos saldos que no coinciden dejan el extracto RECHAZADO.
  - El sistema empareja cada movimiento con los pagos digitales de caja, los depósitos, las liquidaciones de la pasarela (netas de comisión e IGV) y los abonos de la recaudación. Las parejas **exactas** (misma operación y monto) se confirman solas; las **sugeridas** (mismo monto) las confirma alguien de Administración que no cobró, no depositó ni subió el lote; una pareja **a mano** es del mismo monto y la aprueba otra persona en la bandeja.
  - La pantalla **muestra solo las diferencias**. Lo que debía estar en el banco y no está aparece **en rojo con quién lo registró**: un Yape inventado en caja sale en rojo para Promotoría el día hábil siguiente, en cuanto se confirma el extracto de ese día.
  - Los movimientos ajenos a la cobranza (intereses, comisiones) se explican con categoría y nota; un cargo no lo explica quien subió el extracto, y un cargo del mismo monto que un abono de esos días es CRÍTICO. Todo lo resuelto a mano queda en la bitácora y en un resumen semanal para Promotoría.
  - La verificación a ciegas del sprint 3 queda para las excepciones (en Conciliación › Verificación manual).
- **Contracargo:** se anula el pago SIN reembolso (el banco ya devolvió el dinero); la devolución de un pago en línea solo sale por la API de la pasarela.
- 41 triggers en MySQL (13 nuevos en el sprint). Si falta alguno, la aplicación no arranca.

### Familias y matrícula 2027 (sprint 5)
Diseño en `docs/arquitectura/sprint-5-familias.md` y correcciones en `sprint-5-correcciones.md` (migraciones V17 a V20, 58 triggers en MySQL).
- **El padre se entera al instante:** cada pago, anulación y descuento crea su mensaje por WhatsApp, con correo de respaldo, en la misma transacción del dinero. Sin mensaje no hay pago. Lo envía `sistema.mensajeria`, con monto, concepto, comprobante y quién lo registró. Conectores simulados en dev, test y piloto; en producción la aplicación no arranca sin un canal real.
- **Contacto verificado:** todo celular o correo nuevo o cambiado queda pendiente hasta que su dueño lo confirma con un enlace de un solo uso y su DNI. Los contactos se comparan normalizados y no se escribe a un contacto del personal sin aprobación (hallazgo S5-A1).
- **Acceso directo al titular:** al crear o restablecer una cuenta del personal o de un apoderado, el enlace llega al celular o correo del titular. Nadie más lo ve (cierra A2 y S4-M2).
- **Huella de la bitácora:** cada día a Promotoría (y cada hora en horario de caja), con la huella anterior. Si la secuencia retrocede o falta un día, es alerta crítica.
- **Portal de familias para celular:** estado de cuenta, boletas, pago en línea, historial de mensajes y «¿Algo no cuadra?», que solo ven Promotoría y Dirección. Quien intervino en lo reclamado no cierra el aviso.
- **Matrícula 2027:** la familia confirma la renovación; la matrícula queda RESERVADA con su cuota y se activa sola al pagarla, generando las 10 pensiones. Sin confirmación no hay deuda.
- **Recordatorios:** 3 días antes y el día hábil siguiente, de lunes a sábado de 08:00 a 20:00, sin mencionar lo académico. El apoderado los puede apagar; los avisos de pago no.
- **Feriados:** los 16 nacionales en el código; los días no laborables extra los propone un rol y los aprueba otro, con tope.
- **Muestra de caja con semilla secreta** y **cierre bancario mensual a ciegas** contra el estado de cuenta oficial.

### Endurecimiento y entrega (sprint 7)
- **Respaldos:** diario a las 02:30 y antes de cada despliegue, cifrado con `age` para dos destinatarios (Promotoría y el responsable técnico) y guardado fuera del servidor. El manifiesto ancla la bitácora y cuenta las filas de las tablas de solo inserción: el respaldo siguiente avisa si alguien borró algo. El CI restaura cada respaldo en un MySQL aparte, verifica la cadena de la bitácora y arranca la aplicación sobre la copia.
- **Monitoreo sin servicios pagos:** logs en JSON sin datos personales (DNI, celulares, correos y el valor de un «Duplicate entry» salen enmascarados), un código de error en vez del mensaje, alertas técnicas por correo, `/panel/sistema` para Promotoría y un vigilante externo en GitHub.
- **Base de datos endurecida:** con la clave de `cc_app` ya no se crean cuentas, no se cambian claves ni roles, no se firma como el sistema y no se aprueba a nombre de otra persona: cada aprobación lleva la firma de la sesión de quien aprueba. Prod no arranca si un trigger o una función de la base no es el de esta versión.
- **Correcciones de la auditoría y QA:** ninguna operación de dinero (anulación de un pago y su nota de crédito, devoluciones, contracargos, correcciones, reaperturas de caja, anulación de cuotas, ingresos por revisar, parejas manuales) se completa sin SU solicitud aprobada y firmada, y una solicitud ya no se puede insertar aprobada; desactivar o reactivar una cuenta de Promotoría o Dirección se pide y lo aprueba otra persona; la primera Dirección sin solicitud, una sola vez por colegio; un rol anidado en `cc_negocio` no deja arrancar prod y el simulacro semanal relee los GRANT; la alerta «Faltan filas» sigue hasta que Promotoría la resuelve con motivo; los procesos que fallan en algún colegio dejan de latir; el monitoreo mira las dos conexiones; las alertas técnicas se reintentan; la caja, el estado de cuenta y el cronograma quedan en el registro de accesos; «Mis datos» ya no muestra los datos de otro apoderado; los logs ocultan el carné de extranjería y más formatos de celular.
- **Seguridad web:** HSTS de 1 año, cookie `__Host-CCSESION`, Cross-Origin-Opener-Policy y Cross-Origin-Resource-Policy en toda respuesta; desde una misma conexión, 5 intentos fallidos con un usuario o 20 con cualquiera y esa conexión espera 15 minutos (un tercero ya no bloquea la cuenta de la promotora); la sesión dura como máximo 10 horas; una prueba recorre las 132 rutas con id contra otra familia y otro colegio; todo POST exige CSRF; ninguna dependencia con una vulnerabilidad crítica o alta con arreglo (Tomcat y Jackson subidos a sus parches).
- **Ley 29733:** queda registrado quién del personal vio datos personales (fichas, búsquedas, morosos, llamada de control, importación y cambios de contacto) y Promotoría lo ve en la ficha de la familia y en «Quién vio datos personales», con alerta si alguien ve más de 50 fichas en un día; las familias ven «Mis datos» en el portal y piden acceso, rectificación, cancelación u oposición por «¿Algo no cuadra?», con plazo y alertas; aviso de privacidad público que la familia acepta al activar su cuenta; reporte de datos con plazo vencido. Los plazos los confirma el asesor legal.
- **Entrega:** manuales de una página por rol (`docs/manuales/`), guion de 9 videos, plan de capacitación, acta de conformidad y acta del simulacro de restauración (`docs/entrega/` y `docs/operacion/`).

### Panel de la promotora (sprint 6)
Diseño en `docs/arquitectura/sprint-6-panel-promotora.md`, con una sección de implementación por tanda (migraciones V21 y
V22, 61 triggers en MySQL) y correcciones de la auditoría y QA en `sprint-6-correcciones.md` (V23, 63 triggers). Guía
de una página para la promotora: `docs/operacion/guia-promotora.md`.
- **Panel en el celular** (`/panel`, solo Promotoría): lo cobrado hoy y en el mes, la deuda vencida por tramos, el % de pagos digitales, las rebajas del mes con quién aprobó, lo que espera aprobación y las alertas. Ninguna cifra se guarda ni se escribe a mano.
- **Reportes y Excel para el contador:** morosidad por grado (nunca por sección) e ingresos por medio de pago; el Excel no admite fórmulas, lleva datos mínimos y cada descarga queda en la bitácora con su código impreso en el archivo.
- **Resumen diario a las 19:30** por WhatsApp (o correo), con una foto de las cifras que la base compara al centavo con los libros, y la huella de las 19:00. Las cifras de un día ya enviado que cambian sin explicación son alerta crítica.
- **Alertas al celular** (una vez cada una, con texto fijo y sin nombres) y **aprobaciones desde el celular** con la misma regla de «quien pidió no aprueba».
- **Contacto del personal:** el celular o correo de una persona del personal solo cambia con su solicitud aprobada por otra persona (trigger en MySQL).
- **Llamada de control semanal** (`/panel/llamadas`, Promotoría y Dirección): el sistema elige con una semilla secreta familias que pagaron en efectivo (con prioridad para las que no usan el portal o tienen un solo apoderado); se pregunta primero cuánto y cuándo pagaron, y después se compara. «No confirma» es alerta crítica.

## Cómo probarlo en tu computadora
Requisito: Java 21.
```bash
git clone https://github.com/aziel123/cuentas-claras
cd cuentas-claras
git checkout claude/sprint-5-familias
./mvnw spring-boot:run
```
Abre http://localhost:8080. Usuarios de demostración, todos con la clave `demo-cuentas-claras-2026`:
- `promotor`
- `director`
- `administracion`
- `caja`
- `docente`
- `apoderado`
- `promotor.b`: otro colegio, para ver el aislamiento.

Para probar la importación están `docs/ux/ejemplo-importacion.xlsx` (con 2 errores a propósito) y `docs/ux/ejemplo-importacion-corregido.xlsx`.

Para probar la conciliación automática (al arrancar quedan la cuenta del colegio, un primer extracto confirmado y un Yape inventado por la cajera ayer):
1. Entra como `administracion`, ve a Conciliación › Extractos, descarga el extracto de ejemplo de ayer y súbelo.
2. Entra como `promotor` y confirma el extracto escribiendo el saldo final que muestra el log al arrancar («Conciliación de demostración lista…»). En el colegio, ese saldo se lee en la app del banco.
3. Mira Conciliación y el inicio de `promotor`: el Yape inventado aparece en rojo y los intereses quedan sin pareja para explicarlos.

## Decisiones para confirmar con el colegio
Todas tienen un valor por defecto ya implementado y se pueden cambiar.

| # | Tema | Valor actual |
|---|---|---|
| 1 | ¿Una persona puede ser Dirección y Administración a la vez? | **No** (prohibido por la auditoría) |
| 2 | Caja combinada con Dirección, Promotoría o Administración | **No** |
| 3 | Sesiones simultáneas por persona | **Una** (¿hay PC compartidas?) |
| 4 | "Olvidé mi contraseña" | Lo restablece Promotoría. La entrega por WhatsApp o correo llega en el sprint 4 |
| 5 | Navegadores de las PC del colegio | Se necesita Chrome, Edge, Safari o Firefox de 2024 en adelante |
| 6 | Fecha de corte del cronograma 2026 | **01/12/2026**: lo anterior entra como saldo inicial certificado por el contador |
| 7 | Pensión por nivel o por grado | **Por nivel** |
| 8 | Pensiones y vencimientos | **10, último día de marzo a diciembre**; matrícula al último día de febrero |
| 9 | Matrícula mayor que la pensión | **Bloqueada** (DS 005-2021-MINEDU). Confirmar con un asesor legal |
| 10 | Ingreso a mitad de año | Pensiones completas desde el mes de ingreso, **con aprobación** |
| 11 | Grados ofrecidos | Inicial 3–5, Primaria 1–6, Secundaria 1–5. ¿Hay cuna o aulas mixtas? |
| 12 | Documentos de los alumnos | DNI, CE y pasaporte. ¿Hay alumnos con CPP/PTP o sin documento? |
| 13 | Promotoría en alumnos y pensiones | Solo lectura (con datos personales ocultos en parte) más aprobaciones |
| 14 | Deudas de años anteriores a 2026 | Fuera del sistema, salvo que se pidan |
| 15 | Vencimiento en domingo o feriado | Se mantiene, sin mora |
| 16 | Pago parcial en caja | Desactivado en el piloto |
| 17 | Hora límite de cierre de caja | 19:00 |
| 18 | Quién deposita y quién verifica | Deposita la cajera al día hábil siguiente; verifica Administración |
| 19 | Comprobante durante el piloto | Simulado (sin valor tributario); el colegio sigue con su comprobante legal hasta conectar el OSE |
| 20 | IGV de pensiones | Inafecto. Confirmar con el contador |
| 21 | Días hábiles | De lunes a viernes, sin feriados (por ahora) |

### Sprint 4 (las más importantes)
| # | Tema | Valor actual |
|---|---|---|
| 22 | Proveedor de pasarela | Culqi u otro con Yape, Plin y tarjeta, página alojada y consulta por API. En el piloto, solo la simulada |
| 23 | Comisión de la pasarela | La asume el colegio; el padre paga el monto exacto de la cuota. Confirmar con un asesor legal |
| 24 | Banco de recaudación y su formato | Formato genérico hasta saberlo; el adaptador del banco se construye con un archivo de ejemplo |
| 25 | Cómo abona el banco la recaudación | Un abono por día (el lote). Si abona pago por pago, se cambia una propiedad y se empareja uno a uno |
| 26 | Glosa del abono de la recaudación | Sin patrón: la pareja queda sugerida. Si el banco pone un texto fijo (por ejemplo «RECAUD»), se configura y la pareja es exacta |
| 27 | «Un solo archivo» al día | Extracto + archivo de recaudación. Pedir al banco H2H o que la glosa traiga el código, para no subir el segundo |
| 28 | Quién confirma la recaudación y el extracto | Promotoría o Dirección, quien tenga acceso al banco; nunca quien subió |
| 29 | Frecuencia y hora límite del extracto | Diaria; cada extracto pendiente se confirma a ciegas con su propio saldo; hasta las 12:00 del día hábil siguiente |
| 30 | Tolerancias de la conciliación | ±2 días hábiles; S/ 0.00 en montos, también en liquidaciones |
| 31 | Muestreo diario | 3 movimientos del extracto y 3 líneas de recaudación para comparar con la app del banco |
| 32 | Cuentas que se concilian | Una cuenta corriente en soles, donde abonan Yape empresarial, la pasarela y la recaudación |
| 33 | Cargos del extracto | Se emparejan los reembolsos y las devoluciones; todo otro cargo se explica (no lo explica quien subió el extracto) y uno del mismo monto que un abono es CRÍTICO |
| 34 | Liquidaciones de la pasarela | Se leen por su API cada día a las 06:00. Si el proveedor no tiene API, se agrega la carga por archivo cuando se conozca su formato |
| 35 | Usuario de base de datos aparte para los procesos del sistema | No en este sprint; se evalúa en el sprint 7 |
| 36 | Cuenta en línea del apoderado | Enlace de un solo uso (48 h) entregado en persona o por un canal del titular; desde el sprint 5, por WhatsApp o correo |
| 37 | Contracargos | Alerta CRÍTICA y anulación de tipo CONTRACARGO, sin reembolso, que aprueba Promotoría o Dirección |

### Sprint 6
| # | Tema | Valor actual |
|---|---|---|
| 64 | Quién ve qué | Panel: Promotoría. Morosidad e ingresos en pantalla: Promotoría, Dirección y Administración. Excel: Promotoría y Administración. Llamada de control: Promotoría y Dirección. Caja y Docente: nada |
| 65 | % de pagos digitales | Por número de pagos, con el % por monto debajo |
| 66 | Familia morosa | Al menos una cuota con saldo vencido; tramos 1–30, 31–60, 61–90 y más de 90 días |
| 67 | Cobrado y anulado | Cobrado = pagos vigentes por día de caja; lo anulado, aparte, en el periodo en que se aprobó |
| 68 | Hora del resumen diario | 19:30, de lunes a sábado; domingo y feriado solo si hubo cobros |
| 69 | Destinatarios del resumen | Cada Promotor activo (WhatsApp, con correo si falla) y el correo del contador si el DBA lo configura |
| 70 | Alertas al celular | Las críticas más «caja sin cerrar a la hora límite» y «anulación por aprobar»; de 07:00 a 21:00; 10 por persona y día |
| 71 | Anulaciones por aprobar a Dirección | Sí, nunca a quien la pidió ni a la cajera del pago |
| 72 | Aprobar desde el celular | Misma bandeja, con sesión; sin enlaces que aprueben; una sesión por persona |
| 73 | Exportación | Solo .xlsx; hasta 12 meses por archivo y 20 descargas por persona y día |
| 74 | Datos del Excel del contador | Sin DNI, nombres de alumnos ni contactos; familia por código; RUC solo en facturas |
| 75 | Montos en el Excel | Como número con dos decimales; los totales de control también como texto |
| 76 | Lista de familias morosas | Solo en pantalla; no se exporta |
| 77 | Llamada de control | 3 familias por semana que pagaron en efectivo en las 5 semanas anteriores: 2 con prioridad (sin portal o con un solo apoderado) y 1 al azar entre todas |
| 78 | Cambio de celular o correo del personal | Lo pide el titular o Promotoría; lo aprueba otra persona; aviso al contacto anterior |
| 79 | Segundo factor para aprobar desde el celular | No en este sprint (sesión de 30 minutos); se evalúa en el sprint 7 |
| 80 | «Excepciones grandes» solo para Promotoría | Sin umbral: aprueba Promotoría o Dirección |
| 81 | Ventana del recálculo de las fotos del resumen | 35 días |

La lista completa está en los documentos de `docs/arquitectura/`, incluidas la sección 16 de `sprint-3-caja.md` y la 17 de `sprint-4-cero-digitacion.md`.

### Sprint 7 (por confirmar; valores por defecto del diseño, sección 18)
| # | Tema | Valor actual |
|---|---|---|
| 82 | Usuario de base para los procesos (decisión 35) | Uno, `cc_sistema`, para los procesos y la identidad |
| 83 | Duración máxima de una sesión | 10 horas aunque haya actividad, y 30 minutos de inactividad |
| 84 | Roles que asigna Promotoría sin aprobación | ADMINISTRACION, CAJA y DOCENTE; PROMOTOR y DIRECTOR con solicitud aprobada por otra persona |
| 85 | Alta de una cuenta de Promotoría o Dirección | Nace sin ese rol y se le asigna con la solicitud |
| 86 | Segundo factor (decisión 79) | No: firma de sesión, sesión única, 30 minutos de inactividad y 10 horas como máximo |
| 87 | Muestra de la llamada de control | Lunes a las 00:10, la fija el sistema |
| 88 | Respaldo y ventana de despliegue | Diario a las 02:30 y antes de cada despliegue; despliegues después de las 21:00 o en fin de semana |
| 89 | Dónde se guardan los respaldos | Almacenamiento de objetos con bloqueo de objetos, a nombre del colegio; 35 diarios y 12 mensuales |
| 90 | Quién puede abrir un respaldo | Dos claves privadas: Promotoría y el responsable técnico |
| 91 | Simulacros de restauración | Semanal y automático; mensual y presencial con Promotoría |
| 92 | Copia física adicional | No |
| 93 | A quién llegan las alertas técnicas | Al responsable técnico; «sin respaldo» y «faltan filas» también a Promotoría |
| 94 | Vigilante externo | Workflow de GitHub cada 15 minutos |
| 95 | Conservación de los logs técnicos | 30 días |
| 96 | Registro de quién ve datos personales | Fichas, búsquedas, morosos, llamada de control, importación y cambios de contacto; 2 años; alerta con más de 50 fichas al día |
| 97 | Pedidos sobre datos personales | «Mis datos» al instante; pedidos por «¿Algo no cuadra?»; 20 días hábiles el acceso y 10 lo demás, con aviso a los 7 (a confirmar por el asesor legal) |
| 98 | Plazos de conservación | Lo financiero mientras no prescriba (lo fija el contador); contactos de familias que se fueron sin deuda, 1 año (a confirmar por el asesor legal) |
| 99 | Aviso de privacidad | Lo redacta y aprueba el asesor legal; el sistema lo muestra (borrador en `/privacidad`) y registra su aceptación |
| 100 | Inscripción de los bancos de datos y flujo transfronterizo | Trámite del colegio con su asesor, antes de la matrícula 2027 |
| 101 | Responsable de los datos personales | Dirección |
| 102 | Dependencias vulnerables | OSV-Scanner en cada PR (bloquea CRÍTICA o ALTA con arreglo) y Dependabot cada semana |
| 103 | Cookie y HSTS | `__Host-CCSESION`; HSTS de 1 año con subdominios, sin `preload` (exige https) |
| 104 | Intentos de ingreso por conexión | 5 con un usuario o 20 con cualquiera en 15 minutos; la cuenta se bloquea con 15 desde varias conexiones |
| 105 | Fechas de la capacitación | Semana del 25 de enero (sesiones S1 a S6 de `docs/entrega/capacitacion.md`) |
| 106 | Publicación de los videos | Sin listar, en la cuenta del colegio, con datos de demostración |
| 107 | Soporte después del acta | 30 días, por WhatsApp y correo, en horario escolar |

## Pendiente fuera del código
- [ ] Reunión de descubrimiento con el colegio (kit en `docs/ux/`).
- [ ] Trámites largos: verificación de WhatsApp Business, proveedor de comprobantes electrónicos (OSE), pasarela de pagos, Yape o Plin empresarial.
- [ ] Elegir el hosting (decisión D3) para tener un entorno de pruebas en internet.
- [ ] Revisar y unir las ramas a `main` mediante un PR, para que corra el CI de GitHub, incluido el job de MySQL.
- [x] Auditoría del sprint 4 (`auditor-seguridad-antifraude` y `qa-tester`) y sus correcciones (`docs/arquitectura/sprint-4-correcciones.md`).
- [ ] Pedir al banco un extracto y un archivo de recaudación reales (anonimizados) para construir sus adaptadores, y preguntar por H2H y por la glosa del abono de la recaudación.
- [ ] Elegir la pasarela y confirmar si tiene API de liquidaciones.
- [x] Sprint 7: QA (`qa-tester`) y auditoría (`auditor-seguridad-antifraude`) de las 3 tandas y sus correcciones (`docs/arquitectura/sprint-7-correcciones.md`).
- [ ] Sprint 7: el PR hacia `main` (CI completo: jobs `mysql`, `respaldo` y `dependencias`).
- [ ] Asesor legal: confirmar los plazos de la Ley 29733 (20 y 10 días hábiles, 48 horas, 1 y 2 años), redactar el aviso de privacidad y confirmar qué se responde a un pedido de cancelación.
- [ ] El colegio: inscribir sus bancos de datos y declarar el flujo transfronterizo (Meta y, si aplica, el hosting); nombrar al responsable de los datos personales.
- [ ] Contratar el almacenamiento de objetos con bloqueo de objetos y generar las dos claves `age` (Promotoría y responsable técnico).
- [ ] Primer simulacro presencial de restauración en el servidor del colegio (`docs/operacion/acta-simulacro-restauracion.md`), capacitación (S1 a S6), grabar los videos 1, 2, 4 y 7 y firmar el acta de conformidad (`docs/entrega/`).

## Riesgos conocidos
- La aplicación está pensada para **una sola instancia**: las sesiones y algunos límites viven en memoria.
- La clave temporal del **personal** todavía la ve quien la genera (la del apoderado ya no: enlace de un solo uso). La entrega directa al titular llega con WhatsApp y correo en el sprint 5.
- La alerta de «cuenta activada desde la IP de quien la creó» no detecta a quien la activa desde otra conexión; lo cubren el apoderado (no puede entrar y avisa) y el restablecimiento por Promotoría.
- Durante el piloto, la única constancia del padre es la boleta impresa (WhatsApp llega en el sprint 4).
- La huella de la bitácora solo detecta un recorte si la promotora la anota. Desde el sprint 4 se le enviará a diario.
- Los triggers de MySQL requieren `log_bin_trust_function_creators`; está documentado en `docs/operacion/mysql-usuarios.md`.
- **Extracto de varios días:** solo se confirma a ciegas el saldo de cierre; un abono y un cargo inventados que se compensan dentro del mismo extracto los detecta la alerta CRÍTICA de compensación (mismo monto) o quedan como cargos sin explicar que revisa otra persona. Conviene subir el extracto a diario.
- El muestreo de las verificaciones de caja (sprint 3) todavía usa la fecha como semilla; el del extracto ya usa una semilla secreta.
- **Colusión entre quien sube y quien confirma** el extracto o la recaudación: queda fuera del control. La mitigan la bitácora, el archivo original con su SHA-256 y el estado de cuenta oficial del banco. Se recomienda un cierre mensual en el que el contador compare a ciegas los abonos del mes con el estado de cuenta.
- Los riesgos aceptados para la entrega de la versión 1 están en `docs/arquitectura/sprint-7-correcciones.md` («Riesgos residuales aceptados para la entrega»); van al acta de conformidad.
- Desde el sprint 7 (tanda 2), con la clave de `cc_app` ya no se firma como `sistema.*` ni se aprueba a nombre de otra persona. Lo que una persona hace por sí misma (un cobro, un pedido) sí se puede registrar con la clave de `cc_app` «como la cajera X»: lo detectan el aviso a la familia, el cierre a ciegas, la conciliación y la bitácora sin su evento.
- Quien toma el servidor de la aplicación tiene las claves de `cc_app` y `cc_sistema`, la clave HMAC y los secretos de las sesiones abiertas: el sprint 7 lo hace visible (huella diaria, manifiestos de respaldo que no se pueden alterar, avisos a las familias), no lo evita.
- El límite de intentos de ingreso, los latidos y las sesiones viven en memoria (una sola instancia). Un ataque desde 3 conexiones o más todavía puede bloquear una cuenta 15 minutos (decisión 104).
- Ley 29733: los plazos salen de fuentes secundarias (no del texto oficial del DS 016-2024-JUS) y los confirma el asesor legal; la anonimización de los contactos todavía es manual (reporte «Datos con plazo vencido»).
- Los formatos reales de los bancos y de la pasarela no se pudieron verificar: el extracto y la recaudación usan un formato genérico hasta tener un archivo de ejemplo.
- Los feriados no se consideran días hábiles todavía: un feriado puede adelantar una alerta de «no aparece en el banco» o «sin abono»; se resuelve con nota.
- **El panel y sus controles dependen de que la promotora los mire:** el resumen diario, las alertas al celular y la llamada de control semanal no sirven si nadie los lee o nadie llama. Lo mitigan la alerta «el resumen no salió», el recordatorio del sábado de las llamadas que faltan y la capacitación (`docs/manuales/promotoria.md`).
- **La llamada de control** detecta efectivo no registrado solo en la muestra de la semana (3 familias). Una familia que confirma de memoria un monto equivocado da un falso «Confirma»: por eso se pregunta primero y se compara después.
- El comprobante simulado no tiene validez tributaria: mientras no se active el OSE, el colegio sigue emitiendo su comprobante legal también por los pagos en línea y por banco.
