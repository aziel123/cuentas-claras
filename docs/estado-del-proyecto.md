# Estado del proyecto · Cuentas Claras

> Última actualización: 6 de octubre de 2026.
> Resumen para retomar el trabajo: qué está hecho, cómo probarlo y qué hay que decidir con el colegio.

## Avance

| Sprint | Estado | Rama | Pruebas |
|---|---|---|---|
| 0 · Arranque | ✅ Terminado | `main` | 3 |
| 1 · Fundaciones (seguridad, roles, auditoría) | ✅ Terminado, auditado y corregido | `claude/sprint-1-fundaciones` | 317 |
| 2 · Datos del colegio (alumnos, Excel, pensiones, saldo inicial) | ✅ Terminado, auditado y corregido | `claude/sprint-2-datos-colegio` | 713 |
| 3 · Caja (pagos, comprobantes, anulaciones, descuentos, cierre ciego, conciliación) | ✅ Terminado, auditado y corregido | `claude/sprint-3-correcciones` | 967 |
| 4 · Cero digitación (pago en línea, comprobante automático, recaudación bancaria, conciliación automática) | Implementado en 3 tandas; falta la auditoría y sus correcciones | `claude/sprint-4-cero-digitacion` | 1175 |

El sprint 4 se implementó en 3 tandas verificadas, cada una con su migración y probada también contra MySQL 8 real:

| Tanda | Qué trae | Migración | Pruebas al cerrarla |
|---|---|---|---|
| 1 | Comprobante automático (envío al OSE con reintentos) y pago en línea con la pasarela simulada | V13 | 1026 |
| 2 | Recaudación bancaria: archivo del banco confirmado a ciegas y aplicado por el sistema | V14 | 1101 |
| 3 | Extracto bancario encadenado y conciliación automática | V15 | 1175 (44 de MySQL real, que se omiten sin `CC_PRUEBA_MYSQL`) |

Las ramas están **apiladas**: cada una parte de la anterior y contiene todo su trabajo. La más completa es `claude/sprint-4-cero-digitacion`. Ninguna está unida a `main` todavía. El CI de GitHub solo corre en `main` y en los PR, así que se ejecutará por primera vez cuando se abra el PR. Todas las pruebas, incluidas las de MySQL 8 real, se corrieron localmente durante el desarrollo.

Cada sprint siguió el mismo flujo:
1. `arquitecto-software` diseña.
2. `backend-spring` implementa en tandas verificadas.
3. `auditor-seguridad-antifraude` y `qa-tester` revisan en paralelo.
4. `backend-spring` corrige todos los hallazgos.

Los diseños están en `docs/arquitectura/`.

## Qué hace hoy la plataforma

### Seguridad y control
- Inicio de sesión con bloqueo tras 5 intentos fallidos. Los intentos en paralelo no evitan el bloqueo.
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
- **Pago en línea** de los padres (Yape, Plin o tarjeta) con la pasarela simulada: el pago se registra solo cuando la consulta a la pasarela lo confirma, con su boleta. La simulada no puede marcar pagos en producción.
- **Comprobante automático**: cola de envío al OSE (simulado por ahora) con reintentos y alerta antes del plazo legal. Un comprobante rechazado se reemite con número nuevo, sin huecos.
- **Recaudación bancaria**: Administración sube el archivo del banco; otra persona escribe a ciegas el total que ve en el banco y recién entonces el sistema aplica los pagos. Lo que no cuadra queda por revisar y lo resuelve otra persona.
- **Extracto y conciliación automática** (tanda 3):
  - Administración sube el extracto del banco (CSV o Excel en formato genérico; los formatos de cada banco se agregan como adaptadores). Se guarda el archivo original con su SHA-256.
  - Cada extracto **continúa al anterior**: empieza el día siguiente y con su saldo final. Un día ya cargado que vuelve distinto se rechaza y alerta a Promotoría («el banco no cambia el pasado»).
  - Promotoría o Dirección **escribe a ciegas el saldo final** que ve en su app del banco. Quien subió el extracto no lo confirma. Dos saldos que no coinciden dejan el extracto RECHAZADO.
  - El sistema empareja cada movimiento con los pagos digitales de caja, los depósitos, las liquidaciones de la pasarela (netas de comisión e IGV) y los abonos de la recaudación. Las parejas **exactas** (misma operación y monto) se confirman solas; las **sugeridas** las confirma alguien de Administración que no cobró, no depositó ni subió el lote.
  - La pantalla **muestra solo las diferencias**. Lo que debía estar en el banco y no está aparece **en rojo con quién lo registró**: un Yape inventado en caja sale en rojo para Promotoría el día hábil siguiente, en cuanto se confirma el extracto de ese día.
  - Los movimientos ajenos a la cobranza (intereses, comisiones) se explican con categoría y nota. Todo lo resuelto a mano queda en la bitácora y en un resumen semanal para Promotoría.
  - La verificación a ciegas del sprint 3 queda para las excepciones (en Conciliación › Verificación manual).
