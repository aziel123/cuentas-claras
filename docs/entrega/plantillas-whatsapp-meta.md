# Plantillas de WhatsApp para enviar a Meta (versión 2: con el nombre del colegio)

> **Para enviar a Meta.** Sprint 8, tanda 3. Sale de `comunicacion.model.PlantillaMensaje` (la fuente única: `PlantillasMetaDocTest` falla si este documento y el código no dicen lo mismo). Decisiones 120 a 122 del sprint 8.
> El nombre comercial sigue siendo **provisional**: sale de `CC_MARCA_NOMBRE` y **no aparece en ninguna plantilla**. Va en el **nombre visible** del número de WhatsApp Business (lo aprueba Meta aparte), así que cambiar la marca no obliga a volver a aprobar estas plantillas.

## Cómo se cargan en el Administrador de WhatsApp
- **Categoría:** Utilidad (`UTILITY`) en todas: avisos de una operación que la familia o el personal ya tienen con el colegio. Ninguna es de marketing.
- **Idioma:** Español (`es`), el de `cuentasclaras.mensajeria.whatsapp.idioma-plantillas`.
- **Nombre:** exactamente el de la tabla (con `_v2`). Las versiones sin `_v2` (sprint 5) ya no se usan: no hace falta aprobarlas.
- **Cuerpo:** el texto tal cual, con sus variables `{{1}}`…`{{n}}` en orden. **`{{1}}` es siempre el nombre del colegio** (para los mensajes de la plataforma, la marca); lo pone el sistema, nadie lo escribe.
- **Encabezado y pie:** sin encabezado ni pie (el colegio va en `{{1}}`).
- **Botón:** las que dicen «Ver en el portal» llevan un botón de tipo *Visitar sitio web*, URL **estática** `https://app.<dominio>/familia`. Las de activación y verificación, URL **dinámica**: `https://app.<dominio>/activar/{{1}}` y `https://app.<dominio>/verificar/{{1}}` (el sistema manda el resto del enlace de un solo uso). Cambia `<dominio>` por el de la marca elegida.
- **Ejemplos:** Meta pide un valor de ejemplo por variable: van en la tabla de cada plantilla (son ficticios).
- Ninguna menciona evaluaciones, notas, exámenes ni constancias (las de libretas solo dicen que la libreta está en el portal), ni amenaza con consecuencias (INDECOPI; lo revisa `PlantillasMensajeTest`). Los mensajes al personal llevan solo cifras y tipos fijos (sin nombres de familias).

