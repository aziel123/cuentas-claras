# Cierre de caja, depósito y verificación bancaria

Guía de operación del sprint 3 (tanda 3). El diseño completo está en `docs/arquitectura/sprint-3-caja.md`.

## El día de la cajera
1. **Cobra.** Su caja del día se abre sola con el primer cobro.
2. **Cierra a ciegas** desde *Caja › Cerrar caja* (`/caja/cierre`).
   - Cuenta todo el efectivo, incluido el fondo fijo. Puede escribir el total o llenar los billetes y monedas; en ese caso el sistema hace la suma.
   - Mientras no registre su conteo, la pantalla **no muestra lo registrado** (el esperado). *Pagos de hoy* tampoco muestra totales.
   - Si el conteo coincide, la caja se cierra.
   - Si no coincide, la pantalla dice «No coincide, vuelve a contar», sin mostrar montos. Hay **un solo reconteo**: lleva explicación obligatoria y **siempre cierra**, con la diferencia que resulte.
   - El primer conteo queda guardado. No se puede volver a contar para dar con el esperado (en MySQL también lo impide el trigger).
3. **Ve el resultado:** el esperado, lo contado y la diferencia. Con la caja cerrada solo se aceptan **pagos digitales** hasta el día siguiente.
4. **Deposita** lo contado menos el fondo fijo y registra la cuenta, el número de operación del voucher y la fecha. Si deposita otro monto, la explicación es obligatoria y Promotoría recibe una alerta crítica.

## Si quedó abierta la caja de un día anterior
- Mientras su caja de un día anterior siga abierta, la cajera **no puede cobrar hoy**. Es a propósito: así nadie arrastra el efectivo de un día al siguiente.
- **La salida es cerrarla:**
  - la búsqueda de caja muestra «Primero cierra tu caja del dd/mm» con el botón **Cerrar mi caja anterior**;
  - `/caja/cierre` siempre abre primero la caja abierta **más antigua** y la cierra a ciegas, igual que cualquier otra;
  - al cerrarla, ese mismo día ya se puede cobrar con normalidad.
- **Promotoría** ve en su inicio la alerta crítica «La caja de … del dd/mm sigue abierta», con un enlace a *Cajas del día* de esa fecha.
- **Si la cajera no está** (enfermedad o renuncia):
  - otra cajera no puede cerrar esa caja, porque el cierre lo registra su propia cajera (trigger) y nadie más puede contar por ella;
  - mientras tanto, las demás cajeras cobran con normalidad, porque cada una tiene su propia caja;
  - Promotoría y Administración cuentan el efectivo de esa caja en presencia de la cajera cuando vuelva, o restablecen su clave y la acompañan a cerrar. El restablecimiento queda en la bitácora y en «Para revisar».

## Aprobación del cierre (Promotoría o Dirección)
- **Todo cierre** pasa por la bandeja (*Aprobaciones*). Nunca lo aprueba la cajera de esa caja, ni quien creó su cuenta o le restableció la clave en los últimos 30 días.
- **Sin diferencia:** se aprueba con un clic.
- **Con faltante o sobrante:**
  - la tarjeta sale primera, resaltada;
  - para aprobarla hay que escribir un comentario: qué se verificó y cómo se resolvió;
  - *Observar* deja el cierre OBSERVADO, con su motivo.
- *Aprobaciones › Cajas del día* muestra por cajera lo cobrado (efectivo y digital), su cierre con la diferencia y su depósito. El detalle de cada caja incluye los pagos, los cierres con el primer conteo y la explicación, el depósito con su verificación y las anulaciones posteriores al cierre.

## Reapertura
- La cajera la pide desde `/caja/cierre`, por ejemplo cuando llega tarde una familia que paga en efectivo. Solo procede si se cumplen las tres condiciones:
  - es la caja **del mismo día**;
  - no tiene depósito registrado;
  - la aprueba otra persona.
- Al reabrir se vuelve a contar. **El cierre anterior queda registrado tal cual**, con su diferencia: reabrir no «arregla» un faltante. Si el cierre tenía diferencia, la tarjeta de reapertura lo advierte.
- El cierre después de una reapertura **no es ciego** (la cajera ya vio el esperado del cierre anterior): queda marcado «Cierre tras reapertura», va primero en la bandeja, aprobarlo exige un comentario y Promotoría recibe una alerta.