- 40 triggers en MySQL (12 nuevos en el sprint). Si falta alguno, la aplicación no arranca.

## Cómo probarlo en tu computadora
Requisito: Java 21.
```bash
git clone https://github.com/aziel123/cuentas-claras
cd cuentas-claras
git checkout claude/sprint-4-cero-digitacion
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
| 29 | Frecuencia y hora límite del extracto | Diaria, con confirmación en cadena (el lunes sella el fin de semana); hasta las 12:00 del día hábil siguiente |
| 30 | Tolerancias de la conciliación | ±2 días hábiles; S/ 0.00 en montos, también en liquidaciones |
| 31 | Muestreo diario | 3 movimientos del extracto y 3 líneas de recaudación para comparar con la app del banco |
| 32 | Cuentas que se concilian | Una cuenta corriente en soles, donde abonan Yape empresarial, la pasarela y la recaudación |
| 33 | Cargos del extracto | Solo se emparejan los reembolsos; los demás egresos no se revisan aquí |
| 34 | Liquidaciones de la pasarela | Se leen por su API cada día a las 06:00. Si el proveedor no tiene API, se agrega la carga por archivo cuando se conozca su formato |
| 35 | Usuario de base de datos aparte para los procesos del sistema | No en este sprint; se evalúa en el sprint 7 |

La lista completa está en los documentos de `docs/arquitectura/`, incluidas la sección 16 de `sprint-3-caja.md` y la 17 de `sprint-4-cero-digitacion.md`.

## Pendiente fuera del código
- [ ] Reunión de descubrimiento con el colegio (kit en `docs/ux/`).
- [ ] Trámites largos: verificación de WhatsApp Business, proveedor de comprobantes electrónicos (OSE), pasarela de pagos, Yape o Plin empresarial.
- [ ] Elegir el hosting (decisión D3) para tener un entorno de pruebas en internet.
- [ ] Revisar y unir las ramas a `main` mediante un PR, para que corra el CI de GitHub, incluido el job de MySQL.
- [ ] Auditoría del sprint 4 (`auditor-seguridad-antifraude` y `qa-tester`) y sus correcciones.
- [ ] Pedir al banco un extracto y un archivo de recaudación reales (anonimizados) para construir sus adaptadores, y preguntar por H2H y por la glosa del abono de la recaudación.
- [ ] Elegir la pasarela y confirmar si tiene API de liquidaciones.

## Riesgos conocidos
- La aplicación está pensada para **una sola instancia**: las sesiones y algunos límites viven en memoria.
- La clave temporal todavía la ve quien la genera. Se corrige en el sprint 4 con la entrega directa al titular.
- Durante el piloto, la única constancia del padre es la boleta impresa (WhatsApp llega en el sprint 4).
- La huella de la bitácora solo detecta un recorte si la promotora la anota. Desde el sprint 4 se le enviará a diario.
- Los triggers de MySQL requieren `log_bin_trust_function_creators`; está documentado en `docs/operacion/mysql-usuarios.md`.
- **Colusión entre quien sube y quien confirma** el extracto o la recaudación: queda fuera del control. La mitigan la bitácora, el archivo original con su SHA-256 y el estado de cuenta oficial del banco. Se recomienda un cierre mensual en el que el contador compare a ciegas los abonos del mes con el estado de cuenta.
- `cc_app` puede escribir cualquier texto en `creado_por`: con sus credenciales, alguien podría firmar como `sistema.conciliacion`. Lo frenan los triggers (una verificación automática exige una partida confirmada sobre un extracto confirmado) y la bitácora.
- Los formatos reales de los bancos y de la pasarela no se pudieron verificar: el extracto y la recaudación usan un formato genérico hasta tener un archivo de ejemplo.
- Los feriados no se consideran días hábiles todavía: un feriado puede adelantar una alerta de «no aparece en el banco» o «sin abono»; se resuelve con nota.
- El comprobante simulado no tiene validez tributaria: mientras no se active el OSE, el colegio sigue emitiendo su comprobante legal también por los pagos en línea y por banco.
