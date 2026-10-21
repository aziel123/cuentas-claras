# Guía de la plataforma Cuotaria · para presentarla al Colegio Virgen María

> Para: el dueño del producto, antes de la reunión con el coordinador del colegio.
> Versión que se presenta: el rediseño visual (rama `claude/rediseno-interfaz`).
> Escrita el 21 de octubre de 2026 a partir del código, las plantillas, los diseños de `docs/arquitectura/`, el plan y las capturas de `docs/ux/rediseno-aplicado/`.

**Cómo leer esta guía.** Todo lo que dice «la plataforma hace X» se comprobó en el código. Lo marcado con **[verificar]** existe a medias, depende de un trámite externo o no se pudo confirmar: antes de prometerlo, revísalo. Lo marcado con **[próximamente]** todavía no está en la versión que vas a mostrar.

**Regla de oro para la reunión:** si no sabes algo, di «lo verifico y te respondo por escrito mañana». Es mejor que inventar. El colegio perdió S/ 70,000 por confiar en lo que alguien le decía: valorará que seas preciso.

---

## 0. Antes de la reunión (lista rápida)

- [ ] Levanta la aplicación en tu computadora con la marca Cuotaria (en `.claude/launch.json` está la configuración «cuotaria-dev», puerto 8095).
- [ ] Ten a mano la clave de los usuarios de demostración. **No va en esta guía:** es la variable `CC_DEMO_CLAVE` de tu configuración local.
- [ ] **Abre cada usuario en un navegador distinto o en una ventana de incógnito.** Un mismo navegador guarda una sola sesión: si entras como `caja` en la pestaña donde estaba `promotor`, pierdes la sesión de promotor. Sugerencia: Chrome normal = `promotor`, Chrome incógnito = `caja`, Edge = `director`, tu celular = `apoderado`.
- [ ] **No inicies sesión dos veces con el mismo usuario a la vez.** Hubo un error grave (P0) con el doble ingreso en la base de pruebas; se corrigió en el commit `7fa77ae` de la rama del sprint 9. **[verificar]** que la rama del rediseño ya lo tenga antes de presentar; si no, evita el doble ingreso.
- [ ] Ensaya el guion de la sección 4 una vez completa. **Los datos de demostración se crean al arrancar y se pierden al apagar:** si ensayas el faltante, reinicia la aplicación antes de la reunión.
- [ ] Anota en un papel el monto de la pensión de Mateo Quispe Huamán que veas en el ensayo (lo vas a necesitar para el faltante en vivo).

---

## 1. Cuotaria en 1 minuto

**El problema.** En un colegio privado peruano, una sola auxiliar cobraba las pensiones: recibía el efectivo, lo anotaba (o no) en un cuaderno y lo guardaba sin que nadie más lo viera. Así se perdieron más de S/ 70,000 y nadie lo notó a tiempo.

**Qué es Cuotaria.** Una plataforma web de gestión escolar cuyo primer módulo es la **cobranza con controles antifraude**. Su promesa: **que una pérdida así no pueda volver a pasar sin que se note el mismo día.** Después vienen la asistencia, los comunicados, las notas y la exportación al SIAGIE.

**Para quién es.**
| Persona | Qué gana |
|---|---|
| Promotoría (dueño) | Ve desde el celular cuánto entró hoy, quién debe y qué alertas hay, sin pedir reportes a nadie. |
| Dirección | Aprueba anulaciones, descuentos y cierres de caja con un clic, y todo queda registrado con su nombre. |
| Administración | No digita: la deuda la genera el sistema, el banco se concilia subiendo un archivo. |
| Caja | Cobra en segundos sin escribir montos, entrega la boleta al momento y su conteo a ciegas la protege. |
| Familias | Ven todo lo que deben, pagan desde el celular y reciben un aviso por cada pago. |

**Qué la diferencia de un Excel.**
- Un Excel se borra, se edita y no dice quién cambió qué. En Cuotaria **los pagos no se borran ni se editan**: se anulan con motivo y con la aprobación de otra persona, y todo queda en una bitácora que nadie puede alterar sin que se detecte.
- En un Excel la cajera escribe el monto. En Cuotaria **la cajera nunca escribe montos**: marca las cuotas que generó el sistema.
- Un Excel no avisa. Cuotaria **avisa a la familia en cada pago** y **avisa a Promotoría el mismo día** si falta plata en caja.

**Qué la diferencia de otros sistemas escolares.** La mayoría registra pagos. Cuotaria está diseñada alrededor del fraude interno:
- **quien cobra no aprueba** (lo exige el programa y, en la instalación real, también la base de datos);
- **cierre de caja a ciegas**: la cajera cuenta sin ver cuánto debería tener;
- **conciliación con el banco** que muestra en rojo un Yape «inventado»;
- **bitácora inmutable** con una «huella» diaria que el dueño puede guardar fuera del sistema;
- **llamada de control semanal** a familias elegidas al azar que pagaron en efectivo.

**En una frase para el coordinador:** «Cuotaria no confía en nadie en particular, ni siquiera en mí: cada sol tiene un responsable, un comprobante, un aviso a la familia y una segunda persona que lo revisa.»

---

## 2. Los roles

### 2.1 La historia de la segregación de funciones (cuéntala así)

> Imagina el colegio de antes. La señora de caja recibía S/ 380 de una mamá, anotaba S/ 330 en el cuaderno y nadie lo notaba, porque **la misma persona cobraba, anotaba, guardaba el dinero y cuadraba la caja.** Si se equivocaba (o no), ella misma «corregía».
>
> En Cuotaria esas tareas están repartidas entre personas distintas, para que nadie pueda tapar un error (o un robo) sola:
> 1. **El sistema** decide cuánto se debe (a partir del plan de pensiones que aprobó Dirección o Promotoría).
> 2. **Caja** solo cobra lo que el sistema dice y entrega la boleta.
> 3. **La familia** recibe un aviso con el monto: si no le llega o el monto no es el que pagó, lo nota ese mismo día.
> 4. **Caja** cuenta el efectivo **sin ver** cuánto debería tener.
> 5. **Promotoría o Dirección** aprueba ese cierre; si falta plata, le llega una alerta crítica.
> 6. **Administración** cruza lo registrado con lo que realmente llegó al banco.
>
> Para robar, ahora tendrían que ponerse de acuerdo varias personas, y aun así queda rastro en la bitácora.

### 2.2 Cada rol: quién es, qué ve, qué NO puede hacer

Los usuarios de demostración están en `seguridad/inicial/DatosDemoDev.java` (el de la familia, en `alumnos/inicial/DatosDemoApoderadoDev.java`). La clave es la de tu configuración local.

