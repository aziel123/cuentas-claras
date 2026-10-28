# Manual de Promotoría · Cuentas Claras

*Colegio Virgen María · Para la promotora o dueña · Una página A4 · Versión 1, octubre de 2026 · Reemplaza la «Guía de una página para la promotora»*

## Tu día con Cuentas Claras
1. **Abre el panel** en el celular (*Panel del colegio*). Las cifras salen de los pagos y cuotas registrados: nadie las escribe a mano. Mira primero las **alertas en rojo** y toca **Revisar**. Las críticas también te llegan al celular, una vez cada una y sin nombres.
2. **«Para aprobar»** abre las solicitudes de tu equipo: las apruebas tú o Dirección. Lo que pides tú se aplica directo
   (ver «Lo que haces directo y lo que te pide un código»).
3. **A las 19:30 te llega el resumen del día** con la huella de la bitácora. **Si a las 20:00 no llegó, avisa** al responsable técnico; el panel también marca «El resumen no salió».
4. **Cada semana, la llamada de control** (*Llamadas de control*): el sistema elige al azar hasta 3 familias que pagaron en efectivo o deben. Llama, **pregunta primero** cuánto y qué día pagaron, toca **«Ya me dijo: ver lo registrado»** y registra Confirma, No confirma (con lo que te dijo) o No contesta. Si no contesta, vuelve a llamar una hora después; si tampoco, el sistema elige otra familia. Si no puedes una semana, delega en Dirección. Hazlas antes de que termine el domingo.
5. **Datos personales:** en la ficha de cada familia ves «Quién consultó estos datos» (últimos 90 días); en la bitácora, los accesos por persona y fechas. Una vez al mes, revisa el reporte «Datos con plazo vencido».
6. **«Estado técnico y respaldos»** (enlace en el panel): el respaldo debe decir «Al día». **El primer lunes de cada mes**, 20 minutos con el responsable técnico: restauran un respaldo, tú escribes la clave de la bitácora y firmas el acta.

## Lo que el sistema no te deja hacer, y por qué
- **Aprobar desde un enlace:** los enlaces solo abren la pantalla y piden tu sesión.
- **Corregir cifras ya enviadas:** si cambian sin explicación, es una alerta crítica.
- **Cambiar un registro de la llamada de control:** queda tal cual.
- **Dar o quitar Promotoría o Dirección sin tu código:** son acciones sensibles (abajo). Dar Promotoría en producción,
  quitarla o desactivar a otra Promotoría esperan además 24 horas; el colegio nunca queda sin Promotoría activa.
- **Cambiar tu celular o correo de un solo paso:** lo confirmas desde tu contacto ANTERIOR (te llega un código ahí) y lo
  aprueba otra Promotoría. Si perdiste tu celular, lo cambian dos operadores de la plataforma después de verificarte.
- **Ser Caja ni cobrar:** quien cobra no aprueba.
- **Tener dos sesiones:** entrar desde el celular cierra la de la computadora; también a los 30 minutos sin actividad y a las 10 horas.

## Si algo no cuadra
- **«No confirma» es crítica:** revisa los pagos de esa familia y la caja de quien cobró.
- **Alerta de que alguien vio más de 50 fichas en un día:** revisa en los accesos qué abrió antes de hablar con esa persona.
- **«Sin respaldo» o «Faltan filas»:** avisa al responsable técnico y no toques nada. «Faltan filas» sigue en rojo
  hasta que **tú** la resuelvas en el estado técnico («Resolver la alerta»), con un motivo, después de revisar con el
  responsable técnico qué faltó y por qué. Él no puede cerrarla.
- **Te avisan de un cambio de contacto que no pediste:** avisa de inmediato.
- Revisa la bitácora (*Verificar integridad*) y la caja del día.

**Tus dos claves, solo tuyas:** la clave de la bitácora y la llave para abrir los respaldos. Guárdalas fuera del sistema, en sobre cerrado o gestor personal. Nunca por chat ni correo.

## Puesta en marcha, conectores y paso a producción (sprint 8)
- **Al empezar**, el operador de la plataforma crea tu colegio y tu cuenta en una reunión contigo: activas tu cuenta con
  **tu DNI** (el del contrato) y ves quién la creó. Desde ahí el operador ya no puede entrar a tu colegio.
- **`/puesta-en-marcha`** te dice qué falta para cobrar: tu equipo (una Dirección y al menos una Caja, personas distintas de
  ti), año y secciones, pensiones aprobadas, alumnos, saldo inicial, comprobantes, cuenta bancaria, mensajes, correo de
  control, aviso de privacidad y respaldo. Nada se marca a mano: la lista lo calcula.
- **Pasar a producción** lo haces tú, con la firma de tu sesión, cuando la lista está completa. No hay vuelta atrás: desde
  ahí se registran pagos y salen los avisos a las familias. Antes, nadie puede cobrar (lo impide también la base).
