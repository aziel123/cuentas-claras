# Guion de los videos cortos · Cuentas Claras

> Sprint 7, entrega (sección 15.2 de `docs/arquitectura/sprint-7-endurecimiento.md`). Nueve videos de 60 a 120 segundos, uno o dos por rol. **Los videos 1, 2, 4 y 7 son requisito del acta de conformidad**; los demás pueden llegar en las 2 semanas siguientes.

## Formato (igual para los nueve)
- **Grabación de pantalla real** de la plataforma en el perfil `dev` (`./mvnw spring-boot:run`), con los **datos de demostración**: el colegio demo, la Familia Quispe Huamán (Rosa Huamán Ccori con Mateo y Valeria), las demás familias de prueba y los usuarios `promotor`, `director`, `administracion`, `caja` y `apoderado`. **Nunca datos reales** ni la base del piloto o de producción.
- **Voz en off** del equipo, en español claro del Perú y en segunda persona («tu caja», «tus boletas»). Unas 130 palabras por minuto: el texto de cada escena está medido para la duración indicada.
- **Subtítulos** siempre (archivo `.srt` con el mismo texto de la voz): mucha gente ve los videos sin sonido.
- **Patrón:** qué vas a lograr → de 3 a 5 pasos → lo que el sistema no te deja hacer y dónde pedir ayuda.
- **Pantallas de celular** (videos 4 a 8): modo responsivo del navegador a 390 × 844 o un celular de prueba; de computadora (videos 1 a 3 y 9): 1920 × 1080 con zoom del navegador al 125 %.
- **Publicación sin listar** (decisión 106), en la cuenta del colegio (YouTube o Drive). Cada enlace genera el código QR que va en el manual del rol.

### Antes de grabar
- [ ] Arrancar `dev` desde cero (base en memoria limpia) para que los datos sean los de la demostración.
- [ ] No mostrar la consola, el log ni la barra de marcadores: el log de `dev` imprime enlaces de activación y el saldo de la conciliación de demostración. Cópialos fuera de cámara.
- [ ] No escribir ni mostrar claves en pantalla: el ingreso se graba con el campo de clave ya lleno o se corta.
- [ ] Silenciar notificaciones del equipo y cerrar otras pestañas.
- [ ] Si una pantalla tiene un texto que no coincide con el guion (por ejemplo, un aviso que ya cambió), avisar antes de grabar: el video muestra lo que la persona verá.

### Después de grabar
- [ ] Subtítulos revisados por otra persona.
- [ ] Título «Cuentas Claras · N. Nombre del video», descripción con el enlace al manual del rol y el aviso «Datos de demostración: no son familias reales».
- [ ] Publicar sin listar, generar el código QR y pegarlo en el manual (ver `docs/manuales/README.md`).
- [ ] Anotar el enlace en la tabla de publicación (al final) y en los anexos del acta.

---

## Video 1 · Cobrar y entregar la boleta (Caja, 90 s) · Requisito del acta
**Objetivo:** que la cajera cobre una pensión en menos de un minuto, sin escribir montos, y entregue la boleta.
**Usuario:** `caja`. **Familia:** Quispe Huamán.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Cobrar y entregar la boleta» | «En este video vas a cobrar una pensión y entregar la boleta en menos de un minuto.» |
| 2 | *Caja › Cobrar*. Se escribe «quispe» y aparecen los resultados con «Debe S/ … vencido» o «Al día» | «Busca al alumno o al apoderado por su nombre o su DNI. El sistema ya sabe cuánto debe cada familia.» |
| 3 | Ficha de la Familia Quispe Huamán: *Cuotas por pagar* de Mateo y Valeria. Se marca la pensión vencida de Mateo | «Marca las cuotas que el apoderado quiere pagar. Fíjate que no escribes ningún monto: lo pone el sistema, con las pensiones aprobadas.» |
| 4 | *¿Cómo paga?* → Efectivo → **Revisar cobro** | «Elige cómo paga y toca Revisar cobro.» |
| 5 | *Revisa el cobro*: total destacado. Se escribe lo recibido en *Recibido (S/)* y se toca **Cobrar S/ …** | «Revisa el total con el apoderado. Escribe cuánto te entregó y toca Cobrar.» |
| 6 | *Pago registrado · B001-…* con «Entrega S/ … de vuelto». Se toca **Imprimir boleta** | «El sistema te dice el vuelto y emite la boleta en ese momento. Imprímela y entrégala. La boleta también queda en el portal de la familia.» |
| 7 | Vuelta a *Cobrar*, otra familia, medio Yape: campo *N.° de operación (obligatorio)* | «Si paga con Yape, Plin o transferencia, copia el número de operación de su constancia. Administración lo verifica con el banco.» |
| 8 | *Pagos de hoy*: el aviso «Caja no puede borrar ni anular pagos: solo pedirlo» | Cierre (abajo) |