| Rol | Usuario demo | Nombre en la demo | Qué ve en su menú | Qué NO puede hacer (y por qué) |
|---|---|---|---|---|
| **Promotoría** (dueño) | `promotor` | María Elena Torres | Inicio con «Para revisar» y gráficos; Aprobaciones; Panel del colegio; Pagos en línea; Conciliación; Recaudación; alumnos y pensiones; Reportes; Llamadas de control; Bitácora; «Quién vio datos personales»; Usuarios; Marca del colegio; Conectores; Puesta en marcha | **No cobra** (Caja no se combina con Promotoría). **No aprueba lo que ella misma pidió.** Su rol y el de Dirección solo se asignan con una solicitud aprobada por otra persona. |
| **Dirección** | `director` | Jorge Salazar | Inicio con «Para revisar»; Aprobaciones y Cajas del día; Bitácora; alumnos, pensiones, conciliación; Docentes y secciones; Académico | **No cobra.** No aprueba lo que pidió. Puede crear usuarios del personal, pero **no** convertir a alguien en Promotoría ni en otra Dirección. No ve «Quién vio datos personales» (solo Promotoría). |
| **Administración** (contador) | `administracion` | Rosa Medina | Conciliación (subir extracto), Pagos en línea, Recaudación, Alumnos y apoderados, Importar Excel, Pensiones (proponer planes), Saldo inicial, Descuentos y becas (pedirlos), Reportes | **No cobra. No aprueba** sus propios descuentos, planes de pensión ni el extracto que subió. No ve Aprobaciones ni la bitácora. |
| **Caja** | `caja` y `caja2` | Lucía Ramos y Pedro Huanca | Caja (Cobrar, Pagos de hoy, Cerrar mi caja), Feriados, Mi celular y correo | **No borra ni anula pagos**: solo **pide** la anulación o la corrección. **No decide montos. No da descuentos. No aprueba su propio cierre.** No ve el monto esperado al cerrar. Mientras su caja está abierta, en «Pagos de hoy» los montos en efectivo dicen «Se ve al cerrar». No puede cobrar si su caja de un día anterior sigue abierta. |
| **Docente** | `docente` | Carlos Quispe | Académico: sus secciones y los nombres de sus alumnos | **Nunca ve deudas, pagos, documentos ni contactos** (Ley 29733 e INDECOPI: lo académico no depende del pago). Solo ve sus secciones. |
| **Familia (apoderado)** | `apoderado` | Rosa Huamán Ccori (familia Quispe Huamán: Mateo y Valeria) | Mi familia: total por pagar, cuotas, pagar en línea, código para pagar en el banco, boletas, mensajes del colegio, «¿Algo no cuadra?», Mis datos | Solo ve a **su** familia. No puede cambiar montos: «El total lo calcula el colegio con lo que debes: no se puede cambiar». |
| **Operador de la plataforma** (tú, como proveedor) | `operador` | Operador de la plataforma | Colegios de la plataforma: dar de alta un colegio, su ficha y su correo de control | **No ve ningún dato de las familias** ni entra a los módulos del colegio. No se queda con la primera Promotoría. |
| **Otro colegio** | `promotor.b` y `caja.b` | Colegio de Prueba B | Lo mismo que su rol, pero de su colegio | No ve nada del Virgen María (sirve para demostrar el aislamiento). |

Combinaciones prohibidas por diseño: Caja no se combina con Dirección, Promotoría ni Administración; Dirección y Administración tampoco van juntas.

Cada pantalla de inicio tiene una tarjeta **«Cómo te cuida el sistema»** que explica estos límites en lenguaje simple. Úsala en la demo.

---

## 3. Cada flujo, paso a paso

Formato de cada flujo: **usuario demo → pantallas y botones reales → qué control antifraude hay y qué fraude evita.**

### 3.1 Cobrar en caja y emitir el comprobante

**Usuario:** `caja` (Lucía Ramos).

1. **Ingresa a** la página «Ingresa a Cuotaria»: campos *Usuario* y *Clave*, botón **Ingresar**.
2. En **Inicio** (Caja) aparece la tarjeta **Cobrar** con el buscador *«Buscar alumno o apoderado. Por nombre o DNI»*. Escribe «quispe» y pulsa **Buscar**.
3. Elige la familia. Se abre **«Cobrar a la familia»** (familia Quispe Huamán):
   - sección **Cuotas por pagar**, con cada alumno y sus cuotas (vencimiento, estado y saldo);
   - marca la pensión que paga el apoderado;
   - sección **¿Cómo paga?**: elige el medio (Efectivo, Yape, Plin, transferencia…);
   - pulsa **Revisar cobro**.
4. Pantalla **«Revisa el cobro»**:
   - el total grande, con el detalle por cuota. Debajo dice: *«Caja no decide montos: la deuda la generó el sistema. Al registrar, se emite el comprobante.»*;
   - si es efectivo, escribe **Recibido (S/)**; si es digital, el **N.° de operación (obligatorio)**;
   - **Comprobante**: *Boleta a nombre de* (elige el apoderado); *Factura (con RUC)* solo si la familia tiene un RUC aprobado;
   - pulsa **Cobrar S/ …** (el botón muestra el monto).
5. Pantalla **«Pago registrado · B001-…»** con el total, lo recibido y **«Entrega S/ … de vuelto»**. Botones **Imprimir boleta** y **Nuevo cobro**. Aviso: *«el sistema le envía el aviso de este pago a su celular o correo registrado»*.

Detalles que te pueden preguntar:
- Si la familia tiene un **pago en línea en curso**, sale el aviso *«Pregunta al apoderado si ya pagó en su celular antes de cobrar»* (evita cobrar dos veces).
- Si la caja de hoy ya se cerró: *«solo se aceptan pagos digitales hasta mañana»*.
- Dos cajeras a la vez **no pueden cobrar la misma cuota dos veces** (por eso existe `caja2`).
- El **pago a cuenta** (pagar solo una parte) existe, pero está **desactivado en el piloto** (decisión 16); si se activa, queda resaltado para Promotoría.

**Control antifraude y qué evita:**
| Control | Fraude que evita |
|---|---|
| La deuda la genera el sistema; caja solo marca cuotas | Cobrar S/ 380 y registrar S/ 330, o inventar un «descuento» |
| Boleta con numeración correlativa sin huecos, al momento | Cobrar sin dejar constancia |
| Aviso automático a la familia por cada pago | Que un pago no registrado pase desapercibido: la familia es auditora |
| N.° de operación obligatorio en pagos digitales (luego se cruza con el banco) | Registrar un Yape que nunca llegó para tapar efectivo que se llevó |

**Sobre el comprobante, sé honesto:** en la demo y en el piloto la boleta es **simulada** (dice *«Comprobante simulado: aún sin envío a SUNAT»*). Ver la pregunta difícil 5.3.

### 3.2 Cierre de caja ciego y faltante

**Usuario:** `caja` (o `caja2`) para cerrar; `promotor` o `director` para ver y aprobar.

1. En Caja pulsa **Cerrar mi caja**. Pantalla **«Cerrar mi caja»**: *«Cuenta el efectivo de tu caja del dd/mm»*.
2. Recuadro azul: *«¿Por qué no ves cuánto deberías tener? Es un cierre a ciegas: si vieras el monto registrado, cualquiera podría acomodar su conteo para que cuadre…»*.
3. Escribe **Total contado (S/)** o despliega **Contar por billetes y monedas (opcional)** y el sistema suma. Pulsa **Registrar conteo**.
4. **Si cuadra:** *«Tu caja cuadró. Promotoría o Dirección aprobarán el cierre.»*
5. **Si no cuadra:** aparece **«Vuelve a contar»** con *«No coincide con lo registrado… Este reconteo cierra la caja: explica qué pasó.»* Escribe el nuevo total y **¿Qué pasó? (obligatorio)**, y pulsa **Registrar reconteo y cerrar**.
6. Resultado: *Registrado (esperado)*, *Contado al reconteo*, *Diferencia* y *Tu primer conteo fue S/ …*. Mensaje: *«Hubo una diferencia: se avisó a Promotoría y Dirección, que revisarán tu cierre contigo.»*
7. En el **Inicio de Promotoría** aparece en rojo, en **Para revisar**: *«Caja · Crítica — Faltante de S/ 50.00 en la caja de Pedro Huanca del …(esperado S/ 380.00, contado S/ 330.00). Explicación: …»* con el botón **Revisar**.
8. Si después del cierre se necesita reabrir: botón **Pedir reabrir mi caja** (solo el mismo día y antes de depositar, con aprobación). Ese segundo cierre queda marcado como *«Tras reapertura: no fue ciego»*.

