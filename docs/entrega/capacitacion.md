# Plan de capacitación presencial · Cuentas Claras

> Sprint 7, entrega (sección 15.3 de `docs/arquitectura/sprint-7-endurecimiento.md`). Fechas por defecto: semana del 25 de enero, o antes si el piloto con datos reales empieza antes (decisión 105). La lista de asistencia firmada es anexo del acta de conformidad.

**Principio:** cada persona hace los ejercicios **sola**, con su usuario y en su equipo de trabajo. Quien capacita observa, no toca el teclado y no corrige en el momento: anota dónde se trabó la persona. Si alguien se traba, el problema es de la pantalla o del manual, no de la persona.

## 1. Lugar y entorno
- **En el colegio**, en los equipos de cada puesto (la computadora de Caja, el celular de Promotoría y de Dirección).
- **Entorno piloto** con datos de prueba: todas las páginas muestran la franja **«PILOTO · Entorno de prueba»**. Los comprobantes son simulados (sin valor ante SUNAT) y un pago en línea simulado queda por revisar, sin tocar las cuotas. Ningún dato real entra al piloto.
- **Usuarios reales de cada persona** (no los de demostración). Antes de la sesión, cada persona **activó su cuenta** con su enlace y **verificó su celular**; quien no lo hizo, lo hace en los primeros 10 minutos con ayuda.
- **Material impreso:** el manual de su rol (`docs/manuales/`) y, cuando estén publicados, los videos de su rol.

### Preparación del piloto (responsable técnico, el día anterior)
- [ ] Familias y alumnos de prueba con nombres inventados, cuotas vencidas y por vencer, y al menos una familia con dos hijos.
- [ ] Para S1: un sobre con efectivo de juguete que **no cuadra** con lo registrado (diferencia de S/ 10.00) para el cierre.
- [ ] Para S2: un extracto de prueba con **un Yape registrado en caja que no está en el banco** y un abono de intereses sin pareja.
- [ ] Para S3: una anulación, un descuento y un cierre por aprobar pedidos por otras personas, una solicitud pedida por la propia directora y un pedido «Mis datos personales» de una familia de prueba.
- [ ] Para S4: una muestra de llamada de control con números de prueba (de quien capacita), una ficha de familia consultada por otra persona y el respaldo para el simulacro.
- [ ] Cronómetro y hoja de observación por persona (tarea, tiempo, errores, dónde se trabó).

## 2. Sesiones
| Sesión | Con quién | Duración | Ejercicios (cada persona sola) | Criterio de éxito |
|---|---|---|---|---|
| **S1** | Caja (2 personas) | 90 min, y acompañadas el primer día de cobro real | Cobrar a 3 familias: una en efectivo, una con Yape y una de 2 cuotas. Pedir una anulación con motivo. Cerrar a ciegas con la diferencia preparada y registrar el depósito | Cobra en **menos de 1 minuto** sin ayuda y explica por qué no ve lo registrado antes de contar |
| **S2** | Administración y contador | 90 min | Subir el extracto con la diferencia preparada. Explicar el abono de intereses. Descargar el Excel del contador. Pedir un descuento por hermanos | Encuentra la diferencia **sin ayuda** |
| **S3** | Dirección | 60 min | Aprobar y rechazar desde el celular. Intentar aprobar la solicitud que pidió ella misma (no puede). Atender el pedido de datos personales de la familia de prueba | Explica con sus palabras **«quien pide no aprueba»** |
| **S4** | Promotoría | 60 min | Panel, alertas, resumen y llamada de control. Ver quién consultó una ficha. *Estado técnico y respaldos*. **Primer simulacro presencial de restauración** con su clave de la bitácora (sección 9.5) | Hace el simulacro y **firma su acta** (`docs/operacion/acta-simulacro-restauracion.md`) |
| **S5** | Todo el personal junto | 30 min | **«Intenta hacer trampa»:** la cajera intenta anular su propio pago, cambiar un monto y cerrar su caja sin contar; todos ven que el sistema no lo permite y que el intento queda en la bitácora | Nadie cree que «alguien lo puede arreglar por debajo» |
| **S6** | Familias (mesa de ayuda) | 2 mañanas de matrícula | Activar el portal en el celular de la familia con el volante `familias.md` y el video 7 | Meta del plan: **70 % de familias con el portal activado al tercer mes** |

- **S4 y el simulacro:** el acta de conformidad pide que se restaure **un respaldo real**. Si a la fecha de S4 la plataforma todavía no está en producción, el simulacro se hace con el respaldo del piloto y se repite con el primer respaldo de producción en el primer lunes del mes siguiente.
- **S6 y las cuentas reales:** la familia activa su **cuenta real**, así que S6 se hace con la plataforma en producción. No se activan cuentas de familias reales en el piloto.
- **Docentes:** en marzo, con la fase académica. Hasta entonces solo activan su cuenta y leen `docente.md`.

## 3. Preguntas de verificación (al final de cada sesión)
Se hacen de palabra, una por una. Cuenta como correcta si la idea está, aunque no use las mismas palabras. Con 2 de 3 correctas, la persona aprueba; si no, se repasa el punto ese mismo día.

**Caja (S1)**
1. ¿Por qué no ves lo registrado antes de contar tu caja? *(Para que el conteo sea mío y nadie pueda acomodarlo.)*
2. Cobraste la pensión equivocada. ¿Qué haces? *(Pido la anulación o la corrección en Pagos de hoy, con el motivo; la aprueba Promotoría o Dirección.)*
3. Un apoderado dice «yo ya pagué» y no aparece. ¿Qué haces? *(Reviso sus pagos recientes; si no está, le pido usar «¿Algo no cuadra?» o aviso a Promotoría. No registro nada sin dinero.)*