**Cierre (lo que el sistema no te deja hacer y dónde pedir ayuda):** «El sistema no te deja cambiar montos ni borrar un pago. Si te equivocaste, en Pagos de hoy pides la anulación o la corrección, con el motivo, y la aprueba Promotoría o Dirección. ¿Dudas? Pregunta a Promotoría. Todo esto está en tu manual de una página.»

## Video 2 · Cerrar la caja a ciegas (Caja, 90 s) · Requisito del acta
**Objetivo:** que la cajera cierre su caja contando sin ver lo registrado, entienda el único reconteo y registre el depósito.
**Usuario:** `caja`, con cobros en efectivo del día (los de la demostración más el del video 1). Para mostrar el reconteo, se escribe a propósito un conteo distinto.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Cerrar la caja a ciegas» | «Al final del día cierras tu caja. Así se hace.» |
| 2 | *Caja › Cerrar caja*: «Cuenta el efectivo de tu caja», con el fondo fijo | «Cuenta todo el efectivo, incluido el fondo fijo. Todavía no ves lo registrado: así el conteo es tuyo y nadie lo puede acomodar.» |
| 3 | Se abre *Contar por billetes y monedas* y se llenan algunas cantidades | «Puedes escribir el total o llenar los billetes y monedas: el sistema suma por ti.» |
| 4 | **Registrar conteo** → «No coincide con lo registrado. Vuelve a contar…» | «Si no coincide, el sistema te pide volver a contar, sin decirte cuánto falta.» |
| 5 | *Vuelve a contar*: nuevo total y *¿Qué pasó? (obligatorio)* con «Conté mal los billetes de S/ 20» → **Registrar reconteo y cerrar** | «Tienes un solo reconteo. Explica qué pasó con tus palabras. Este reconteo cierra la caja.» |
| 6 | Resultado: *Registrado (esperado)*, *Contado al reconteo* y *Diferencia*, con «Hubo una diferencia: se avisó a Promotoría y Dirección…» | «Ahora sí ves lo registrado y la diferencia. Promotoría y Dirección revisan el cierre contigo: no pongas ni saques dinero de tu bolsillo.» |
| 7 | *Registrar depósito*: cuenta, N.° de operación del voucher, fecha y monto | «Al día hábil siguiente deposita lo contado menos el fondo fijo y registra el voucher.» |

**Cierre:** «El sistema no te deja ver lo registrado antes de contar, contar más de dos veces ni aprobar tu propio cierre. Si necesitas reabrir la caja el mismo día, pídelo antes de depositar. ¿Dudas? Pregunta a Promotoría.»

