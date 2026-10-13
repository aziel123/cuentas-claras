# Guía de la demo (para ventas)

> Sprint 8, tanda 3 (sección 8 del diseño; decisiones 126 y 127). La demo está en `https://demo.<dominio>` (el nombre
> comercial y el dominio siguen siendo provisionales: `CC_MARCA_NOMBRE`). Es una instalación aparte, con datos **ficticios**
> en memoria, **sin salida a internet**, que **se borra cada noche a las 03:00**.

## Qué decirle al prospecto antes de empezar
- Entra a `https://demo.<dominio>/demo` y pulsa **«Crear mi demo»**: en un minuto tiene SU colegio ficticio (nadie más lo
  ve), con 40 familias, pensiones, los pagos de hoy y una cuenta por rol. **La clave se muestra una sola vez**: que la anote.
- **No envía mensajes ni cobra.** Los WhatsApp a las familias se ven dentro de la demo («Mensajes»), pero no salen a nadie; las
  boletas dicen «DEMO · SIN VALOR».
- **No escriba datos reales** (ni de sus alumnos ni de su personal). Para probar la importación, la demo solo acepta el archivo
  de ejemplo (se descarga desde la misma pantalla).
- Se puede crear hasta 3 demos por conexión al día.

## El recorrido de 6 minutos (el orden del guion de los anuncios)
Está también en la demo (`/demo/guia`, enlazado desde la franja roja de arriba).

| # | Qué muestra | Con qué cuenta | Qué decir |
|---|---|---|---|
| 1 | **Cobrar** a una familia: buscar por apellido o DNI, elegir la pensión, registrar. Sale la boleta al momento | Caja (Lucía Ramos) | «La cajera no decide montos: la deuda la genera el sistema.» |
| 2 | **El padre recibe un WhatsApp en cada pago**: «Mensajes» muestra el aviso con monto, concepto y número de boleta | Promotoría | «Cada padre es un auditor: si un pago no se registra, la familia lo nota ese mismo día.» |
| 3 | **Cerrar la caja a ciegas**: la cajera escribe lo que cuenta sin ver lo esperado; si no cuadra, lo aprueba otra persona | Caja | «La diferencia sale el mismo día, no a fin de mes.» |
| 4 | **Anular exige a otra persona**: en «Aprobaciones» hay un pedido de devolución de la cajera | Dirección o Promotoría | «Quien cobra nunca aprueba su propia anulación.» |
| 5 | **El panel de la promotora**: cobrado hoy, morosidad y alertas, desde el celular | Promotoría | «El dueño ve lo que pasa sin depender de nadie.» |
| 6 | **La bitácora**: cada cobro, aprobación y anulación con quién, qué y cuándo; «Comprobar integridad» | Promotoría | «Nadie puede borrar ni editar lo que pasó sin que se note.» |

Otros puntos según el interés: el **portal de las familias** (cuenta Apoderado: estado de cuenta, pago en línea simulado), el
**descuento por hermanos** aprobado, el **saldo inicial** que Promotoría confirma, los **planes de pensiones** que propone
Administración y aprueba Dirección, y la **importación** con el archivo de ejemplo.

## Lo que la demo NO muestra (decirlo si preguntan)
- **La segunda capa de la base de datos:** en una instalación real, MySQL rechaza por su cuenta las operaciones prohibidas
  (por ejemplo, anular sin la aprobación firmada) aunque alguien saltara la aplicación. La demo usa una base en memoria
  (H2) sin esos triggers: muestra las reglas de la aplicación.
- **WhatsApp, correo, OSE, pasarela y banco reales:** todo es simulado.
- **La conciliación bancaria con un extracto real** (se muestra en una reunión con un extracto de ejemplo).
- **El alta de un colegio por el operador:** si el prospecto la quiere ver, la muestra el equipo de ventas con su cuenta de
  operador de la demo, en un colegio ficticio.

## Preguntas frecuentes
- *¿Mis datos están en Chile?* La instalación real está en Google Cloud, región Santiago (la más cercana a Lima). El contrato
  de encargo de tratamiento lo declara (Ley 29733).
- *¿Qué pasa si no pago la suscripción?* Nunca se cortan los avisos de pago a las familias ni su acceso a sus comprobantes sin
  30 días de aviso (`docs/operacion/suscripcion.md`).
- *¿El operador de la plataforma puede ver a mis familias?* No: solo ve la ficha del colegio y una lista de sí/no.