**En la demo ya hay un faltante:** ayer `caja2` (Pedro Huanca) cerró con **S/ 50 menos** y su explicación: *«Volví a contar dos veces y faltan S/ 50; no sé en qué momento salieron del cajón.»* Está por aprobar.

**Control antifraude y qué evita:**
| Control | Fraude que evita |
|---|---|
| Conteo a ciegas, un solo reconteo | Que la cajera vea el esperado y «acomode» el conteo |
| Los montos en efectivo de hoy ocultos en «Pagos de hoy» hasta cerrar | Sumar lo registrado para saber cuánto «debería» haber |
| Explicación obligatoria si hay diferencia | Faltantes sin responsable |
| Alerta crítica a Promotoría el mismo día | Descubrir el faltante a fin de mes o nunca |
| No se cobra con la caja de un día anterior abierta | Arrastrar faltantes de un día al siguiente |
| Alerta si la caja no se cierra a la hora límite (por defecto 19:00) | «Me olvidé de cerrar» |

### 3.3 Aprobación por Promotoría o Dirección

**Usuario:** `director` (o `promotor`).

1. Inicio de Dirección: tarjeta **Para revisar** con *«N solicitudes esperan tu aprobación»* y el botón **Ir a aprobaciones**; también **Ver cajas del día**.
2. Pantalla **«Solicitudes por aprobar»**. Explica qué se aprueba aquí: *cierres y reaperturas de caja, anulaciones y correcciones de pagos, descuentos y becas, retiros, celulares y correos de apoderados, responsables de pago, ingresos tardíos y anulaciones de cuotas.* Y la regla: *«Quien los pide (o cobró el pago, o es su caja) no los aprueba.»*
3. Cada solicitud muestra el tipo, el resumen, el motivo y quién la pidió. Botones:
   - **Aprobar** (casos simples);
   - si es un **cierre con diferencia** o tras reabrir: **Revisar el detalle** (botón principal) y **Aprobar con nota**, que exige un *Comentario (obligatorio)*: qué verificaste con la cajera;
   - **Rechazar** (o *Devolver*), con *Motivo (obligatorio)*.
4. Si eres quien pidió o participaste: *«La pediste tú, cobraste ese pago, es tu caja o preparaste esa cuenta: debe resolverla otra persona.»* No aparecen los botones.
5. **Cajas del día** (**Día anterior** / **Día siguiente**) → detalle de cada caja: fondo fijo, efectivo y digital vigentes, cierres (esperado, primer conteo, contado, diferencia, explicación), depósito y pagos.
6. En el celular hay una versión corta: **«Para aprobar: N»**, con **Aprobar** → **Sí, aprobar**.

**Control antifraude:** separación de funciones a nivel de cada solicitud. Cada aprobación queda con la «firma» de la sesión de quien aprobó y no se puede hacer a nombre de otro. **Evita** que alguien se autoapruebe una anulación, un descuento o su propio cierre.

### 3.4 Anulación de un pago con motivo y aprobación

**Usuarios:** `caja` para pedir; `director` o `promotor` para aprobar.

1. Caja → **Pagos de hoy**. Aviso fijo: *«Caja no puede borrar ni anular pagos: solo pedirlo…»*.
2. En la fila del pago: **Solicitar anulación**. Se abre *«¿Pedir la anulación de B001-…?»* con:
   - **Causa (obligatoria)**: Pago duplicado / Se cobró por error / Retiro del alumno / Otra causa;
   - **Motivo (obligatorio)**, mínimo 10 caracteres;
   - botón **Pedir anulación**. El pago **sigue vigente** hasta que se apruebe.
3. Alternativa: **Corregir** (el mismo dinero pasa a otras cuotas, de la misma familia o de otra). Pantalla *«Corregir el pago …»*, botón **Pedir corrección**. Si pasa a otra familia, quien apruebe lo ve resaltado.
4. Dirección o Promotoría lo aprueba en **Aprobaciones**. Si sale dinero ya cobrado (devolución), el sistema pide **«Antes de aprobar, llama al apoderado»**: el *Número al que llamaste* y la casilla **Hablé con el apoderado**.
5. Al aprobarse se emite una **nota de crédito**, las cuotas **vuelven a deberse** y la familia recibe un aviso. En «Pagos de hoy» el pago queda tachado con *«Nota de crédito …»*.

**En la demo ya hay una:** `caja` cobró en efectivo a Thiago Rojas Salazar y pidió la devolución con el motivo *«El apoderado ya había pagado esta pensión por transferencia»*.

**Control antifraude:** el pago nunca se borra; queda el original, la nota de crédito, el motivo, quién pidió y quién aprobó. **Evita** el fraude clásico: cobrar, entregar boleta y luego «anular» para quedarse con el efectivo. Con la llamada obligatoria, la familia confirma que de verdad se le devolvió.

### 3.5 Cuotas, moras y descuentos

**Cuotas (planes de pensión).** Usuarios: `administracion` propone; `promotor` o `director` aprueba.
1. Cobranza → **Pensiones**: *«Un plan por nivel. Lo propone Administración y lo aprueba otra persona de Promotoría o Dirección.»*
2. Administración crea el plan (pensión mensual, matrícula, vencimientos; por defecto 10 pensiones con vencimiento el último día de marzo a diciembre) y pulsa **Enviar para aprobación**. Queda bloqueado.
3. Otra persona lo abre y pulsa **Aprobar plan** o **Devolver plan** (con motivo). Al aprobarse se **generan solos los cronogramas** de los alumnos de ese nivel. Lo pendiente se ve en **Cronogramas pendientes** → **Generar pendientes**.
4. Cambiar montos: **Crear versión nueva**. La versión vigente sigue hasta que otra persona apruebe la nueva y **las cuotas ya generadas no cambian**.
5. **Anular una cuota** también pasa por Aprobaciones.

**Moras: NO existen hoy en la plataforma.** No hay cálculo de intereses moratorios en el código. Los vencimientos en domingo o feriado se mantienen y no generan mora. Las moras tienen límites legales (INDECOPI): si el colegio las quiere, primero las valida un asesor legal y luego se diseñan. **[verificar con el colegio si las necesita]**