## Video 3 · El extracto de cada mañana (Administración, 120 s)
**Objetivo:** que Administración suba el extracto, mire solo las diferencias y explique un movimiento.
**Usuarios:** `administracion` y, para la confirmación, `promotor`. **Datos:** la conciliación de demostración (cuenta del colegio, primer extracto confirmado, el extracto de ejemplo de ayer y un Yape inventado por la cajera).

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «El extracto de cada mañana» | «Cada mañana subes el extracto del banco y el sistema concilia solo. Tú miras solo lo que no cuadra.» |
| 2 | *Conciliación bancaria › Extractos*. Se descarga el extracto de ejemplo (en el colegio: la banca por internet) | «Descarga de la banca por internet el extracto de los días completos hasta ayer. No lo abras ni lo cambies.» |
| 3 | Se sube el archivo → *Revisa antes de registrar*: el saldo final no se muestra | «Súbelo y revisa. El saldo no aparece a propósito: lo escribe a ciegas otra persona mirando su app del banco.» |
| 4 | Se registra → «Lo confirma Promotoría o Dirección» | «Tú no confirmas tu propio extracto.» |
| 5 | Celular de `promotor`: *Confirma el extracto mirando tu app del banco*, saldo escrito → **Confirmar y conciliar** | «Promotoría escribe el saldo que ve en el banco. Si coincide, el sistema empareja los Yape, depósitos, pagos en línea y recaudación.» |
| 6 | De vuelta como `administracion`: *Diferencias con el banco*. El Yape inventado aparece en rojo con quién lo registró | «Lo que debía estar en el banco y no está sale en rojo con quién lo registró. Revísalo hoy.» |
| 7 | Movimiento sin pareja (intereses): se elige la categoría, se escribe la nota y se guarda | «El dinero que entró y nadie registró, como los intereses, se empareja o se explica con una nota.» |
| 8 | Texto de la pantalla: «Lo explica alguien que no subió el extracto» en la sección de cargos | Cierre |

**Cierre:** «El sistema no te deja confirmar el extracto que subiste ni explicar sus cargos: lo confirma otra persona. Si un movimiento de la muestra no está en el banco, no confirmes y avisa a Dirección. ¿Dudas? Pregunta a Promotoría.»

## Video 4 · Aprobar desde el celular (Dirección y Promotoría, 60 s) · Requisito del acta
**Objetivo:** que Dirección y Promotoría resuelvan una solicitud desde el celular, leyendo el motivo, y vean que no pueden aprobar lo que pidieron.
**Preparación (fuera de cámara):** como `caja`, pedir la anulación de un pago de hoy con un motivo; como `director`, pedir el cambio de su propio celular en *Mi celular y correo*.
**Usuario:** `director` en el celular.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Aprobar desde el celular» | «Puedes aprobar desde el celular con las mismas reglas que en la computadora.» |
| 2 | Ingreso con usuario y clave (clave ya escrita) | «El aviso que te llega solo abre la pantalla: para aprobar necesitas tu sesión.» |
| 3 | *Para aprobar*: tarjeta «Anulación de pago» con el resumen, «Pedida por …» y el motivo → **Ver y resolver** | «Lee qué se pide, quién lo pidió y por qué.» |
| 4 | Detalle → **Aprobar** → «¿Aprobar: Anulación de pago?» → **Sí, aprobar** | «Si está bien, apruébalo y confirma. Tu aprobación queda firmada con tu sesión y en la bitácora.» |
| 5 | Tarjeta «Cambio de celular o correo del personal» con «La resuelve otra persona» y **Ver detalle** (sin Aprobar) | «Si lo pediste tú, no aparece el botón: lo resuelve otra persona.» |

**Cierre:** «El sistema no te deja aprobar lo que pediste o en lo que participaste, ni aprobar desde un enlace. Si entras desde el celular, se cierra tu sesión de la computadora. ¿Dudas? Pregunta al responsable técnico.»

## Video 5 · El panel y el resumen de las 19:30 (Promotoría, 90 s)
**Objetivo:** que Promotoría lea el panel en un minuto, empiece por las alertas en rojo y sepa qué hacer si el resumen no llega.
**Usuario:** `promotor` en el celular.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Tu panel y el resumen del día» | «Tu panel te dice cómo va el colegio hoy, desde el celular.» |
| 2 | *Panel del colegio* arriba: «Todas las cifras salen de los pagos y cuotas registrados…» | «Ninguna cifra se escribe a mano: salen de los pagos y cuotas registrados.» |
| 3 | *Alertas*: las críticas en rojo → **Revisar** en una | «Empieza siempre por las alertas en rojo. Toca Revisar y ve directo al detalle.» |
| 4 | **Para aprobar: N** | «Aquí están las solicitudes que esperan tu aprobación.» |
| 5 | *Hoy*, *Este mes* y *Deuda vencida* | «Lo cobrado hoy, el porcentaje digital, el mes y la deuda vencida por tramos.» |
| 6 | *Resumen de hoy* y **Ver resúmenes enviados** | «A las 19:30 te llega el resumen por WhatsApp o correo, con la huella de la bitácora. Si a las 20:00 no llegó, avisa al responsable técnico.» |
| 7 | **Estado técnico y respaldos**: «Respaldo diario · Al día» | «Aquí ves si el respaldo de anoche está al día. Si algo sale en rojo, avisa: no tienes que arreglarlo tú.» |

