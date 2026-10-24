# Términos de servicio de la plataforma (contrato entre el proveedor y cada colegio)

> **BORRADOR · A REVISAR POR UN ABOGADO antes de firmar el primer contrato** (decisión 137). Es un esquema con el contenido
> que el sistema y su operación permiten cumplir (sección 13.1 de `docs/arquitectura/sprint-8-lista-para-vender.md`), no un
> texto revisado. Los plazos, montos, límites y la ley aplicable los decide el abogado. No incluye promesas cuantitativas
> («elimina el fraude», «cero pérdidas»): el DL 1044 exige poder probarlas.
>
> Entre corchetes, lo que se completa por cada colegio. «La plataforma» es el producto que se vende con el nombre comercial
> de `CC_MARCA_NOMBRE` (todavía provisional).

## 1. Partes
- **El proveedor:** [razón social del proveedor] (`CC_MARCA_EMPRESA`), RUC [RUC del proveedor] (`CC_MARCA_RUC`), con domicilio
  en [dirección], representado por [nombre y DNI].
- **El colegio:** [razón social], RUC [RUC], con domicilio fiscal en [dirección fiscal], nombre comercial [nombre del colegio],
  representado por [representante legal], DNI [DNI], según [poder o vigencia de poder].
- **Código del contrato:** [código]. Es el mismo que el operador escribe en el alta del colegio (`colegio.contrato_codigo`):
  enlaza la ficha del colegio en la plataforma con este contrato.
- **Titular de la cuenta de Promotoría:** [nombre], DNI [DNI], celular o correo [contacto]. Con ese DNI activa su cuenta en la
  reunión de puesta en marcha.

## 2. Objeto
El proveedor da al colegio acceso a la plataforma (los módulos contratados: [cobranza con controles antifraude; más
adelante, académico y comunicación]), el soporte, los respaldos y las actualizaciones, en una instalación [compartida /
dedicada] alojada por el proveedor.

## 3. Qué hace la plataforma y qué no hace
**Hace:** registra los cobros y emite el comprobante (con el OSE que el colegio contrate), avisa a la familia de cada pago,
exige la aprobación de otra persona para anular, descontar o cerrar una caja con diferencia, cierra la caja a ciegas,
concilia con el banco, deja cada operación en una bitácora que no se puede editar sin que se note, y alerta a la Promotoría.

**No hace (y el colegio se obliga a hacerlo):**
- **No reemplaza el control del colegio.** El colegio asigna los roles a personas distintas (quien cobra no aprueba), revisa
  las alertas y el resumen diario, y atiende los avisos de las familias.
- El colegio valida su **saldo inicial** con su contador antes de pasar a producción.
- El colegio hace a su nombre los trámites con el OSE, la pasarela de pagos, el banco y, si lo quiere propio, WhatsApp.
- El colegio decide sus pensiones, moras y reglamento conforme a la ley (el proveedor no da asesoría legal ni tributaria).
- La plataforma **no bloquea evaluaciones ni notas por deuda** (Código de Protección y Defensa del Consumidor e INDECOPI).

### 3.1 La Promotoría aplica directo; la espera y el freno (sprint 11, para revisión del abogado)
- Las acciones de la Promotoría del colegio se aplican directo, con su firma y aviso inmediato a las demás Promotorías y al
  correo externo de control; las sensibles piden además un código de un solo uso enviado a su celular registrado.
- Los cambios que mueven el dinero del colegio (cuenta bancaria, pago en línea, recaudación) y los que dan o quitan la
  Promotoría esperan **24 horas** antes de aplicarse. Durante la espera los puede **frenar** otra Promotoría del colegio con
  30 días o más en el rol, la persona afectada o un operador de la plataforma.
- **El operador de la plataforma interviene en el dinero del colegio solo para frenar, nunca para aplicar.** Lo hace con su
  sesión firmada y un motivo, y queda en la bitácora del colegio y en la de la plataforma.

## 4. Datos
Los datos de las familias, alumnos y personal son **del colegio**. El colegio es el **responsable del tratamiento** y el
proveedor, el **encargado**: el contrato de encargo de tratamiento (anexo A, `contrato-encargo-tratamiento-datos.md`) es
parte de estos términos.