**Descuentos y becas.** Usuarios: `administracion` pide; `promotor` o `director` aprueba.
1. Cobranza → **Descuentos y becas** → buscar al alumno → *«Descuento para …»*.
2. **Tipo** (por ejemplo, Descuento por hermanos), **¿Cómo se descuenta?** (Porcentaje o soles por cuota), **Valor** (100 % = beca completa), las **Cuotas** a las que se aplica, **Motivo (obligatorio)** y **Sustento (obligatorio)** (ej.: *«Reglamento de pensiones, art. 12»*).
3. **Revisar descuento** → **Enviar a aprobación** → otra persona aprueba en Aprobaciones. La familia recibe un aviso.
4. En la demo: descuento por hermanos del 10 % para Valeria Quispe Huamán, pedido por `administracion` y aprobado por `promotor`.

**Control antifraude:** nadie fija montos a mano; los planes y los descuentos tienen dos personas y quedan en la bitácora con su sustento. **Evita** descuentos «de favor» o rebajas inventadas para justificar un cobro menor. Lo redondeado siempre favorece a la familia.

### 3.6 Pago en línea y portal de familias

**Usuario:** `apoderado` (Rosa Huamán Ccori). Muéstralo **en tu celular**: está pensado para eso.

1. **Mi familia**: *«Hola, Rosa»*, con **Tu resumen** (deuda vencida, próximo vencimiento) y **Total por pagar** en grande, junto al botón **Cómo pagar**.
2. **Lo que debes**: las cuotas de cada hijo. Marca las cuotas → **Pagar en línea**. Texto: *«…pagar con Yape, Plin o tarjeta. Hasta S/ … por pago.»*
3. **«Revisa tu pago»**: el total *«lo calcula el colegio… no se puede cambiar. Pagas el monto exacto, sin recargo.»* Boleta a su nombre (o factura si tiene RUC aprobado). Botón **Ir a pagar**.
4. En la demo se abre la **pasarela simulada**, con la franja *«PAGO SIMULADO · NO ES DINERO REAL»* y los botones **Pagar con Yape (simulado)**, **Pagar con tarjeta (simulado)**, **Rechazar el pago** y **Pagar S/ 1.00 menos (prueba de monto distinto)**.
5. **«Tu pago en línea»** muestra el estado. El pago **se registra solo** cuando el sistema confirma con la pasarela; nadie lo digita. Sale la boleta y el aviso.
6. Otras secciones del portal:
   - **Pagar en el banco**, con el *código de pago* de cada hijo;
   - **Tus comprobantes** / **Mis boletas** (descargables);
   - **Mensajes del colegio**;
   - **¿Algo no cuadra?** → **Avisar a Promotoría** (le llega a Promotoría y Dirección en «Avisos de las familias»);
   - **Recordatorios de vencimiento** (3 días antes y el día hábil siguiente), que la familia puede **Apagar**; *«Los avisos de tus pagos, anulaciones y descuentos siempre llegan»*;
   - **Mis datos** y **Aviso de privacidad**;
   - **Matrícula 2027**: la familia confirma si su hijo continúa (**Responder**); al pagar la matrícula se generan sus pensiones.

**Control antifraude:** el dinero entra sin pasar por manos del personal; el pago solo cuenta cuando lo confirma la pasarela (no basta una captura de pantalla); la familia ve su estado de cuenta completo, así que cualquier deuda falsa o pago faltante sale a la luz.

**Honestidad:** **hoy solo existe la pasarela simulada.** Los proveedores reales (Culqi, Niubiz, Izipay) se pueden registrar como conectores, pero su adaptador se construye cuando el colegio tenga su cuenta. **[verificar]** antes de prometer una fecha.

### 3.7 Conciliación bancaria y extractos

**Usuarios:** `administracion` sube; `promotor` o `director` confirma a ciegas.

1. Dinero de hoy → **Conciliación bancaria** → pantalla **«Diferencias con el banco»**: *«El sistema ya emparejó solo lo que cuadra. Aquí ves solo lo que falta revisar.»* Botones **Extractos**, **Cierres del mes** y **Verificación manual (excepciones)**.
2. **Extractos** → **«Subir el extracto del banco»** en tres pasos (*Subir · Revisar · Registrar*). Archivo .csv, .txt o .xlsx del día anterior, sin abrirlo ni cambiarlo. Botón **Subir y revisar** → *«Revisa antes de registrar»*.
3. Cada extracto **continúa al anterior** (empieza con su saldo final). Si un día ya cargado vuelve distinto, se rechaza: «el banco no cambia el pasado».
4. Otra persona (Promotoría o Dirección) pulsa **Confirmar a ciegas** → *«Confirma el extracto mirando tu app del banco»*:
   - *1. ¿Ves estos movimientos en tu app?* (una muestra al azar, **sin montos**);
   - *2. Escribe el saldo que muestra tu app del banco al cierre del…* (no se muestra a propósito; tiene intentos limitados);
   - botón **Confirmar y conciliar**.
   Quien subió el extracto no lo puede confirmar.
5. El sistema empareja solo los Yape, depósitos, pagos en línea y recaudación. Resumen: *movimientos, emparejados (%), sugeridos, sin pareja*.
6. En rojo: **«Debía estar en el banco y no está»**, *«Un Yape o una transferencia que no está en el banco puede ser un pago inventado para tapar efectivo: revísalo hoy con quien lo registró.»*
7. Los movimientos ajenos (intereses, comisiones, ITF) se explican con una categoría y una nota.

**En la demo:** ayer `caja` registró un **Yape inventado** (operación `YP700112233`) que no está en el banco. Para verlo en rojo: entra como `administracion`, en Extractos descarga *«el extracto de ejemplo de ayer»*, súbelo; luego como `promotor` confírmalo escribiendo el saldo final que muestra el log al arrancar la aplicación. Es un flujo largo: **ensáyalo o muéstralo solo si preguntan.**

**Control antifraude:** doble persona (sube una, confirma otra a ciegas) y el banco como fuente independiente. **Evita** inventar pagos digitales para tapar efectivo y alterar el extracto.

**Recaudación bancaria** (pagos en ventanilla o app del banco con el código del alumno): Dinero de hoy → **Recaudación bancaria** → **Subir el archivo del banco** → **Subir y revisar** → otra persona **Confirmar a ciegas** (escribe el total que ve en el banco) → **Confirmar y aplicar los pagos**. Lo que no cuadra queda en **Pagos por revisar**. También permite **Exportar base de deudas** para el banco. **[verificar]** Los formatos de cada banco aún no se conocen: hoy usa un formato genérico.

### 3.8 Depósito del efectivo

**Usuario:** `caja`.

1. Después de cerrar la caja, en la misma pantalla **Cerrar mi caja** aparece **«Registrar depósito»**: *«Deposita en el banco lo contado menos el fondo fijo… y registra el número de operación del voucher.»*
2. Campos: **Cuenta del colegio**, **N.° de operación del voucher**, **Fecha del depósito**, **Monto depositado (S/)** (sugiere lo esperado) y **Explicación (solo si depositas otro monto)**. Botón **Registrar depósito**.
3. Promotoría y Dirección lo ven en el detalle de la caja (*Depósito* y *Verificación bancaria*). El depósito se empareja con el extracto en la conciliación.
4. Alertas: *«El efectivo de la caja de … aún no se deposita»* y **Depósito tardío**: *«…llegó al banco más de un día hábil después de la caja. Posible uso del efectivo de un día para cubrir otro.»*

**Control antifraude:** lo cerrado, lo depositado y lo que realmente llegó al banco se cruzan. **Evita** el «jineteo» (usar el efectivo de hoy y reponerlo con el de mañana) y los depósitos menores a lo cobrado.

