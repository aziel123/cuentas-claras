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
- Al reabrir se vuelve a contar a ciegas. **El cierre anterior queda registrado tal cual**, con su diferencia: reabrir no «arregla» un faltante. Si el cierre tenía diferencia, la tarjeta de reapertura lo advierte.

## Anulaciones después del cierre
- Si se anula un pago en efectivo de una caja **ya cerrada**:
  - el cierre no cambia;
  - la anulación queda marcada «posterior al cierre»;
  - Promotoría ve la alerta «Devolución pendiente»;
  - el reembolso lo hace Administración desde el banco.
- Una **corrección** en una caja cerrada no cambia el efectivo de esa caja: el pago de reemplazo entra en la misma caja.

## Verificación bancaria (Administración)
- En *Conciliación bancaria* (`/conciliacion`), Administración marca cada pago digital (Yape, Plin, transferencia o tarjeta) y cada depósito como **Encontrado** o **No aparece** frente al estado de cuenta.
  - «No aparece» exige una nota y es una alerta crítica para Promotoría. Así se detecta un Yape inventado para quedarse con el efectivo, algo que el cierre solo no ve.
  - Nunca verifica quien cobró o depositó (trigger).
- Lo que lleva más de `cuentasclaras.caja.dias-sin-verificar` días (1 por defecto) aparece resaltado y como alerta.

## Alertas de Promotoría (inicio, «Para revisar»)
Se calculan al consultar, sin tareas programadas. Las críticas aparecen primero.
- **Críticas:**
  - faltante o sobrante en un cierre por aprobar;
  - caja de un día anterior sin cerrar;
  - pago o depósito que no aparece en el banco;
  - depósito distinto de lo contado;
  - hueco en una serie de comprobantes.
- **Atención:**
  - caja de hoy abierta pasada la hora límite (`hora-limite-cierre`, 19:00);
  - pagos digitales o depósitos sin verificar;
  - efectivo de días anteriores sin depositar;
  - anulaciones de pago pendientes, con su monto;
  - devoluciones posteriores al cierre;
  - cierres observados.
- **Para saber:** los cierres sin diferencia que esperan aprobación.

Arriba de las alertas aparece **Hoy en caja**: lo cobrado hoy, cuánto fue en efectivo, cuánto digital (con su porcentaje) y cuántas cajas están abiertas o cerradas.