| # | Nombre en Meta | Para | Variables | Botón |
|---|---|---|---|---|
| 1 | `cc_pago_registrado_v2` | Familias | 7 | «Ver en el portal» (`/familia`) |
| 2 | `cc_pago_anulado_v2` | Familias | 6 | «Ver en el portal» (`/familia`) |
| 3 | `cc_descuento_aprobado_v2` | Familias | 4 | «Ver en el portal» (`/familia`) |
| 4 | `cc_activacion_v2` | Familias y personal | 2 | URL dinámica `/activar/{{1}}` |
| 5 | `cc_contacto_cambiado_v2` | Familias | 2 | — |
| 6 | `cc_huella_v2` | Personal del colegio | 6 | — |
| 7 | `cc_renovacion_v2` | Familias | 5 | «Ver en el portal» (`/familia`) |
| 8 | `cc_renovacion_registrada_v2` | Familias | 4 | «Ver en el portal» (`/familia`) |
| 9 | `cc_aviso_atendido_v2` | Familias | 2 | «Ver en el portal» (`/familia`) |
| 10 | `cc_recordatorio_v2` | Familias | 5 | «Ver en el portal» (`/familia`) |
| 11 | `cc_cuota_vencida_v2` | Familias | 4 | «Ver en el portal» (`/familia`) |
| 12 | `cc_verificacion_v2` | Familias | 3 | URL dinámica `/verificar/{{1}}` |
| 13 | `cc_apoderado_agregado_v2` | Familias | 3 | «Ver en el portal» (`/familia`) |
| 14 | `cc_contacto_por_verificar_v2` | Familias | 4 | «Ver en el portal» (`/familia`) |
| 15 | `cc_feriado_propuesto_v2` | Personal del colegio | 4 | — |
| 16 | `cc_resumen_diario_v2` | Personal del colegio | 13 | — |
| 17 | `cc_alerta_promotoria_v2` | Personal del colegio | 3 | — |
| 18 | `cc_alerta_mas_v2` | Personal del colegio | 2 | — |
| 19 | `cc_contacto_personal_cambiado_v2` | Personal del colegio | 2 | — |
| 20 | `cc_conector_vigente_v2` | Personal del colegio | 6 | — |
| 21 | `cc_colegio_en_produccion_v2` | Personal del colegio | 3 | — |
| 22 | `cc_enlace_titular_reenviado_v2` | Titular, correo institucional y correo de control | 3 | — |
| 23 | `cc_contacto_titular_corregido_v2` | Titular, correo institucional y correo de control | 4 | — |
| 24 | `cc_ficha_colegio_corregida_v2` | Titular, correo institucional y correo de control | 3 | — |
| 25 | `cc_correo_control_propuesto_v2` | Promotoría, correo institucional y correo de control | 3 | — |
| 26 | `cc_correo_control_cambiado_v2` | Promotoría, correo institucional y correo de control | 3 | — |
| 27 | `cc_asistencia_v2` | Familias | 3 | «Ver asistencia» (`/familia/asistencia`) |
| 28 | `cc_asistencia_corregida_v2` | Familias | 6 | «Ver asistencia» (`/familia/asistencia`) |
| 29 | `cc_comunicados_v2` | Familias | 3 | «Ver comunicados» (`/familia/comunicados`) |
| 30 | `cc_comunicado_urgente_v2` | Familias | 3 | «Ver comunicados» (`/familia/comunicados`) |
| 31 | `cc_libreta_v1` | Familias | 3 | «Ver libretas» (`/familia/libretas`) |
| 32 | `cc_libreta_corregida_v1` | Familias | 3 | «Ver libretas» (`/familia/libretas`) |

## 1. `cc_pago_registrado_v2` · Registramos su pago

```text
{{1}}: registramos su pago de {{2}} por {{3}}. Comprobante {{4}} del {{5}}, {{6}}, registrado por {{7}}. Si no reconoce este pago, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | S/ 450.00 |
| `{{3}}` | Pensión de octubre de Mateo |
| `{{4}}` | B001-00000123 |
| `{{5}}` | 12/10/2026 |
| `{{6}}` | efectivo |
| `{{7}}` | Lucía R. |

## 2. `cc_pago_anulado_v2` · Se anuló un pago

```text
{{1}}: se anuló el pago {{2}} de {{3}} ({{4}}). Motivo: {{5}}. Aprobó: {{6}}. Si usted no lo pidió, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | B001-00000123 |
| `{{3}}` | S/ 450.00 |
| `{{4}}` | Pensión de octubre de Mateo |
| `{{5}}` | pago duplicado por error |
| `{{6}}` | Jorge S. |

## 3. `cc_descuento_aprobado_v2` · Se aprobó un descuento

```text
{{1}}: se aprobó un descuento de {{2}} en {{3}}. Aprobó: {{4}}. Si usted no lo solicitó, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 10 % |
| `{{3}}` | Pensión de diciembre de Valeria |
| `{{4}}` | María Elena T. |

## 4. `cc_activacion_v2` · Activa tu acceso

```text
{{1}}: para activar su acceso al portal use este enlace (vence el {{2}}). Nadie del colegio se lo pedirá.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 14/10/2026 18:00 |

## 5. `cc_contacto_cambiado_v2` · Cambió su contacto en el colegio

```text
{{1}}: este {{2}} dejó de recibir los avisos de pagos del colegio porque se registró otro. Si usted no lo pidió, avísenos en el colegio o desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | número |

## 6. `cc_huella_v2` · Huella diaria de la bitácora

```text
{{1}}: huella del {{2}}: evento {{3}}, código {{4}}. Huella anterior: {{5}}. Bitácora verificada: {{6}}. Guarde este mensaje y compárelo con el de ayer.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 11/10/2026 |
| `{{3}}` | 4821 |
| `{{4}}` | 7F3A-91C2 |
| `{{5}}` | C0D4-1E77 |
| `{{6}}` | sí |

## 7. `cc_renovacion_v2` · Confirme si su hijo continúa el próximo año