### 3.9 Auditoría inmutable

**Usuario:** `promotor` (o `director`).

1. Control y auditoría → **Bitácora de auditoría**: *«Todo lo que se hace en el sistema queda aquí: quién, qué, cuándo y desde dónde. Nadie puede borrarlo ni cambiarlo.»*
2. Filtros: *Desde*, *Hasta*, *Usuario*, *Acción* y **Solo «Revisar»** (lo sensible, como claves restablecidas o cambios de roles). Botón **Filtrar**.
3. **Verificar integridad**: *«Comprueba que nadie editó, borró ni agregó eventos directamente en la base de datos.»* Opcionalmente escribe la última **huella** anotada (*Evento* y *Código*) y pulsa **Verificar integridad**.
   - Si está bien: *«La bitácora está íntegra: se revisaron N eventos de tu colegio y ninguno fue alterado.»*
   - Si no: *«La bitácora fue ALTERADA…»*.
4. **Anota esta huella**: número de evento, fecha y código, para guardarla **fuera del sistema** (cuaderno o celular). Además, la huella llega a Promotoría **todos los días** por WhatsApp o correo y al **correo externo de control** (el contador o un tercero de confianza).
5. Solo Promotoría: **«Quién vio datos personales»** (quién abrió fichas, buscó alumnos o vio morosos) y **Datos con plazo vencido**.

**Control antifraude:** cada evento está sellado y encadenado con el anterior. En la instalación real, la base de datos **solo permite agregar** registros en la bitácora y en el libro de pagos (no permite borrar ni editar), y la aplicación **no arranca** si alguien quitó esas protecciones. **Evita** que alguien con acceso técnico borre su rastro sin que se note.

**Aclaración honesta:** la demo usa una base en memoria **sin** esas protecciones de la base de datos; muestra solo las reglas de la aplicación. Las protecciones de la base se prueban en cada cambio contra MySQL real.

### 3.10 Alta de familias y alumnos

**Usuario:** `administracion`.

1. Familias y alumnos → **Alumnos y apoderados** → **«Alumnos»**: botón **Registrar alumno** (formulario con alumno, apoderados, grado y sección).
2. Desde una familia: **Registrar hermano** y **Agregar apoderado**. Desde un apoderado: **Guardar corrección**, **Dar acceso en línea** (le llega al apoderado un enlace de un solo uso por WhatsApp o correo, que vence en 48 horas; nadie ve su clave) y **Restablecer acceso** (solo Promotoría).
3. **Importar desde Excel** (3 pasos): **«Importar alumnos desde Excel»** → *1. Descarga la plantilla* (**Descargar plantilla**) → *2. Sube el archivo lleno* (**Subir y revisar**) → **«Revisa antes de importar»** fila por fila → **«Importación terminada»**. Es «todo o nada»: si una fila tiene error, no se guarda nada a medias. Hay archivos de ejemplo en `docs/ux/` (`ejemplo-importacion.xlsx` trae 2 errores a propósito).
4. Un **celular o correo nuevo** de un apoderado queda pendiente hasta que **su dueño lo confirma** con un enlace y su DNI. Cambiar el contacto o el responsable de pago pasa por Aprobaciones.
5. **Retiros** e **ingresos tardíos** (matrícula después del inicio de clases, que recorta pensiones) también se aprueban por otra persona.

**Control antifraude:** el contacto de la familia es el canal del aviso de pago; si alguien pudiera cambiarlo libremente, desviaría los avisos a su propio celular. Por eso se verifica y se aprueba.

### 3.11 Puesta en marcha de un colegio nuevo

**Usuarios:** `operador` para el alta; luego la **Promotoría** del colegio nuevo.

1. **Operador** → **«Colegios de la instalación»** → **«Dar de alta un colegio»**: nombre comercial, razón social, RUC, dirección fiscal, correo, niveles, logo, **código del contrato**, año escolar inicial y **titular del contrato** (primera Promotoría: nombre, DNI, usuario, celular con WhatsApp y correo). Botón **Dar de alta el colegio**.
2. El colegio **nace «en preparación»**: no cobra ni envía recordatorios. Al titular le llega un enlace para **Activa tu cuenta**, donde confirma su DNI.
3. Promotoría, Dirección o Administración ven **Configuración → Puesta en marcha**: una **lista de verificación de 14 pasos** que el sistema calcula solo:
   - datos y logo del colegio;
   - equipo con segregación;
   - año escolar y secciones;
   - pensiones;
   - alumnos;
   - saldo inicial;
   - comprobantes (OSE);
   - cuenta bancaria;
   - WhatsApp y correo;
   - correo externo de control;
   - aviso de privacidad del colegio;
   - respaldo;
   - pago en línea (opcional);
   - recaudación bancaria (opcional).
4. Dos pasos se cumplen con la firma de Promotoría: **«Firmar: el colegio empieza sin deudas anteriores»** y **«Firmar: uso el comprobante simulado y emito el legal aparte»**.
5. Con la lista completa: **Pasar el colegio a producción**. *«No tiene vuelta atrás»*, y avisa a toda la Promotoría y al correo externo de control.
6. **Conectores** (OSE, pasarela, cuenta bancaria, recaudación): **Proponer el cambio** → lo aprueba **otra persona** → **Probar conexión**. Las claves secretas se guardan cifradas y nadie las vuelve a ver.
7. **Saldo inicial** (deudas anteriores): Pensiones → **Saldo inicial** → **Crear lote** → **Agregar deuda** → **Enviar para confirmación** → otra persona **Confirmar lote** escribiendo a ciegas el total del informe del contador.

**Control antifraude:** el operador (tú) **no** puede ver familias ni quedarse con la Promotoría; cambiar adónde entra el dinero (cuenta, pasarela) exige dos personas y avisa a todos; el saldo inicial no se copia del cuaderno, se valida con el contador.

### 3.12 Multi-colegio

**Usuarios:** `promotor.b` y `caja.b` (Colegio de Prueba B).

- Una sola instalación puede atender a varios colegios. Cada colegio ve **solo sus datos**: el programa filtra todo por colegio y la base de datos impide mezclar datos entre colegios.
- Prueba en vivo: entra como `caja.b` y busca «quispe» → no encuentra a las familias del Virgen María. En el Colegio B hay un alumno con el **mismo DNI** que uno del Virgen María, y son personas distintas para el sistema.
- Cada colegio tiene su propia marca (razón social, RUC, dirección y logo) en su portal, sus boletas y sus mensajes.
- **Para el Virgen María se propuso una instalación dedicada** (solo para él), y la compartida para los colegios nuevos (decisiones 108 y 109). **[verificar]** que siga siendo así antes de decirlo.

### 3.13 Qué viene **[próximamente]**

Dilo claramente: **nada de esto está en la versión que vas a mostrar**, salvo lo indicado.