## 5. Disponibilidad, mantenimiento y soporte
- Objetivo de disponibilidad: [99 % mensual], **sin compensación económica** en la primera etapa (MVP).
- Mantenimiento programado: después de las 21:00 o en fin de semana, con [48 horas] de aviso, unos minutos sin servicio
  (decisión 88). Urgencias de seguridad: en el momento, con aviso posterior.
- Respaldos: diarios y cifrados, fuera del proveedor de nube, con restauración de prueba mensual. Punto de recuperación de
  [24 horas] (respaldo propio) o de minutos (la recuperación a un punto en el tiempo de la base administrada). Tiempo de
  recuperación objetivo: [2 horas].
- Soporte: [canal: correo `CC_MARCA_SOPORTE_CORREO` y WhatsApp `CC_MARCA_SOPORTE_WHATSAPP`], de [lunes a viernes, 08:00 a
  18:00, hora de Lima]. Tiempo de primera respuesta: [4 horas hábiles].

## 6. Precio y pago (fuera de la plataforma)
- **La suscripción se cobra fuera de la plataforma** (decisión 128; `docs/operacion/suscripcion.md`): el proveedor emite
  una **factura electrónica mensual** con su propio sistema autorizado y el colegio paga por **transferencia** a la cuenta
  del proveedor. La plataforma nunca cobra al colegio ni mezcla el dinero de las familias con el del proveedor.
- Precio: [monto] al mes [más IGV], por [instalación / número de alumnos]. Alta y puesta en marcha: [monto o «incluidas»].
- Vencimiento: [día] de cada mes. Reajuste anual: [índice o porcentaje], con [30 días] de aviso.
- Mora: [interés legal o el que diga el abogado].

## 7. Suspensión
- Por falta de pago, el proveedor puede suspender el acceso del **personal** del colegio solo con **30 días de aviso por
  escrito**.
- **Nunca se suspenden los avisos de pago a las familias ni su acceso a sus comprobantes y a su estado de cuenta** mientras
  el colegio siga cobrando con la plataforma: las familias no pagan la deuda del colegio con el proveedor.
- Por un riesgo de seguridad grave (por ejemplo, credenciales comprometidas), la suspensión puede ser inmediata, solo de lo
  afectado y avisando el mismo día.

## 8. Fin del contrato
- Cualquiera de las partes puede terminarlo con [60 días] de aviso por escrito.
- **Exportación:** en los 30 días siguientes, el proveedor entrega los datos del colegio (un Excel por tabla), cifrados para
  la clave que indique el colegio.
- **Conservación:** después, los datos quedan **bloqueados, sin acceso**, durante el plazo de conservación tributaria que
  indique el contador del colegio (por defecto, [5 años], a confirmar; decisión 132), y luego se eliminan con un **acta** del
  responsable técnico del proveedor.

## 9. Responsabilidad
- El proveedor responde por los daños que cause por incumplir estos términos, hasta un límite de [lo pagado por el colegio en
  los últimos 12 meses], **salvo dolo o culpa grave**.
- El proveedor no responde por las decisiones del colegio, por el uso indebido de las cuentas por su personal, por la
  colusión entre personas del colegio que la plataforma detecta y avisa pero no puede impedir, ni por los servicios que el
  colegio contrate a su nombre (OSE, pasarela, banco).
- Fuerza mayor y caso fortuito según el Código Civil.

## 10. Otras cláusulas
- **Propiedad intelectual:** la plataforma es del proveedor; el colegio recibe una licencia de uso mientras dure el contrato.
  Los datos, el logo y la marca del colegio son del colegio.
- **Confidencialidad** de la información de la otra parte, también después del contrato.
- **Cambios del servicio:** con [30 días] de aviso; si reducen lo contratado, el colegio puede terminar sin penalidad.
- **Ley peruana y jurisdicción** de los jueces y tribunales de Lima [o arbitraje, según el abogado].
- **Comunicaciones:** por escrito a los correos de las partes.

## Anexos
- **A.** Contrato de encargo de tratamiento de datos personales (`contrato-encargo-tratamiento-datos.md`).
- **B.** Subencargados y flujo transfronterizo (anexo B del contrato de encargo).
- **C.** Matriz de controles: lo que la plataforma impide, lo que detecta y quién lo revisa (sale de
  `docs/estado-del-proyecto.md`).
- **D.** Riesgos residuales aceptados (como en el acta de conformidad del sprint 7), que el colegio marca «Entendido».

Firmas: [proveedor] · [colegio] · Lugar y fecha: [Lima, …]