```text
{{1}}: confirme en el portal si {{2}} continúa en {{3}} el {{4}}. Puede responder hasta el {{5}}. Si no responde, no se genera ningún cobro.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Mateo |
| `{{3}}` | 6.° de primaria |
| `{{4}}` | año 2027 |
| `{{5}}` | 30/11/2026 |

## 8. `cc_renovacion_registrada_v2` · Registramos su respuesta sobre el próximo año

```text
{{1}}: registramos en el colegio que {{2}} {{3}} el {{4}}. Si usted no lo pidió, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Mateo |
| `{{3}}` | continúa |
| `{{4}}` | año 2027 |

## 9. `cc_aviso_atendido_v2` · Respondimos su aviso

```text
{{1}}: respondimos el aviso que nos envió el {{2}}. Vea la respuesta en el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 10/10/2026 |

## 10. `cc_recordatorio_v2` · Recordatorio de vencimiento

```text
{{1}}: le recordamos que {{2}} vence el {{3}} ({{4}}). Puede pagar en línea, en el banco con el código {{5}} o en caja. Si ya pagó, ignore este mensaje.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | la pensión de noviembre de Mateo |
| `{{3}}` | 30/11/2026 |
| `{{4}}` | S/ 450.00 |
| `{{5}}` | 000123 |

## 11. `cc_cuota_vencida_v2` · Tiene un pago pendiente

```text
{{1}}: {{2}} venció el {{3}} ({{4}}). Puede pagar en línea, en el banco o en caja. Si ya pagó, ignore este mensaje.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | la pensión de octubre de Mateo |
| `{{3}}` | 31/10/2026 |
| `{{4}}` | S/ 450.00 |

## 12. `cc_verificacion_v2` · Confirme su contacto en el colegio

```text
{{1}}: confirme que este {{2}} es suyo para recibir los avisos de pagos del colegio. Use este enlace (vence el {{3}}). Si usted no lo pidió, no lo use y avísenos.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | número |
| `{{3}}` | 14/10/2026 18:00 |

## 13. `cc_apoderado_agregado_v2` · Se agregó un apoderado a su familia

```text
{{1}}: se registró a {{2}} como apoderado(a) de su familia, con el contacto {{3}}. Si no lo reconoce, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Pedro Flores |
| `{{3}}` | +51 9•• ••• 678 |

## 14. `cc_contacto_por_verificar_v2` · Se registró un contacto nuevo en su familia

```text
{{1}}: se registró el {{2}} {{3}} para {{4}}. Recibirá los avisos cuando lo confirme. Si no lo reconoce, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | número |
| `{{3}}` | +51 9•• ••• 678 |
| `{{4}}` | la familia Flores Díaz |

## 15. `cc_feriado_propuesto_v2` · Día no laborable por aprobar

```text
{{1}}: {{2}} propuso el {{3}} como día no laborable ({{4}}). No cuenta hasta que lo apruebe otra persona de Promotoría o Dirección.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Jorge S. |
| `{{3}}` | 08/12/2026 |
| `{{4}}` | Día del colegio |

## 16. `cc_resumen_diario_v2` · Resumen del día

```text
{{1}} · {{2}}. Cobrado hoy: {{3}} en {{4}} pagos ({{5}} digital). Efectivo: {{6}}. Cajas: {{7}}. Cobrado en el mes: {{8}}. Vencido por cobrar: {{9}}. Por aprobar: {{10}}. Alertas críticas: {{11}}. Huella 19:00: {{12}}. Llamadas de control: {{13}}. Si algo no te cuadra, revisa el panel.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 12/10/2026 |
| `{{3}}` | S/ 2,350.00 |
| `{{4}}` | 9 |
| `{{5}}` | S/ 900.00 |
| `{{6}}` | S/ 1,450.00 |
| `{{7}}` | 2 cerradas, 0 con diferencia |
| `{{8}}` | S/ 38,200.00 |
| `{{9}}` | S/ 5,400.00 |
| `{{10}}` | 3 |
| `{{11}}` | 0 |
| `{{12}}` | 7F3A-91C2 |
| `{{13}}` | 1 de 3 hechas |

## 17. `cc_alerta_promotoria_v2` · Alerta del colegio

```text
{{1}}: {{2}} ({{3}}). Revísalo en el sistema.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Caja cerrada con diferencia |
| `{{3}}` | S/ 50.00 |

## 18. `cc_alerta_mas_v2` · Más alertas del colegio

```text
{{1}}: hoy hay {{2}} alertas más. Revísalas en el panel.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 3 |