**Administración (S2)**
1. ¿Por qué no puedes confirmar el extracto que subiste? *(Porque lo confirma otra persona mirando el banco: así un archivo alterado no pasa solo.)*
2. Un Yape de caja sale en rojo. ¿Qué significa y qué haces? *(Que no está en el banco; lo reviso hoy con quien lo registró y aviso a Promotoría.)*
3. ¿Qué pasa cuando descargas el Excel del contador? *(Queda en la bitácora con mi nombre y un código impreso; no lleva documentos ni contactos.)*

**Dirección (S3)**
1. ¿Por qué no te aparece el botón Aprobar en una solicitud? *(Porque la pedí yo o participé: la resuelve otra persona.)*
2. Una familia pide que borren sus datos. ¿Qué respondes? *(Que los pagos y la bitácora se conservan por obligación tributaria y como evidencia; le explico por escrito qué se guarda y por qué, dentro del plazo.)*
3. ¿Cuál es el plazo para responder un pedido de rectificación? *(10 días hábiles, a confirmar por el asesor legal; a los 7 aparece una alerta.)*

**Promotoría (S4)**
1. Son las 20:00 y no te llegó el resumen. ¿Qué haces? *(Aviso al responsable técnico; el panel también lo marca.)*
2. En la llamada de control, ¿qué preguntas primero? *(Cuánto pagó y qué día, sin decir los montos.)*
3. ¿Dónde guardas la clave de la bitácora y la llave del respaldo? *(Fuera del sistema, en sobre cerrado o gestor personal; nunca por chat ni correo.)*

**Todo el personal (S5)**
1. ¿Se puede borrar un pago? *(No: se anula con motivo y aprobación de otra persona, y queda a la vista.)*
2. Si algo no cuadra, ¿qué es lo primero? *(Avisar a Promotoría o Dirección sin tocar nada.)*
3. ¿Qué queda registrado cuando abres la ficha de una familia? *(Mi nombre, la pantalla y la fecha.)*

**Familias (S6, mesa de ayuda)**
1. ¿Dónde ves lo que debes? *(En Mi familia, arriba, el total por pagar.)*
2. Pagaste y no aparece. ¿Qué haces? *(Uso «¿Algo no cuadra?» en el portal.)*
3. Alguien te llama del colegio y te pide tu clave. ¿Qué haces? *(No la doy: el colegio nunca la pide.)*

## 4. Métricas de usabilidad durante la capacitación
Por cada tarea se anota en la hoja de observación: **éxito sin ayuda** (sí o no), **tiempo**, **errores** y **dónde se trabó**. Al final de S1, S2, S3 y S4, cada persona llena el cuestionario **SUS** (10 preguntas). Meta: **SUS de 70 o más** por perfil. Con 2 o 3 personas por perfil el SUS es una señal, no una medida: se completa con las primeras semanas de uso.

## 5. Lista de asistencia (anexo del acta)
Sesión: ______ · Fecha: ____/____/______ · Lugar: ______________________ · Capacita: ______________________

| N.° | Nombre completo | Rol | Hora | Activó su cuenta y verificó su celular (Sí / No) | Ejercicios hechos sola (Sí / No) | Preguntas correctas (de 3) | Firma |
|---|---|---|---|---|---|---|---|
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |
| 6 | | | | | | | |
| 7 | | | | | | | |
| 8 | | | | | | | |

Firma de quien capacita: ______________________ · Firma de Promotoría o Dirección: ______________________

## 6. Seguimiento de 2 semanas
- **Caja:** 15 minutos diarios, al cierre del día, durante 2 semanas: ¿cuánto tardó cada cobro?, ¿cerró sin ayuda?, ¿hubo diferencia y se explicó el mismo día?
- **Promotoría:** una reunión semanal de 30 minutos: alertas de la semana, llamadas hechas, resumen recibido cada día, avisos de las familias y pedidos de datos personales con su plazo.
- **Administración y Dirección:** una revisión al final de cada semana de lo que quedó pendiente en conciliación y en la bandeja.

| Día | Fecha | Con quién | Qué se observó | Problema o duda | Qué se hizo | Responsable |
|---|---|---|---|---|---|---|
| 1 | | | | | | |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |
| 6 | | | | | | |
| 7 | | | | | | |
| 8 | | | | | | |
| 9 | | | | | | |
| 10 | | | | | | |

Si la misma duda aparece dos veces, se corrige el manual (y, si hace falta, se pide el cambio de la pantalla).

## 7. Métricas de adopción y de impacto (skill `evaluacion-ux`)
| Métrica | Meta inicial | Dónde se mide | Línea base | Al mes 1 | Al mes 3 |
|---|---|---|---|---|---|
| % de pagos digitales (no efectivo) | Más de 60 % al tercer mes | Panel del colegio («… digital») y *Reportes › Ingresos por medio de pago* | | | |
| % de familias que activaron el portal | Más de 70 % | Conteo de cuentas de apoderado activadas (lo da el responsable técnico) | | | |
| Cierres de caja sin diferencia | 100 % (las diferencias se explican el mismo día) | *Aprobaciones › Cajas del día* | | | |
| Morosidad | Bajar 20 % frente al año anterior | *Reportes › Morosidad por grado*; el año anterior, del contador | | | |
| Tiempo para registrar un pago presencial | Menos de 1 minuto | Cronómetro en S1 y en el seguimiento | | | |
| Docentes que registran notas en el sistema | 100 % al primer bimestre | Fase académica (marzo) | | | |
| SUS por perfil | 70 o más | Cuestionario al final de S1 a S4 | | | |

Si una métrica no llega a su meta al tercer mes, se entrevista a 3 personas del perfil (guiones de la skill `evaluacion-ux`) antes de cambiar nada.