| Módulo | Estado real | Fecha objetivo del plan |
|---|---|---|
| **Docentes y secciones** | **Ya existe**: Dirección o Administración proponen quién es tutor, docente o auxiliar de cada sección y otra persona lo aprueba (**Docentes y secciones**). El docente ve sus secciones y los nombres de sus alumnos, nunca deudas | Sprint 9, tanda 1 (hecho) |
| **Asistencia diaria** con aviso a los padres | **[próximamente]**: en desarrollo en otra rama, no está en el rediseño. Lo toma el docente desde el celular, en menos de 1 minuto por sección; aviso al apoderado el mismo día | Listo para el inicio de clases de 2027 (meta del 4 dic 2026) |
| **Comunicados** con confirmación de lectura | **[próximamente]**: en el menú aparece con la etiqueta «Próximamente» | Igual que la asistencia |
| **Nómina de matrícula para el SIAGIE** | **[próximamente]**: depende de confirmar el formato oficial | Sprint 9 |
| **Notas por competencias, libretas en PDF y exportación al SIAGIE** | **[próximamente]**: depende de confirmar con Dirección las escalas, competencias y periodos vigentes | Sprint 10 (meta del 12 feb 2027) |
| **Aula virtual** | **No se construye**: se integra Google Workspace for Education o Microsoft 365 A1, que son gratuitos | Después del sprint 10 |

Dos promesas que sí están en el diseño: **las notas y la libreta nunca se bloquean por deuda** (INDECOPI), y **una nota cambiada después del cierre del periodo exige la aprobación de otra persona**, igual que el dinero.

---

## 4. Guion de demo de 10 minutos

**Preparación:** cuatro ventanas abiertas y ya con sesión: `promotor` (Chrome), `caja` (incógnito), `director` (Edge) y `apoderado` (celular). Ten anotado el monto de la pensión de Mateo.

| Min | Usuario y pantalla | Qué haces | Qué dices |
|---|---|---|---|
| 0:00 – 1:00 | `promotor` · Inicio | Muestra el saludo, **Para revisar** con la alerta roja de Pedro Huanca y las cifras de **Hoy en caja** | «Esto es lo primero que ve el dueño cada mañana, también en el celular. Arriba, en rojo, lo crítico: ayer faltaron S/ 50 en una caja. No lo supimos a fin de mes: lo supimos ese mismo día.» |
| 1:00 – 3:00 | `caja` · Inicio → Cobrar | Busca «quispe», marca la pensión de Mateo, **Efectivo**, **Revisar cobro**, escribe lo recibido, **Cobrar S/ …**, muestra el vuelto y **Imprimir boleta** | «La cajera no escribe montos: la deuda la generó el sistema desde el plan que aprobó Dirección. La boleta sale al momento y la mamá recibe un aviso en su celular. Cada padre se vuelve auditor.» |
| 3:00 – 3:30 | `caja` · Pagos de hoy | Señala *«Caja no puede borrar ni anular pagos: solo pedirlo»* y el monto que dice *«Se ve al cerrar»* | «Si se equivoca, no puede borrar: pide la anulación y la aprueba otra persona. Y no puede sumar lo que cobró antes de contar.» |
| 3:30 – 5:30 | `caja` · **Cerrar mi caja** (**momento estrella**) | Lee en voz alta el recuadro «¿Por qué no ves cuánto deberías tener?». Escribe **S/ 50 menos** de lo cobrado hoy → **Registrar conteo** → aparece **Vuelve a contar** → mismo monto y en *¿Qué pasó?*: «Di vuelto de más a una familia» → **Registrar reconteo y cerrar** | «Esto es lo que habría salvado los S/ 70,000. La cajera cuenta a ciegas: no puede acomodar su conteo. Y fíjense: el conteo la protege a ella también, porque si cuadra queda demostrado.» |
| 5:30 – 6:30 | `promotor` · Inicio (recarga) | Aparece la **nueva alerta crítica** de Lucía Ramos con el esperado, lo contado y su explicación. Pulsa **Revisar** | «Pasaron segundos, no semanas. El dueño ya sabe cuánto falta, de quién es la caja y qué explicó.» |
| 6:30 – 7:30 | `director` · **Aprobaciones** | Muestra el cierre con diferencia (**Revisar el detalle**, **Aprobar con nota**) y la anulación de Thiago: al aprobar pide **llamar al apoderado** | «El que cobra nunca aprueba. Dirección revisa, deja una nota y todo queda con su nombre. Para devolver dinero, el sistema exige haber llamado a la familia.» |
| 7:30 – 8:30 | `apoderado` · celular | **Total por pagar**, **Lo que debes**, **Pagar en línea**, código del banco y **¿Algo no cuadra?** | «La mamá ve todo lo que debe, paga con Yape o tarjeta y descarga su boleta. Si ve algo raro, avisa directo a la Promotoría. Aquí el pago es simulado; con la pasarela del colegio será real.» |
| 8:30 – 9:30 | `promotor` · **Bitácora de auditoría** | Pulsa **Verificar integridad** y muestra **Anota esta huella** | «Todo lo que acabamos de hacer está aquí: quién, qué y cuándo. Nadie puede borrarlo sin que la verificación lo detecte, y esta huella le llega al dueño cada día para guardarla fuera del sistema.» |
| 9:30 – 10:00 | Cierre | Muestra «Próximamente» en el menú | «Lo que sigue: asistencia y comunicados para el inicio de clases, y luego notas y SIAGIE. Pero lo primero es que cada sol esté claro antes de la matrícula 2027.» |

**Si sobra tiempo o te preguntan:** la conciliación con el Yape inventado en rojo (3.7), el aislamiento con `caja.b` (3.12) o la puesta en marcha (3.11).

**Si algo falla en vivo:** no improvises arreglos. Di «esto lo reviso después» y pasa a la siguiente pantalla. La alerta de Pedro Huanca de ayer ya demuestra el faltante aunque el faltante en vivo falle.

---

## 5. Preguntas difíciles y su respuesta honesta

### 5.1 «¿Están seguros los datos? ¿Cumplen la Ley 29733?»
**Lo que existe:**
- Cada rol ve solo lo necesario (el docente no ve deudas ni contactos; el operador no ve familias).
- Queda registrado quién del personal vio datos personales, con alerta si alguien ve más de 50 fichas en un día (pantalla «Quién vio datos personales», solo Promotoría).
- Las familias ven **Mis datos** y pueden pedir acceso, rectificación, cancelación u oposición desde el portal.
- Hay un aviso de privacidad que la familia acepta al activar su cuenta.
- Los registros técnicos ocultan DNI, celulares y correos.
- Las sesiones se cierran tras 30 minutos sin actividad y duran como máximo 10 horas; hay límite de intentos de ingreso.
- Los respaldos son diarios y cifrados.
- Hay una plantilla de contrato de encargo de tratamiento de datos.

**Lo que falta (dilo):**
- El aviso de privacidad y los plazos los debe redactar y confirmar un **asesor legal**.
- **El colegio** debe inscribir sus bancos de datos y declarar el envío de datos fuera del país (servidores en Chile y WhatsApp de Meta en EE.UU.).
- Los contratos están pendientes de revisión por un abogado.

**Frase:** «La plataforma está construida para cumplir la ley, pero hay trámites que le tocan al colegio y textos que debe revisar un abogado. Se los dejo en una lista.»

### 5.2 «¿Qué pasa si se cae internet?»
**Honesto:** la plataforma es web y **no funciona sin conexión**. No hay modo fuera de línea.
- El servidor está en la nube, no en una PC del colegio: si se cae el internet del colegio, los datos no se pierden y las familias siguen pagando desde su celular o en el banco con el código del alumno.
- Se recomienda un **plan B de conectividad** (un router 4G o el celular como módem).
- **[verificar]** No hay un procedimiento escrito para cobrar en efectivo durante una caída (por ejemplo, un recibo provisional que se registra después). Propón definirlo juntos en el piloto.