## 19. `cc_contacto_personal_cambiado_v2` · Cambió el contacto de tu cuenta

```text
{{1}}: este {{2}} dejó de recibir los mensajes de tu cuenta porque se registró otro. Si no lo pediste, avisa hoy a Promotoría.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | número |

## 20. `cc_conector_vigente_v2` · Cambió un conector del colegio

```text
{{1}}: cambió {{2}} a {{3}} ({{4}}). Lo propuso {{5}} y lo aprobó {{6}}. Si no lo reconoces, avisa hoy al contador y revisa la conciliación.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | la cuenta bancaria |
| `{{3}}` | BCP |
| `{{4}}` | termina en 1234 |
| `{{5}}` | Rosa M. |
| `{{6}}` | María Elena T. |

## 21. `cc_colegio_en_produccion_v2` · El colegio empezó a cobrar

```text
{{1}}: el colegio pasó a producción el {{2}} con la firma de {{3}}. Desde ahora se registran los pagos y salen los avisos a las familias.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 01/03/2027 |
| `{{3}}` | María Elena T. |

## 22. `cc_enlace_titular_reenviado_v2` · Se reenvió el enlace del titular

> Correcciones del sprint 8 (S8-A1). Al titular (a su contacto registrado), al correo institucional del colegio y al correo externo de control, en la misma transacción del reenvío.

```text
{{1}}: el operador {{2}} de la plataforma reenvió el enlace para activar la cuenta del titular a {{3}}. Si el titular no lo pidió, avisa hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.ana |
| `{{3}}` | WhatsApp ***-***-041 |

## 23. `cc_contacto_titular_corregido_v2` · Cambió el contacto del titular

> Correcciones del sprint 8 (S8-A1). Lo propone un operador y lo aplica otro. Al contacto ANTERIOR del titular, al correo institucional y al correo externo de control.

```text
{{1}}: los operadores {{2}} y {{3}} de la plataforma cambiaron el contacto del titular antes de que active su cuenta: su enlace va ahora a {{4}}. Si el titular no lo pidió, avisa hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.ana |
| `{{3}}` | operador.luis |
| `{{4}}` | WhatsApp ***-***-112 |

## 24. `cc_ficha_colegio_corregida_v2` · Se corrigió la ficha del colegio

> Correcciones del sprint 8 (S8-M5). Al titular, al correo institucional (el anterior y el nuevo, si cambió) y al correo externo de control.

```text
{{1}}: el operador {{2}} de la plataforma corrigió la ficha del colegio ({{3}}). Si no lo reconoces, avisa hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.ana |
| `{{3}}` | razón social y dirección |

## 25. `cc_correo_control_propuesto_v2` · Se propuso cambiar el correo de control

> Correcciones del sprint 8 (S8-A2). En un colegio en producción: a toda la Promotoría, al correo institucional y al correo de control ANTERIOR, antes de que el cambio surta efecto.

```text
{{1}}: el operador {{2}} de la plataforma propuso que el correo externo de control sea {{3}}. No cambia hasta que lo apruebe una persona de Promotoría del colegio. Si no lo reconoces, avisa hoy a la Promotoría.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.ana |
| `{{3}}` | c***@estudio-contable.pe |

## 26. `cc_correo_control_cambiado_v2` · Cambió el correo de control

> Correcciones del sprint 8 (S8-A2). A toda la Promotoría, al correo institucional y a los correos de control anterior y nuevo.

```text
{{1}}: el correo externo de control del colegio ahora es {{2}} (lo cambió {{3}}). Desde hoy recibe la huella de la bitácora y el resumen diario. Si no lo reconoces, avisa hoy a la Promotoría y al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | c***@estudio-contable.pe |
| `{{3}}` | María Elena T. (Promotoría) |

## 27. `cc_asistencia_v2` · Asistencia de hoy

> Sprint 9, tanda 2 (sección 11 del diseño). Un aviso por familia y día, a la hora fija del colegio, con los hermanos juntos.
> Solo el primer nombre de cada hijo y estados fijos: nunca montos ni palabras de cobranza (lo exige también la base). El
> botón lleva a `/familia/asistencia`, nunca al estado de cuenta. Por WhatsApp solo con el «sí» explícito de la familia.