- **Conectores** (`/conectores`): adónde entra el dinero (cuenta bancaria, pago en línea, recaudación) y quién emite las
  boletas (OSE). Cada cambio lo propone una persona y lo aprueba **otra** con su firma, y en lo que mueve dinero **una
  Promotoría participa**. Cuando un conector cambia, te llega un aviso a ti (y a toda la Promotoría) y al correo externo de
  control, y queda una alerta crítica 7 días: **si no lo reconoces, avisa hoy al contador y revisa la conciliación.**
- **Marca del colegio** (`/colegio/marca`): razón social, dirección y logo que ven las familias en su portal y en las boletas.
- **Respaldos en una instalación compartida:** la clave del respaldo y de la bitácora las custodia la plataforma (2
  personas). Tú compruebas tu bitácora con la huella diaria por WhatsApp y una vez por trimestre eliges 3 huellas que el
  operador busca contigo en un respaldo restaurado ([acta](../operacion/acta-verificacion-trimestral.md)). Si
  `/panel/sistema` dice «Faltan filas», es de TU colegio: revisa con el responsable técnico y resuélvelo con motivo.

## Lo que haces directo y lo que te pide un código (sprint 11)
- **Directo, en un paso:** lo que tú haces se aplica al momento, con la firma de tu sesión: plan de pensiones, saldo
  inicial, días no laborables, comunicados y lo que pides en «Para aprobar». **Cada acción directa avisa al momento** a todas
  las Promotorías (también a ti) y al correo externo de control. Si no reconoces un aviso, cambia tu clave y avisa hoy.
- **Con tu código por WhatsApp (6 dígitos, 10 minutos, una sola vez):** anular o devolver un pago o una cuota, descuentos y
  becas, partidas manuales, el contacto o el responsable de pago de una familia, los roles o la cuenta de Promotoría o
  Dirección y todo conector. Pulsa **«Enviar código»**: el WhatsApp dice exactamente qué autoriza y el código no sirve para
  otra cosa. **Nunca llega por correo**: si no tienes celular registrado, no hay código.
- **Con espera de 24 horas:** la cuenta bancaria, el pago en línea y la recaudación nuevos; dar Promotoría en producción,
  quitarla o desactivar a otra Promotoría. Durante la espera la frena **otra Promotoría con 30 días o más en el rol**, la
  persona afectada o un operador de la plataforma; tu socia (30 días o más) puede aprobar al instante un conector con SU
  código. Si nadie la frena, se aplica sola al terminar.
- **Lo de tu equipo** (Administración, Caja, Docentes, Dirección) sigue con dos personas: lo apruebas tú o Dirección. La
  Dirección que creó una cuenta no aprueba lo de esa cuenta; tú sí.
- **Extractos del banco:** si cambiaste la cuenta bancaria hace menos de 30 días, el extracto lo confirma otra persona.

## Qué ves mientras tu colegio está en preparación (sprint 11)
- **Arriba de tu inicio** ves «Tu colegio está en preparación: faltan N de 15 pasos», una barra con los pasos obligatorios hechos (de 13) y **el siguiente paso**, con quién lo hace.
- Si el siguiente paso te toca, tienes el botón que te lleva directo a la pantalla. Si no, dice a quién le toca y, si hay algo que sí puedes hacer tú, te lo muestra aparte («Lo que puedes hacer tú»).
- Las pantallas vacías dicen qué falta, quién lo hace y, si depende de otro paso, «Primero: …».
- Lo que todavía no se puede (cobrar, enviar un comunicado, confirmar la recaudación, abrir la renovación) se ve deshabilitado con el motivo al lado.
- Un «i» junto a un título o un botón abre una ayuda corta: tócalo (o pasa el mouse) para leerla.
- **Tu inicio** muestra solo la guía (sin cifras ni gráficos en cero) y los 3 pasos que siguen. Las cifras y los gráficos aparecen cuando el colegio pasa a producción.
- En la **Puesta en marcha** (`/puesta-en-marcha`), cada paso tiene su estado en palabras, quién lo hace e «Ir». Ahí firmas, con confirmación, «sin deudas anteriores» (en el paso del saldo inicial) y el acepto del comprobante simulado (o al proponerlo en Conectores, con la casilla). «Pasar a producción» se ve deshabilitado hasta que falten 0 pasos obligatorios.
- Lo que haces tú se aplica directo (tanda 1c); lo que propone Administración lo apruebas tú o Dirección, y aparece en **Aprobaciones** con «Revisar», aunque se apruebe en su pantalla (planes, saldo inicial, extractos, recaudación, feriados, correo de control y respaldo).

## Dónde ver el video
| Video 5 · El panel y el resumen | Video 6 · La llamada de control | Video 4 · Aprobar desde el celular |
|---|---|---|
| Código QR del video 5: se agrega cuando el video esté publicado | Código QR del video 6: se agrega cuando el video esté publicado | Código QR del video 4: se agrega cuando el video esté publicado |