### 5.3 «¿Emite boletas electrónicas de SUNAT?»
**Lo que hay en el código:**
- La plataforma emite **boletas, facturas y notas de crédito** con numeración correlativa sin huecos, y lleva una cola de envío al OSE con reintentos, alertas antes del plazo legal y reemisión si SUNAT rechaza una.
- Existe un **conector con Nubefact** (OSE/PSE), **pero está apagado y no se ha probado con una cuenta real**. El propio código dice que antes de activarlo hay que comprobarlo con la cuenta de prueba del colegio. **[verificar]**
- Hoy, en la demo y en el piloto, el comprobante es **simulado y sin valor tributario**.

**Frase:** «La plataforma está lista para enviar a SUNAT a través de un OSE como Nubefact, pero ese envío aún no se ha probado con una cuenta real. Mientras tanto, el colegio sigue emitiendo su comprobante legal como hoy. Si ya trabajan con un OSE, empezamos por ahí.»

### 5.4 «¿Cuánto cuesta?»
**Honesto:** **el precio todavía no está definido.** Los términos de servicio tienen el monto en blanco y lo decide el proveedor con su abogado.
- Lo que sí se sabe:
  - el servidor en Google Cloud cuesta unos **US$ 65–75 al mes por instalación**, sin contar WhatsApp, OSE ni la comisión de la pasarela, que contrata el colegio;
  - la suscripción se cobra **fuera de la plataforma**, con factura mensual y transferencia, y nunca se mezcla con el dinero de las familias.
- Si el colegio no pagara: solo se suspende el acceso del personal, con 30 días de aviso; **nunca** los avisos ni el acceso de las familias.

**Frase:** «Les traigo una propuesta económica por escrito. Primero quiero que vean si les resuelve el problema.» (Ten tu número pensado antes de la reunión.)

### 5.5 «¿Quién da soporte?»
**Honesto:** hoy, **tú**.
- Los términos proponen soporte de lunes a viernes de 8:00 a 18:00, con primera respuesta en 4 horas hábiles, por WhatsApp y correo (valores entre corchetes, por confirmar), y **30 días de soporte después del acta de conformidad**.
- Hay un plan de capacitación (sesiones por rol), **manuales de una página por rol** en `docs/manuales/` y guiones de videos.
- Las alertas técnicas (sin respaldo, errores) llegan al responsable técnico, y hay un vigilante externo que revisa cada 15 minutos que la aplicación responda.

### 5.6 «¿Cómo pasamos nuestros datos actuales?»
- **Alumnos y apoderados:** con la **importación desde Excel** (plantilla descargable, revisión fila por fila, todo o nada). Nadie digita 300 alumnos.
- **Deudas pendientes:** como **saldo inicial**, por lotes. Otra persona confirma escribiendo a ciegas el total del informe **validado por su contador**. No se copian del cuaderno, porque después del robo el cuaderno no es confiable.
- **Corte propuesto:** el cronograma en el sistema empieza el 01/12/2026; lo anterior entra como saldo inicial. Las deudas de años anteriores a 2026 quedan fuera, salvo que se pidan.
- **El historial de pagos antiguos no se migra** (no hay función para eso). **[verificar]** si lo necesitan.
- **Piloto:** dos semanas en paralelo con el cuaderno, conciliando ambos cada día.

### 5.7 «¿Un empleado puede borrar un pago?»
**No.** Nadie puede borrarlo, ni Caja, ni Dirección, ni Promotoría.
- Un pago solo se **anula** con causa, motivo y la aprobación de **otra persona**, y queda una nota de crédito.
- En la instalación real, **la base de datos misma** rechaza borrar pagos o cambiar montos y fechas de cuotas, aunque alguien saltara el programa. Si alguien quita esas protecciones, la aplicación **no arranca**.
- Si alguien con acceso técnico tocara la bitácora directamente, **Verificar integridad** y la huella diaria lo detectan.
- Lo que no se puede impedir del todo es la **colusión** (dos personas de acuerdo). Eso se detecta con los avisos a las familias, la conciliación con el banco, la llamada de control y la bitácora. Dilo así: «no lo hace imposible; lo hace visible».

### 5.8 «¿Y si usted desaparece?»
**Lo que existe y protege al colegio:**
- Los datos son **del colegio** (los términos lo dicen). Al terminar el contrato, el proveedor entrega los datos en 30 días (un Excel por tabla). **[verificar]** que ese proceso de exportación está descrito en los términos, pero no hay un botón que lo haga: hoy sería un procedimiento manual.
- Los respaldos diarios van cifrados para **dos llaves: una de la Promotoría y otra del responsable técnico**. El colegio puede abrir su propio respaldo.
- El sistema usa tecnología estándar y muy conocida (Java, Spring Boot y MySQL), tiene miles de pruebas automáticas y una documentación extensa (diseños, manuales, operación, despliegue). Otro desarrollador puede continuarlo.
- La infraestructura está en Google Cloud con scripts reproducibles, no en una PC.

**Lo que falta (dilo):** no hay todavía un acuerdo sobre **el código fuente** (por ejemplo, un depósito del código a favor del colegio o una licencia si el proveedor deja de operar) ni un segundo técnico designado. **[verificar]** Propónlo como cláusula del contrato.

### 5.9 Otras preguntas probables (respuestas cortas)
- **«¿La cajera puede hacer un descuento?»** No. Lo pide Administración y lo aprueba Promotoría o Dirección.
- **«¿Bloquea al alumno si debe?»** No. La ley no lo permite y el sistema no condiciona exámenes, notas ni libretas al pago. El docente ni siquiera ve las deudas.
- **«¿Cobran mora?»** Hoy la plataforma no calcula moras. Si el colegio la quiere, primero la valida un asesor legal.
- **«¿Llegan WhatsApp de verdad?»** El conector con WhatsApp (de Meta) y el envío por correo existen en el código. Falta la verificación del negocio en Meta y aprobar las plantillas, un trámite que demora semanas. **[verificar]** Mientras tanto, en la demo los mensajes son simulados y se ven en «Mensajes a las familias».
- **«¿Funciona en el celular?»** Sí. El portal de familias, el panel de la promotora y las aprobaciones están pensados para el celular.
- **«¿Dónde están los datos?»** En Google Cloud, región Santiago de Chile (la más cercana a Lima). **[verificar]** Todavía no se ha desplegado: falta la prueba inicial en Cloud SQL.

---

## 6. Glosario corto