```text
{{1}}: asistencia del {{2}}. {{3}}. Si hay un error o desea justificar una falta, ingrese al portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | lunes 15/03/2027 |
| `{{3}}` | Mateo: falta. Lucía: llegó tarde (08:12) |

## 28. `cc_asistencia_corregida_v2` · Se corrigió la asistencia

> Sprint 9, tanda 2. Una corrección después del aviso (del día, aprobada por Dirección, una justificación de la familia o
> su reversión): la familia vuelve a ser el auditor. Quien registró va con el nombre y la inicial del apellido.

```text
{{1}}: se corrigió la asistencia de {{2}} del {{3}}: ahora figura {{4}} (antes: {{5}}). Lo registró {{6}}. Si no lo reconoce, avísenos desde el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Mateo |
| `{{3}}` | 15/03/2027 |
| `{{4}}` | presente |
| `{{5}}` | falta |
| `{{6}}` | Prof. Ana T. |

## 29. `cc_comunicados_v2` · Comunicados del colegio

> Sprint 9, tanda 3 (sección 11 del diseño). Un resumen por familia y día, a la hora del colegio, al contacto principal
> (con tope mensual): cuántos comunicados nuevos y por confirmar, y de quién. SIN títulos ni texto (son texto libre de una
> persona: Meta lo clasificaría como marketing); se leen y se confirman en el portal. Nunca montos ni palabras de cobranza.

```text
{{1}}: tiene {{2}} en el portal, de {{3}}. Léalos y confirme su lectura allí.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 2 comunicados nuevos y 1 por confirmar |
| `{{3}}` | Dirección y Prof. Ana T. |

## 30. `cc_comunicado_urgente_v2` · Comunicado urgente

> Sprint 9, tanda 3. Solo Dirección o Promotoría envían urgentes (una suspensión de clases, un simulacro): sale al momento,
> entre las 07:00 y las 20:00, con el título que pasó por el filtro de contenido. Si Meta lo clasifica como marketing, el
> urgente sale sin título con `cc_comunicados_v2` (sección 15 del diseño).

```text
{{1}}: {{2}} le envió un comunicado urgente: «{{3}}». Léalo en el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Dirección |
| `{{3}}` | Suspensión de clases del martes 16 |

## 31. `cc_libreta_v1` · Libreta disponible

> Sprint 10, tanda 3 (sección 3.7 del diseño). Uno por familia y periodo, con los hermanos juntos, cuando el sistema emite
> la libreta al cerrar el periodo. Nace en la v1 (ya con el colegio en `{{1}}`). Sin notas, conclusiones ni montos: la
> libreta se ve en el portal. La reciben TODAS las familias, también la que debe (INDECOPI: la libreta no se retiene).
> Por WhatsApp solo con el «sí» del apoderado a la finalidad «Avisos de libretas»; si no, por correo.

```text
{{1}}: la libreta del {{2}} de {{3}} ya está en el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | I bimestre |
| `{{3}}` | Mateo y Valeria |

## 32. `cc_libreta_corregida_v1` · Se corrigió la libreta

> Sprint 10, tanda 3. Cuando una corrección o una nota tardía aprobada (o una reemisión) da la versión siguiente de la
> libreta. El portal muestra todas las versiones y qué cambió; el mensaje no lo dice (sin notas).

```text
{{1}}: se corrigió la libreta del {{2}} de {{3}}. Revise el detalle en el portal.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | I bimestre |
| `{{3}}` | Lucas |

## 33. `cc_cambio_estado_propuesto_v2` · Cambio propuesto en el acceso del colegio

> Sprint 11, tanda 1. Un operador propone dar de baja, suspender o reactivar el colegio, o bloquear la facultad del titular. Va al titular, a la Promotoría, al correo institucional, al de control y a los operadores.

```text
{{1}}: el operador {{2}} de la plataforma propone {{3}}. Motivo: {{4}}. Todavía no cambia nada: lo debe aprobar otro operador. Si cree que es un error, escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.pruebas |
| `{{3}}` | suspender el acceso del personal desde el 01/12/2026 |
| `{{4}}` | Falta de pago: contrato CT-001 vencido desde agosto |

## 34. `cc_cambio_estado_aprobado_v2` · Cambio aprobado en el acceso del colegio

> Sprint 11, tanda 1. Otro operador aprueba el cambio; la base fija la fecha en que rige.

```text
{{1}}: los operadores {{4}} de la plataforma aprobaron {{2}}, que rige desde el {{3}}. Hasta esa fecha todo funciona. Si cree que es un error, escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | suspender el acceso del personal |
| `{{3}}` | 01/12/2026 |
| `{{4}}` | operador.uno y operador.dos |