## Anulaciones y devoluciones
- Un pago **digital** solo se puede anular (devolución o corrección) si Administración ya lo encontró en el banco. Un Yape que nunca llegó no sale de la conciliación con una devolución: queda como alerta.
- Al pedir una devolución se elige la causa. Si es «pago duplicado», el sistema exige que exista otro pago vigente de esas cuotas.
- Para aprobar una **devolución en efectivo**, quien aprueba llama al apoderado, marca «Hablé con el apoderado» y escribe el número al que llamó: debe ser un celular registrado de la familia. En la bitácora queda enmascarado. Una **corrección hacia otra familia** exige hablar con ambas familias.
- Toda devolución aprobada espera su **reembolso**, que registra Administración en *Conciliación* (nunca la cajera del pago):
  - digital: a la cuenta de origen, con el número de operación de la devolución;
  - efectivo: contra la firma de la nota de crédito impresa (trae espacio para la firma, el nombre y el DNI de quien recibe).
  - Hasta que se registre, Promotoría tiene una alerta **crítica**.
- Si se anula un pago en efectivo de una caja **ya cerrada**, el cierre no cambia y la anulación queda marcada «posterior al cierre».
- Una **corrección** en una caja cerrada no cambia el efectivo de esa caja: el pago de reemplazo entra en la misma caja.

## Verificación bancaria a ciegas (Administración)
- En *Conciliación bancaria* (`/conciliacion`), Administración **no ve** el número de operación ni el monto registrados. Para marcar **Encontrado** escribe lo que ve en el estado de cuenta: la operación, la fecha y el monto. El sistema compara:
  - el número se compara en su forma canónica (sin guiones, espacios ni ceros a la izquierda);
  - un pago se acepta hasta 3 días después de cobrado; un depósito, en su fecha;
  - si no coincide, no se guarda nada y queda en la bitácora; Promotoría ve cuántos intentos no coincidieron.
- «No aparece» exige una nota y es una alerta crítica para Promotoría.
- Nunca verifica quien cobró o depositó (trigger).
- Se marcan los números **parecidos** (iguales o a un carácter de otro de los últimos 90 días) y los depósitos **tardíos** (más de un día hábil después de la caja).
- Un mismo número de operación no se registra dos veces, ni en otro formato ni en otro medio digital; lo mismo para los vouchers de depósito.

## Alertas de Promotoría (inicio, «Para revisar»)
Se calculan al consultar, sin tareas programadas. Las críticas aparecen primero. Los días hábiles son de lunes a viernes (los feriados no se consideran).
- **Críticas:**
  - faltante o sobrante en un cierre por aprobar;
  - caja de un día anterior sin cerrar;
  - pago o depósito que no aparece en el banco;
  - pago digital sin verificar pasada la hora límite del día hábil siguiente al cobro;
  - efectivo sin depositar cuando ya pasó un día hábil completo, y depósito tardío;
  - depósito distinto de lo contado;
  - devolución aprobada sin reembolso registrado;
  - hueco en una serie de comprobantes, o una serie que no es de las configuradas;
  - una boleta o factura sin su pago, o una nota de crédito sin su anulación aprobada.
- **Atención:**
  - caja de hoy abierta pasada la hora límite (`hora-limite-cierre`, 19:00, incluida);
  - pagos digitales o depósitos sin verificar por más de `dias-sin-verificar` días, contados en horas;
  - efectivo del día hábil anterior sin depositar;
  - anulaciones de pago pendientes, con su monto;
  - todas las devoluciones en efectivo del día;
  - verificaciones bancarias de hoy que no coincidieron;
  - cierres tras reapertura y cierres observados.
- **Para saber:** los cierres sin diferencia que esperan aprobación y una **muestra al azar** de 3 verificaciones del día hábil anterior, con quién verificó y qué escribió, para compararlas con el banco.

Arriba de las alertas aparece **Hoy en caja**: lo cobrado hoy, cuánto fue en efectivo, cuánto digital (con su porcentaje) y cuántas cajas están abiertas o cerradas.