**Cierre:** «El sistema no te deja corregir cifras ya enviadas: si cambian sin explicación, es una alerta crítica. Guarda tu clave de la bitácora fuera del sistema. ¿Dudas? Pregunta al responsable técnico.»

## Video 6 · La llamada de control (Promotoría, 90 s)
**Objetivo:** que Promotoría haga la llamada semanal preguntando primero y registre el resultado.
**Usuario:** `promotor`. **Datos:** la muestra de la semana de la demostración (números de celular ficticios).

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «La llamada de control» | «Cada semana llamas a unas pocas familias para confirmar lo que pagaron.» |
| 2 | *Llamadas de control*: «Semana del …» y las familias elegidas con su motivo | «El sistema elige al azar familias que pagaron en efectivo o tienen deuda vencida. Nadie más sabe a quién vas a llamar.» |
| 3 | Botón para llamar al celular registrado | «Llama al celular registrado de la familia.» |
| 4 | (Sin pantalla nueva) | «Pregunta primero: ¿cuánto pagó en el colegio en las últimas semanas y qué día? No digas los montos.» |
| 5 | **Ya me dijo: ver lo registrado** → pagos con «Lo registró …» | «Cuando te responda, toca Ya me dijo y compara con lo registrado.» |
| 6 | Se elige **Confirma** → **Registrar resultado** | «Registra el resultado. No se puede cambiar.» |
| 7 | Otra familia: **No confirma** con la nota de ejemplo | «Si no confirma, escribe lo que te dijo. Es una alerta crítica: revisa sus pagos y la caja de quien cobró.» |

**Cierre:** «Si no contesta, vuelve a llamar una hora después; si tampoco, el sistema elige otra familia. Si una semana no puedes, delega en Dirección. Hazlas antes de que termine el domingo. ¿Dudas? Pregunta al responsable técnico.»

## Video 7 · Tu portal en el celular (Familias, 90 s) · Requisito del acta
**Objetivo:** que una familia active su cuenta, vea lo que debe, sepa cómo pagar y encuentre su boleta.
**Preparación (fuera de cámara):** como `promotor`, crear el acceso en línea de otro apoderado de la demostración y copiar del log el enlace de activación. **Usuario:** ese apoderado; para el estado de cuenta, `apoderado` (Rosa Huamán Ccori).

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Tu portal en el celular» | «Desde tu celular ves lo que debes, pagas y descargas tus boletas.» |
| 2 | *Activa tu cuenta*: número de documento, nueva clave, repetir, enlace al Aviso de privacidad → **Activar mi cuenta** | «Abre el enlace que te llegó por WhatsApp o correo. Escribe tu número de documento y elige tu clave. Nadie más la conoce, ni el colegio.» |
| 3 | Ingreso: «Listo, tu cuenta está activa…» | «Ingresa con tu documento y tu clave.» |
| 4 | *Mi familia*: *Total por pagar*, *Lo que debes* por hijo | «Arriba ves cuánto debes y el próximo vencimiento de cada hijo.» |
| 5 | Se marcan cuotas → **Pagar en línea** (pasarela simulada con la marca «PAGO SIMULADO»); luego *Pagar en el banco* con el código de pago | «Puedes pagar con Yape, Plin o tarjeta cuando el colegio lo active, o en el banco con el código de pago de tu hijo.» |
| 6 | **Mis boletas** y una boleta abierta | «Tus boletas quedan aquí. Y cada pago te llega por WhatsApp o correo: revisa que coincida.» |

**Cierre:** «Los avisos de tus pagos no se apagan: son tu constancia. El colegio nunca te pedirá tu clave. ¿Algo no cuadra? Avísanos desde el portal.»