## 35. `cc_cambio_estado_resuelto_v2` · Se dejó sin efecto un cambio en el colegio

> Sprint 11, tanda 1. Un operador rechaza o anula el cambio propuesto.

```text
{{1}}: el operador {{4}} de la plataforma {{3}} el cambio propuesto ({{2}}). No cambia nada.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | suspender el acceso del personal |
| `{{3}}` | anuló |
| `{{4}}` | operador.dos |

## 36. `cc_cambio_estado_aplicado_v2` · Cambió el acceso del colegio

> Sprint 11, tanda 1. El proceso aplica el cambio en su fecha.

```text
{{1}}: desde el {{3}}, {{2}}. Para cualquier consulta, escriba al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | el acceso del personal quedó en pausa |
| `{{3}}` | 01/12/2026 |

## 37. `cc_cambio_estado_en_espera_v2` · Un cambio del colegio espera

> Sprint 11, tanda 1. El cambio espera que el colegio ponga en orden su caja, sus comprobantes o sus libretas (alerta diaria).

```text
{{1}}: {{2}} espera que el colegio resuelva lo siguiente: {{3}}. Mientras tanto no se aplica.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | la pausa del acceso del personal |
| `{{3}}` | 2 cierres por revisar, 1 comprobante rechazado |

## 38. `cc_cambio_estado_proximo_v2` · Recordatorio de un cambio en el colegio

> Sprint 11, tanda 1. Recordatorio a 7 días y a 1 día de la fecha en que rige.

```text
{{1}}: recuerde que {{2}} rige desde el {{3}}. Si cree que es un error, escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | la pausa del acceso del personal |
| `{{3}}` | 01/12/2026 |

## 39. `cc_objecion_titular_v2` · El titular objetó un cambio del colegio

> Sprint 11, tanda 1. El titular objeta una baja o una suspensión.

```text
{{1}}: el titular {{2}} objetó {{3}}. No se aplica hasta que otro operador responda la objeción.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | María Torres |
| `{{3}}` | la pausa del acceso del personal |

## 40. `cc_objecion_respondida_v2` · Se respondió la objeción del titular

> Sprint 11, tanda 1. Un operador que no propuso responde la objeción del titular.

```text
{{1}}: el operador {{2}} de la plataforma respondió la objeción sobre {{3}}: {{4}}
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | operador.dos |
| `{{3}}` | la pausa del acceso del personal |
| `{{4}}` | Revisamos el pago y el contrato sigue vencido. |

## 41. `cc_facultad_titular_usada_v2` · El titular cambió Promotoría o Dirección

> Sprint 11, tanda 1. El titular da o quita Promotoría o Dirección sin segunda aprobación (facultad del titular). Va al correo de control, a toda la Promotoría, a la cuenta que recibe o pierde el rol y a los operadores.

```text
{{1}}: el titular {{2}} {{3}} {{4}} a la cuenta {{5}} sin segunda aprobación (facultad del titular). Si no lo reconoce, escriba hoy al proveedor y a la Promotoría.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | María Torres |
| `{{3}}` | dio |
| `{{4}}` | Dirección |
| `{{5}}` | diana.rojas |

## 42. `cc_codigo_facultad_v2` · Su código para dar o quitar Promotoría o Dirección

> Sprint 11, tanda 1. El código de 6 dígitos de cada uso de la facultad del titular. Va solo al titular, a su contacto registrado; ni el mensaje ni la base lo guardan (lo calcula el envío).

```text
{{1}}: su código para dar o quitar Promotoría o Dirección es {{2}}. Vence a las {{3}}. Si usted no lo pidió, escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 482915 |
| `{{3}}` | 10:42 |

## 43. `cc_codigo_promotoria_v2` · Su código para una acción en el colegio