| Término | Qué significa en Cuotaria |
|---|---|
| **Cuota** | Lo que una familia debe por un concepto (pensión de octubre, matrícula), con monto y vencimiento. La genera el sistema. |
| **Plan de pensiones** | Los montos y vencimientos de un nivel en un año. Lo propone Administración y lo aprueba otra persona. |
| **Cronograma** | Las cuotas de un alumno en el año, generadas desde el plan. |
| **Saldo inicial** | Las deudas anteriores al sistema, validadas por el contador y confirmadas a ciegas. |
| **Comprobante** | Boleta (persona) o factura (con RUC). En el piloto es simulado. |
| **Nota de crédito** | Documento que anula una boleta cuando se aprueba la anulación de un pago. |
| **OSE** | Operador que envía los comprobantes electrónicos a SUNAT y les da validez (por ejemplo, Nubefact). |
| **Pasarela de pago** | Empresa que cobra con Yape, Plin o tarjeta en línea (Culqi, Niubiz, Izipay). Hoy, solo simulada. |
| **Recaudación bancaria** | La familia paga en el banco con el código del alumno; el archivo del banco se sube y los pagos se aplican solos. |
| **Cierre ciego** | La cajera cuenta el efectivo sin ver cuánto debería tener; solo después ve la diferencia. |
| **Fondo fijo** | Sencillo que se queda en caja para dar vuelto; no se deposita. |
| **Faltante / sobrante** | Diferencia entre lo registrado y lo contado en un cierre. Genera alerta. |
| **Conciliación** | Cruce de los pagos registrados con los movimientos reales del banco. |
| **Extracto** | El movimiento de la cuenta del colegio que se descarga del banco y se sube cada día. |
| **Confirmar a ciegas** | Otra persona escribe lo que ve en su app del banco (saldo o total) sin que el sistema se lo muestre; el sistema compara. |
| **Segregación de funciones** | Quien cobra no aprueba; quien pide no aprueba; quien sube no confirma. |
| **Bandeja de aprobaciones** | Donde Promotoría o Dirección aprueban o rechazan lo que pidió otra persona. |
| **Bitácora (auditoría)** | Registro de todo lo que pasa: quién, qué, cuándo y desde dónde. Solo admite agregar. |
| **Huella** | Número de evento, fecha y código de la bitácora que el dueño guarda fuera del sistema para detectar si alguien la recortó. |
| **Correo externo de control** | Correo de un tercero de confianza (el contador) que recibe la huella y los avisos importantes. |
| **Llamada de control** | Cada semana el sistema elige familias que pagaron en efectivo; Promotoría o Dirección les pregunta cuánto pagaron y compara. |
| **En preparación / en producción** | Estados del colegio: en preparación no se cobra; Promotoría lo pasa a producción con su firma. |
| **Conector** | La conexión de un colegio con su OSE, pasarela, cuenta bancaria o recaudación. Cambiarlo exige dos personas. |
| **Operador de la plataforma** | Quien da de alta colegios. No ve datos de las familias. |
| **SIAGIE** | Sistema del MINEDU donde los colegios registran matrícula y notas oficiales. Cuotaria facilitará la exportación; no lo reemplaza. |
| **INDECOPI** | Entidad que protege al consumidor: el colegio no puede condicionar evaluaciones al pago. |
| **Ley 29733** | Ley de Protección de Datos Personales del Perú. |

---

## 7. Autoevaluación (15 preguntas)

Tapa la respuesta, contesta en voz alta y luego compara.

1. **¿Con qué usuario demo muestras el faltante de caja que ya existe y de cuánto es?**
   Con `promotor`, en Inicio → Para revisar. Es de S/ 50 en la caja de Pedro Huanca (`caja2`), del día anterior: esperado S/ 380, contado S/ 330.

2. **¿Por qué la cajera no ve el monto esperado al cerrar?**
   Porque es un cierre a ciegas: si lo viera, podría acomodar su conteo para que cuadre. Solo después de registrar su conteo ve lo registrado y la diferencia.

3. **¿Cuántas veces puede volver a contar la cajera?**
   Una. El reconteo cierra la caja y exige explicar qué pasó.

4. **¿Quién aprueba una anulación pedida por Lucía Ramos?**
   Promotoría o Dirección, nunca ella. Quien pidió, quien cobró ese pago o la dueña de la caja no pueden aprobarla.

5. **¿Qué pasa con la boleta cuando se aprueba una anulación?**
   Se emite una nota de crédito, las cuotas vuelven a deberse y la familia recibe un aviso. El pago original no se borra.

6. **¿Qué exige el sistema antes de aprobar una devolución de dinero ya cobrado?**
   Llamar a la familia: escribir el número al que se llamó y marcar «Hablé con el apoderado».

7. **¿Quién decide el monto de una pensión?**
   Nadie en caja. Administración propone el plan, otra persona de Promotoría o Dirección lo aprueba, y el sistema genera las cuotas.

8. **¿La plataforma cobra mora?**
   No. Hoy no existe el cálculo de mora; antes de crearlo, un asesor legal debe validarlo.

9. **¿La boleta que sale hoy tiene validez ante SUNAT?**
   No. Es simulada. Existe un conector con Nubefact, pero está apagado y sin probar con una cuenta real. El colegio sigue con su comprobante legal mientras tanto.

10. **¿Cómo se detecta un Yape inventado por la cajera?**
    En la conciliación: al confirmar el extracto del banco de ese día, el Yape aparece en rojo en «Debía estar en el banco y no está», con quién lo registró.

11. **¿Por qué el extracto lo confirma una persona distinta de la que lo subió, y cómo?**
    Para que nadie pueda subir un extracto alterado y darlo por bueno. La otra persona revisa una muestra de movimientos sin montos en su app del banco y escribe a ciegas el saldo final.

12. **¿Qué es la huella y dónde se guarda?**
    El número de evento, la fecha y el código de la bitácora. Se anota fuera del sistema (cuaderno o celular) y llega cada día a Promotoría y al correo externo de control. Sirve para detectar si alguien recortó la bitácora.

13. **¿Qué ve el docente de un alumno con deuda?**
    Nada de la deuda. Solo ve sus secciones y los nombres de sus alumnos, sin pagos, documentos ni contactos.

14. **¿Qué puede ver el operador de la plataforma de un colegio?**
    Solo la ficha del colegio y la lista de puesta en marcha como sí/no y conteos. Nunca datos de las familias.

15. **¿Qué está «próximamente» y qué no debes prometer como listo?**
    Asistencia con aviso a los padres, comunicados con confirmación de lectura, nómina para el SIAGIE, y notas por competencias con libretas y exportación al SIAGIE. Tampoco prometas como listos la pasarela real, el envío real a SUNAT ni los WhatsApp reales: dependen de trámites del colegio. Lo que sí existe en lo académico es la asignación de docentes a secciones.

---

### Fuentes que usó esta guía
- `CLAUDE.md`, la skill `contexto-colegio`, `docs/plan-de-desarrollo.md` y `docs/estado-del-proyecto.md`
- `docs/arquitectura/` (sprints 1 a 9) y `docs/ventas/guia-demo.md`
- `docs/entrega/terminos-de-servicio.md` y `docs/operacion/suscripcion.md`
- `src/main/resources/templates/` (nombres de pantallas y botones), `seguridad/config/ModuloApp.java` y `GrupoMenu.java` (menú y permisos)
- Datos de demostración: `seguridad/inicial/DatosDemoDev.java`, `alumnos/inicial/DatosDemoColegioDev.java`, `DatosDemoApoderadoDev.java`, `caja/inicial/DatosDemoCajaDev.java` y `conciliacion/inicial/DatosDemoConciliacionDev.java`
- Conectores: `comprobantes/service/EmisorNubefact.java`, `conectores/model/ProveedorConector.java` y `TipoConector.java`
- Capturas: `docs/ux/rediseno-aplicado/despues/`