## Video 8 · ¿Algo no cuadra? y Mis datos (Familias, 60 s)
**Objetivo:** que la familia reporte un pago que no aparece, vea sus datos y pida una corrección.
**Usuario:** `apoderado`.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «¿Algo no cuadra? y Mis datos» | «Si algo no cuadra, avísanos desde tu celular.» |
| 2 | *¿Algo no cuadra?*: «Pagué y no aparece», el pago, el texto de ejemplo → **Enviar a Promotoría** → «Lo recibió Promotoría. Te responderemos aquí.» | «Elige qué pasó, el pago si lo tienes y cuéntanos. Le llega solo a Promotoría y Dirección, y te responden aquí.» |
| 3 | *Mis datos*: tus datos y los de tus hijos, contactos verificados, para qué se usa cada dato, a quién se envía y cuánto se guarda → imprimir | «En Mis datos ves qué datos tuyos guarda el colegio, para qué y a quién se envían. Lo puedes imprimir.» |
| 4 | *¿Algo no cuadra?* → «Mis datos personales» → Rectificación, texto «El segundo apellido de mi hija está mal escrito» → enviar | «Para corregir un dato, pídelo aquí. El colegio te responde dentro del plazo de ley.» |

**Cierre:** «Tus pagos y boletas se conservan por ley tributaria, aunque pidas eliminar tus datos: te explicamos por escrito qué se guarda y por qué. ¿Dudas? Escríbenos desde el portal.»
*(Nota de edición: si en la fecha de grabación el asesor legal ya confirmó los plazos, se puede decir «en 10 días hábiles»; si no, se deja «dentro del plazo de ley».)*

## Video 9 · Si algo no cuadra en el colegio (todo el personal, 60 s)
**Objetivo:** que todo el personal sepa que nada se borra, dónde queda el registro y a quién avisar sin tocar nada.
**Usuario:** `promotor` (bitácora) y una página de error de ejemplo.

| # | En pantalla | Voz en off |
|---|---|---|
| 1 | Portada: «Si algo no cuadra en el colegio» | «En Cuentas Claras nada se borra. Todo queda registrado, también lo que se corrige.» |
| 2 | *Bitácora de auditoría*: lista de eventos con quién, qué y cuándo | «Cada pago, anulación, aprobación y descarga queda en la bitácora con quién la hizo y cuándo.» |
| 3 | **Verificar integridad** → resultado íntegro y la huella | «Promotoría puede comprobar que nadie alteró la bitácora.» |
| 4 | *Pagos de hoy* con un pago anulado y su nota de crédito | «Un error se corrige con una anulación aprobada por otra persona. El pago original sigue a la vista.» |
| 5 | Página de error con «Código de error: …» | «Si ves una página de error, anota el código y avisa.» |

**Cierre:** «Si algo no cuadra, avisa a Promotoría o Dirección y no toques nada antes: ni el efectivo, ni el archivo, ni la computadora. Nadie puede “arreglarlo por debajo”, y eso te protege a ti también.»

---

## Publicación (completar)
| # | Video | Rol | Requisito del acta | Enlace sin listar | Publicado el | Responsable | QR en el manual |
|---|---|---|---|---|---|---|---|
| 1 | Cobrar y entregar la boleta | Caja | **Sí** | | | | ☐ |
| 2 | Cerrar la caja a ciegas | Caja | **Sí** | | | | ☐ |
| 3 | El extracto de cada mañana | Administración | No (2 semanas) | | | | ☐ |
| 4 | Aprobar desde el celular | Dirección y Promotoría | **Sí** | | | | ☐ |
| 5 | El panel y el resumen de las 19:30 | Promotoría | No (2 semanas) | | | | ☐ |
| 6 | La llamada de control | Promotoría | No (2 semanas) | | | | ☐ |
| 7 | Tu portal en el celular | Familias | **Sí** | | | | ☐ |
| 8 | ¿Algo no cuadra? y Mis datos | Familias | No (2 semanas) | | | | ☐ |
| 9 | Si algo no cuadra en el colegio | Todo el personal | No (2 semanas) | | | | ☐ |

**Métrica de éxito de los videos:** en la capacitación, cada persona completa la tarea de su video sin ayuda después de verlo una vez (ver `docs/entrega/capacitacion.md`). Si más de 1 de cada 5 necesita ayuda en el mismo paso, se regraba esa escena.