> Sprint 11, tanda 1c (decisión 216, A1c-5). El código de 6 dígitos de una acción sensible de Promotoría, atado a esa acción (el texto dice qué autoriza). Va solo a la Promotoría que lo pidió, a su WhatsApp registrado; la base guarda solo su SHA-256.

```text
{{1}}: código {{2}} para: {{3}}. Vence a las {{4}}. Si usted no lo pidió, no lo comparta y escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 482913 |
| `{{3}}` | Dar Promotoría a j.perez |
| `{{4}}` | 10:42 |

## 44. `cc_codigo_contacto_v2` · Confirme el cambio de su celular o correo

> Sprint 11, tanda 1c (A1c-1). El cambio del celular o el correo de una cuenta de Promotoría o Dirección lo confirma su dueña desde su contacto ANTERIOR, con este código (o lo aplican dos operadores).

```text
{{1}}: código {{2}} para confirmar: {{3}}. Vence a las {{4}}. Si usted no lo pidió, no lo comparta y avise hoy al colegio.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | 305118 |
| `{{3}}` | el celular de diana.rojas pasa a ···0712 |
| `{{4}}` | 10:42 |

## 45. `cc_accion_directa_v2` · Una Promotoría aplicó un cambio directo

> Sprint 11, tanda 1c (sección 3.16, A1c-8). Cada acción que una Promotoría aplica directo (sin segunda aprobación) avisa al momento a todas las Promotorías (también a quien la hizo) y al correo externo de control.

```text
{{1}}: {{2}} aplicó directo: {{3}}. Si no lo reconoce, cambie su clave y avise hoy al colegio y al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | María Torres |
| `{{3}}` | Plan de pensiones 2027 aprobado |

## 46. `cc_directa_en_espera_v2` · Un cambio espera 24 horas

> Sprint 11, tanda 1c (decisión 218, A1c-2 y A1c-3). La cuenta bancaria, la pasarela o la recaudación que aprobó directo una Promotoría, y dar o quitar Promotoría, esperan 24 horas: las puede frenar otra Promotoría antigua, la persona afectada o un operador.

```text
{{1}}: {{2}} pidió {{3}}. Se aplica el {{4}} si nadie lo frena. Si no lo reconoce, frénelo desde el sistema o escriba hoy al proveedor.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | María Torres |
| `{{3}}` | cambiar la cuenta bancaria a BCP ···2022 |
| `{{4}}` | 26/10/2026 10:42 |

## 47. `cc_directa_frenada_v2` · Se frenó un cambio en espera

> Sprint 11, tanda 1c. Un cambio que esperaba sus 24 horas se frenó (con el motivo de quien lo frenó).

```text
{{1}}: {{2}} frenó {{3}}. Motivo: {{4}}. No se aplica: se puede volver a pedir.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | Jorge Pérez |
| `{{3}}` | cambiar la cuenta bancaria a BCP ···2022 |
| `{{4}}` | No reconocemos esta cuenta |

## 48. `cc_conciliacion_bloqueada_v2` · Alerta: confirmación rechazada

> Sprint 11, tanda 1c (A1c-4). En los 30 días siguientes a un cambio de cuenta bancaria o de recaudación, quien lo propuso o lo aprobó no confirma el extracto ni el lote: si lo intenta, sale esta alerta CRÍTICA.

```text
{{1}}: {{2}} intentó confirmar {{3}} después de cambiar la cuenta del colegio. Se rechazó: la confirma otra persona. Revíselo hoy.
```

| Variable | Ejemplo (ficticio) |
|---|---|
| `{{1}}` | Colegio San Martín (ejemplo) |
| `{{2}}` | María Torres |
| `{{3}}` | el extracto del 25/10/2026 |

## Después de la aprobación
1. Anotar en `docs/estado-del-proyecto.md` la fecha de aprobación de cada plantilla.
2. Con el número de WhatsApp Business de la plataforma verificado, cargar su id y su token en Secret Manager (`whatsapp-token`, `whatsapp-secreto-app`, `whatsapp-token-verificacion`) y `WHATSAPP_NUMERO_ID` en `/etc/cc/instalacion.env` (docs/operacion/despliegue-gcp.md).
3. Probar con un número de prueba fuera de producción (`permitir-real-fuera-de-prod` y `numeros-de-prueba`) antes de activar `MENSAJERIA_WHATSAPP_PROVEEDOR=WHATSAPP_CLOUD` en producción.
