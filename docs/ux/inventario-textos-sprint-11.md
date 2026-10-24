# Inventario de textos · Sprint 11, tanda 1b, F-D7 «menos texto» (para revisión del usuario)

> Generado el 24-oct-2026 desde las plantillas de `main` + sprint 11 (textos de 60 caracteres o más en `<p>`, `<li>` y `<small>` de las
> pantallas principales). **La columna «Propuesta» es automática** (por palabras de control: anular, aprobar, bitácora, comprobante,
> verificar, cuenta, monto…) y la revisan el usuario y `investigador-ux` **antes** de implementar. Regla: lo no crucial se quita; lo
> crucial pasa a un tooltip accesible; **lo que el aprobador debe verificar (montos, números de cuenta, CCI, comprobante) queda
> visible**; un control antifraude nunca se borra, a lo sumo pasa a tooltip. Las expresiones `${…}` aparecen tal como están en la plantilla.

Marca en «Decisión» con ✔ (acepto la propuesta), Q (quitar), T (tooltip) o V (dejar visible).

**Total:** 151 textos en 47 plantillas.

| # | Plantilla | Línea | Texto actual | Propuesta | Decisión |
|---|---|---|---|---|---|
| 1 | inicio/*.html (las 6) | — | Tarjeta «Cómo te cuida el sistema» (4 viñetas: boleta y aviso, nada se borra, bitácora, sesión de 30 min) | Se elimina la tarjeta. Queda un ícono «i» junto al título del inicio con 1 línea: «Cada pago queda con su boleta y aviso a la familia; nada se borra.» La de la sesión se quita | |
| 2 | aprobaciones/bandeja.html | — | Subtítulo «Cambios que una sola persona no puede hacer: …» | Se elimina. Tooltip «i» junto al título: «Quien pide un cambio no lo aprueba.» (control) | |
| 3 | aprobaciones/bandeja.html | — | Tarjeta de cambio de conector: párrafo de advertencia, 5 viñetas, motivo y quién la pidió | Título «Cuenta bancaria → BCP ···2022»; 1 o 2 datos (qué cambia, quién y cuándo); ⚠ con tooltip «Confirma el número con el banco antes de aprobar»; número de cuenta y CCI completos **visibles**; titular, prueba de conexión y motivo en «Ver detalle»; Aprobar y Rechazar | |
| 4 | inicio/administracion.html | 10 | Configuras pensiones, cargas alumnos y familias, concilias el banco y sacas reportes. | Tooltip «i» (control antifraude: no se borra) | |
| 5 | inicio/administracion.html | 21 | Sube el extracto del banco: el sistema empareja solo y te muestra las diferencias. Conciliar el banco | Tooltip «i» (control antifraude: no se borra) | |
| 6 | inicio/administracion.html | 25 | Pagos en línea por revisar, de hoy y vencidos. Ver pagos en línea | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 7 | inicio/administracion.html | 29 | Fichas de alumnos y familias, y matrícula en cada año. Ver alumnos | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 8 | inicio/administracion.html | 39 | La deuda de cada alumno la genera el sistema desde las pensiones: nadie decide montos a mano. | Tooltip «i» (control antifraude: no se borra) | |
| 9 | inicio/administracion.html | 41 | La conciliación cruza cada pago registrado con el movimiento del banco. | Tooltip «i» (control antifraude: no se borra) | |
| 10 | inicio/apoderado.html | 10 | Mira lo que debes, paga desde el celular con Yape o tarjeta y descarga tus boletas. | Tooltip «i» (control antifraude: no se borra) | |
| 11 | inicio/apoderado.html | 20 | Elige las cuotas, paga en la página segura de la pasarela y recibe tu boleta al instante. No tienes que ir al colegio. | Tooltip «i» (control antifraude: no se borra) | |
| 12 | inicio/apoderado.html | 27 | Cada pago que hagas te llega por aviso, con el monto y el número de boleta. | Tooltip «i» (control antifraude: no se borra) | |
| 13 | inicio/apoderado.html | 28 | Si ves un cobro que no reconoces, avisa al colegio: tu estado de cuenta es la mejor forma de control. | Tooltip «i» (control antifraude: no se borra) | |
| 14 | inicio/caja.html | 10 | Registra los pagos presenciales y cierra tu caja al final del día. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 15 | inicio/caja.html | 29 | Por ejemplo: «quispe» o «7845». Escribe al menos 2 letras o números. | Tooltip «i» (control antifraude: no se borra) | |
| 16 | inicio/caja.html | 38 | Cada pago emite su boleta al momento: tienes respaldo de todo lo que cobras. | Tooltip «i» (control antifraude: no se borra) | |
| 17 | inicio/caja.html | 39 | Si te equivocas, pides la anulación a Dirección: nadie borra pagos. | Tooltip «i» (control antifraude: no se borra) | |
| 18 | inicio/caja.html | 40 | Cierras a ciegas: cuentas el efectivo sin ver lo registrado, y el sistema compara tu conteo el mismo día. | Tooltip «i» (control antifraude: no se borra) | |
| 19 | inicio/director.html | 11 | Apruebas anulaciones, descuentos y becas y cierres de caja, y revisas la bitácora de auditoría. | Tooltip «i» (control antifraude: no se borra) | |
| 20 | inicio/director.html | 32 | 0} ? 'item-critico'"> 3 solicitudes esperan tu aprobación. Quien pide un cambio no lo aprueba: lo decides tú u otra persona de Promotoría. 0} ? 'btn-primario' : 'btn-s… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 21 | inicio/director.html | 40 | Cajas del día: quién abrió, quién cerró y con qué diferencia. Ver cajas del día | Tooltip «i» (control antifraude: no se borra) | |
| 22 | inicio/director.html | 66 | Sin docente, nadie registra su asistencia. Propón la asignación y otra persona la aprueba. | Tooltip «i» (control antifraude: no se borra) | |
| 23 | inicio/director.html | 78 | Caja no puede anular ni dar descuentos: te lo pide a ti y tú decides. | Tooltip «i» (control antifraude: no se borra) | |
| 24 | inicio/director.html | 79 | Cada aprobación o rechazo queda registrado con tu nombre y el motivo. | Tooltip «i» (control antifraude: no se borra) | |
| 25 | inicio/director.html | 80 | Puedes crear usuarios del personal, pero no cambiar a Promotoría ni a otra Dirección. | Tooltip «i» (control antifraude: no se borra) | |
| 26 | inicio/docente.html | 10 | Tus secciones y la lista de tus alumnos. Pronto, asistencia, notas y comunicados desde el celular. | Tooltip «i» (control antifraude: no se borra) | |
| 27 | inicio/docente.html | 21 | Las secciones donde eres tutor, docente o auxiliar, con los nombres de tus alumnos. Ver mis secciones | Tooltip «i» (control antifraude: no se borra) | |
| 28 | inicio/promotor.html | 12 | Cuánto entra cada día, la morosidad y las alertas de caja, sin esperar al fin de mes. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 29 | inicio/promotor.html | 55 | 1 anulación de pago por S/ 450.00 espera aprobación. Revisar | Tooltip «i» (control antifraude: no se borra) | |
| 30 | inicio/promotor.html | 92 | Claves restablecidas, usuarios creados y cambios de roles de los últimos 7 días. Si algo no lo reconoces, revísalo con quien lo hizo. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 31 | inicio/promotor.html | 118 | Cada sol cobrado queda registrado con su boleta y el apoderado recibe un aviso. | Tooltip «i» (control antifraude: no se borra) | |
| 32 | inicio/promotor.html | 119 | Nadie puede borrar un pago: solo anularlo con motivo y aprobación de Dirección. | Tooltip «i» (control antifraude: no se borra) | |
| 33 | inicio/promotor.html | 120 | La bitácora de auditoría guarda quién hizo qué, y puedes comprobar que nadie la alteró. | Tooltip «i» (control antifraude: no se borra) | |
| 34 | aprobaciones/bandeja.html | 9 | Cambios que una sola persona no puede hacer: cierres y reaperturas de caja, anulaciones y correcciones de pagos, descuentos y becas, retiros, celulares y correos de ap… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 35 | aprobaciones/bandeja.html | 26 | Retiro de alumno Retirar a Mateo Quispe Huamán desde el 01/10/2026 Boleta B001-00000001 · S/ 900.00 · Efectivo | Tooltip «i» (control antifraude: no se borra) | |
| 36 | aprobaciones/bandeja.html | 55 | Queda en la bitácora con tu comentario: qué verificaste con la cajera y cómo se resolvió (la diferencia o los pagos agregados después de reabrir). | Tooltip «i» (control antifraude: no se borra) | |
| 37 | aprobaciones/bandeja.html | 66 | Sale dinero ya cobrado. Confirma por teléfono con la familia, a un celular registrado. En la bitácora queda el apoderado con quien hablaste y su número enmascarado. | Tooltip «i» (control antifraude: no se borra) | |
| 38 | aprobaciones/bandeja.html | 90 | La pediste tú, cobraste ese pago, es tu caja o preparaste esa cuenta: debe resolverla otra persona. | Tooltip «i» (control antifraude: no se borra) | |
| 39 | aprobaciones/bandeja.html | 97 | Matrículas con fecha de ingreso posterior al inicio de clases: recortan pensiones. Revisa que cada una tenga sustento. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 40 | aprobaciones/cajas.html | 9 | Lo cobrado por cada cajera, su cierre a ciegas (con la diferencia) y su depósito. | Tooltip «i» (control antifraude: no se borra) | |
| 41 | aprobaciones/detalle.html | 45 | Sale dinero ya cobrado: antes, llama a la familia a un celular registrado. | Tooltip «i» (control antifraude: no se borra) | |
| 42 | aprobaciones/detalle.html | 70 | No se cambia nada. Quien la pidió verá tu motivo en la bitácora. | Tooltip «i» (control antifraude: no se borra) | |
| 43 | aprobaciones/detalle.html | 83 | La pediste tú o participaste (cobraste ese pago, es tu caja o preparaste esa cuenta): la resuelve otra persona. | Tooltip «i» (control antifraude: no se borra) | |
| 44 | aprobaciones/movil.html | 9 | Toca una solicitud para ver el detalle y resolverla. Quien la pidió (o cobró ese pago) no la aprueba, desde ningún dispositivo. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 45 | aprobaciones/movil.html | 23 | Anulación de pago Boleta B001-00000012 · S/ 350.00 Pedida por caja Motivo Ver y resolver | Tooltip «i» (control antifraude: no se borra) | |
| 46 | conectores/lista.html | 8 | Adónde entra el dinero del colegio y quién emite sus boletas. Cambiar un conector lo propone una persona y lo aprueba otra de Promotoría o Dirección con su firma; en t… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 47 | conectores/lista.html | 50 | Lo aprueba otra persona de Promotoría o Dirección en Aprobaciones. | Tooltip «i» (control antifraude: no se borra) | |
| 48 | conectores/lista.html | 69 | Real mueve dinero o tiene valor legal. Pruebas usa la cuenta de pruebas del proveedor. | Tooltip «i» (control antifraude: no se borra) | |
| 49 | conectores/lista.html | 76 | La que te dio Nubefact para el RUC del colegio (https://api.nubefact.com/...). | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 50 | conectores/lista.html | 89 | Se guarda cifrado y nadie lo vuelve a ver: solo sus 4 últimos caracteres. | Tooltip «i» (control antifraude: no se borra) | |
| 51 | pensiones/resumen.html | 9 | Un plan por nivel. Lo propone Administración y lo aprueba otra persona de Promotoría o Dirección. Un plan aprobado no cambia: para cambiar montos se crea una versión n… | Tooltip «i» (control antifraude: no se borra) | |
| 52 | pensiones/resumen.html | 75 | 0}" th:text="/${n.sinCronograma} ${n.sinCronograma == 1 ? 'matrícula' : 'matrículas'} de ${n.etiqueta} sin cronograma./">2 matrículas sin cronograma. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 53 | pensiones/plan.html | 57 | Al enviarlo queda bloqueado: nadie lo edita mientras otra persona de Promotoría o Dirección lo revisa. Si hay que corregirlo, te lo devolverán. | Tooltip «i» (control antifraude: no se borra) | |
| 54 | pensiones/plan.html | 63 | Al aprobar, este plan reemplaza a la versión vigente (sus cuotas ya generadas no cambian) y se generan los cronogramas de las matrículas de este nivel que aún no lo ti… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 55 | pensiones/plan-formulario.html | 10 | Queda en borrador hasta que otra persona de Promotoría o Dirección lo apruebe. Quien lo propone o lo edita no puede aprobarlo. | Tooltip «i» (control antifraude: no se borra) | |
| 56 | pensiones/plan-formulario.html | 42 | No puede ser mayor que una pensión (DS 005-2021-MINEDU). Con 0 no se cobra. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 57 | pensiones/plan-formulario.html | 55 | Vencimiento de cada pensión: una por mes, en orden. Las casillas vacías no se usan. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 58 | puesta-en-marcha/lista.html | 9 | El colegio está en preparación: todavía no se registran pagos ni salen recordatorios de cobro. Completa la lista y la Promotoría lo pasa a producción. | Tooltip «i» (control antifraude: no se borra) | |
| 59 | puesta-en-marcha/lista.html | 11 | El colegio está en producción. Esta lista es la salud de su configuración. | Tooltip «i» (control antifraude: no se borra) | |
| 60 | puesta-en-marcha/lista.html | 46 | Dos pasos de la lista se pueden cumplir con tu firma, si así es el colegio. Quedan resaltados en la bitácora y no se deshacen. | Tooltip «i» (control antifraude: no se borra) | |
| 61 | puesta-en-marcha/lista.html | 58 | Solo la Promotoría, con la firma de su sesión y la lista obligatoria completa. No tiene vuelta atrás: desde ese momento se registran pagos y salen los avisos a las fam… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 62 | puesta-en-marcha/lista.html | 70 | El contador o un tercero de confianza del contrato: recibe la huella de la bitácora y el resumen diario. Lo registra el operador de la plataforma; con el colegio en pr… | Tooltip «i» (control antifraude: no se borra) | |
| 63 | caja/buscar.html | 12 | Busca al alumno o a su apoderado. El sistema ya sabe cuánto debe cada familia. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 64 | caja/buscar.html | 34 | Por ejemplo: «quispe» o «7845». Escribe al menos 2 letras o números. | Tooltip «i» (control antifraude: no se borra) | |
| 65 | caja/buscar.html | 47 | Mateo Quispe Huamán 5.° Primaria A · 2026 Familia Quispe Huamán · DNI 78451236 Debe S/ 450.00 vencido Al día 0 and !r.debeVencido()}" class="secundario" th:text="/Por … | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 66 | caja/cierre.html | 37 | Escribe el total o llena los billetes y monedas (abajo): el sistema suma. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 67 | caja/cierre.html | 79 | Por ejemplo: «Conté mal los billetes de S/ 20» o «Di vuelto de más a una familia». Lo verá quien apruebe el cierre. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 68 | caja/cierre.html | 130 | Deposita en el banco lo contado menos el fondo fijo (el sencillo se queda en caja) y registra el número de operación del voucher. Administración lo verificará con el e… | Tooltip «i» (control antifraude: no se borra) | |
| 69 | caja/comprobante.html | 63 | Si la devolución fue a la cuenta de origen (Yape, Plin, transferencia o tarjeta), Administración anota el número de operación al registrarla. | Tooltip «i» (control antifraude: no se borra) | |
| 70 | caja/correccion.html | 9 | El mismo dinero pasa a otras cuotas. Al aprobarse, se emite una nota de crédito y una boleta nueva. Nadie borra ni edita el pago. | Tooltip «i» (control antifraude: no se borra) | |
| 71 | caja/correccion.html | 74 | Por ejemplo: «Se aplicó a la pensión de Mateo y era la de Valeria». Quedará en la auditoría. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 72 | caja/familia.html | 19 | Pago en línea en curso. Pregunta al apoderado si ya pagó en su celular antes de cobrar. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 73 | caja/familia.html | 98 | En el siguiente paso ves el total exacto y recién ahí se registra el pago. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 74 | caja/revisar.html | 35 | Caja no decide montos: la deuda la generó el sistema. Al registrar, se emite el comprobante. | Tooltip «i» (control antifraude: no se borra) | |
| 75 | caja/revisar.html | 45 | Lo que te entregó el apoderado. El vuelto se muestra al registrar el pago. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 76 | caja/revisar.html | 52 | Cópialo de la constancia del Yape, Plin, transferencia o voucher. Administración lo verificará con el banco. | Tooltip «i» (control antifraude: no se borra) | |
| 77 | caja/revisar.html | 89 | Esta familia no tiene RUC registrado: solo boleta. Para factura, Administración registra el RUC del apoderado y lo aprueba otra persona. | Tooltip «i» (control antifraude: no se borra) | |
| 78 | caja/revisar.html | 116 | Al cobrar se emite la boleta y el apoderado recibe el aviso con el monto y el número. | Tooltip «i» (control antifraude: no se borra) | |
| 79 | conciliacion/cierres-mensuales.html | 8 | Cada mes, quien no subió ni confirmó extractos escribe a ciegas los totales del estado de cuenta oficial del banco. | Tooltip «i» (control antifraude: no se borra) | |
| 80 | conciliacion/confirmar.html | 26 | Se confirman de uno en uno, del más antiguo al más nuevo, cada uno con el saldo de su último día. | Tooltip «i» (control antifraude: no se borra) | |
| 81 | conciliacion/confirmar.html | 42 | Elegidos al azar cuando se subió el extracto. El monto no se muestra a propósito: míralo en tu app. Si alguno no está en tu app del banco, no confirmes: puede ser un e… | Tooltip «i» (control antifraude: no se borra) | |
| 82 | conciliacion/cuentas.html | 9 | Las cuentas en soles cuyo extracto se concilia (donde abonan Yape empresarial, la pasarela y la recaudación). Solo Promotoría las registra o desactiva; el número no se… | Tooltip «i» (control antifraude: no se borra) | |
| 83 | conciliacion/cuentas.html | 46 | Como aparece en el banco: dígitos y guiones (por ejemplo 191-2345678-0-12). | Tooltip «i» (control antifraude: no se borra) | |
| 84 | conciliacion/diferencias.html | 10 | Todavía no hay extractos confirmados. Administración sube el extracto del banco y Promotoría o Dirección lo confirman escribiendo a ciegas el saldo de su app. | Tooltip «i» (control antifraude: no se borra) | |
| 85 | conciliacion/diferencias.html | 23 | Mientras no se confirmen a ciegas, el sistema no da por buena ninguna pareja. | Tooltip «i» (control antifraude: no se borra) | |
| 86 | conciliacion/diferencias.html | 25 | BCP · 1 extracto Confirmar a ciegas Lo confirma Promotoría o Dirección | Tooltip «i» (control antifraude: no se borra) | |
| 87 | conciliacion/diferencias.html | 49 | El extracto confirmado ya cubre su día y no aparece. Un Yape o una transferencia que no está en el banco puede ser un pago inventado para tapar efectivo: revísalo hoy … | Tooltip «i» (control antifraude: no se borra) | |
| 88 | conciliacion/diferencias.html | 51 | Pago digital Yape B001-00000012 · cobró Lucía Ramos Del 05/10 | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 89 | conciliacion/diferencias.html | 65 | Mismo monto y fecha cercana, pero no la misma operación. Confírmalas solo si estás seguro: quien cobró o depositó no puede hacerlo. | Tooltip «i» (control antifraude: no se borra) | |
| 90 | conciliacion/diferencias.html | 67 | En el banco ABONO YAPE 05/10 Registrado Yape B001-00000012 05/10 El extracto todavía no está confirmado a ciegas: se revisa después. Confirmar | Tooltip «i» (control antifraude: no se borra) | |
| 91 | conciliacion/diferencias.html | 100 | Dinero que entró al banco y nadie registró. Emparéjalo con lo que corresponde o explica qué es (intereses, transferencia propia...). | Tooltip «i» (control antifraude: no se borra) | |
| 92 | conciliacion/diferencias.html | 102 | ABONO TRANSF 05/10 El extracto todavía no está confirmado a ciegas: se revisa después. Emparejar Emparejar a mano Elige lo registrado que corresponde a este abono (sol… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 93 | conciliacion/diferencias.html | 162 | Dinero que salió del banco. Búscalo en tu app del banco y explica qué es. Lo explica alguien que no subió el extracto; si no está en el banco, el extracto fue alterado… | Tooltip «i» (control antifraude: no se borra) | |
| 94 | conciliacion/diferencias.html | 164 | COMISION 05/10 Explicar ¿Qué es este cargo? Explica qué es después de verlo en tu app del banco. Queda resaltado para Promotoría. Qué es Elige Comisión Nota (obligator… | Tooltip «i» (control antifraude: no se borra) | |
| 95 | conciliacion/diferencias.html | 199 | ¿Algo que la conciliación no puede ver (por ejemplo, un Yape que llegó a otra cuenta)? Usa la verificación manual (excepciones). | Tooltip «i» (control antifraude: no se borra) | |
| 96 | conciliacion/extracto.html | 23 | Los saldos y los totales se muestran cuando otra persona confirme el extracto escribiendo a ciegas el saldo de su app del banco. | Tooltip «i» (control antifraude: no se borra) | |
| 97 | conciliacion/extractos.html | 9 | Cada día se sube el extracto del día anterior. Es una cadena: cada extracto empieza con el saldo final del anterior y el día siguiente. Lo confirma otra persona escrib… | Tooltip «i» (control antifraude: no se borra) | |
| 98 | conciliacion/extractos.html | 46 | Descarga el extracto de días completos (hasta ayer) de la banca por internet y súbelo sin abrirlo ni cambiarlo: se guarda tal cual, con su huella. Si trae días ya carg… | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 99 | conciliacion/extractos.html | 51 | Hasta 2 MB. La línea «cuenta;número» y las columnas fecha, descripcion, numero_operacion, referencia, cargo, abono y saldo (obligatorio). | Tooltip «i» (control antifraude: no se borra) | |
| 100 | conciliacion/extractos.html | 57 | Desarrollo: descarga el extracto de ejemplo de ayer (con los pagos de la demostración). | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 101 | conciliacion/revisar.html | 35 | El saldo final no se muestra aquí a propósito: lo escribirá a ciegas quien confirme, mirando su app del banco. | Tooltip «i» (control antifraude: no se borra) | |
| 102 | conciliacion/revisar.html | 56 | Las exactas (misma operación y monto) las confirma el sistema cuando se confirme el extracto; las sugeridas las revisa Administración. | Tooltip «i» (control antifraude: no se borra) | |
| 103 | conciliacion/revisar.html | 76 | Al registrarlo, el archivo queda guardado tal cual (con su huella) y espera la confirmación a ciegas de otra persona de Promotoría o Dirección. Recién entonces se conc… | Tooltip «i» (control antifraude: no se borra) | |
| 104 | conciliacion/verificacion.html | 9 | La conciliación automática con el extracto verifica sola lo que cuadra. Aquí quedan las excepciones: busca el Yape, Plin, transferencia o depósito en el banco y escrib… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 105 | conciliacion/verificacion.html | 23 | Ya aprobadas. Promotoría tiene una alerta crítica hasta que registres el reembolso: un pago digital vuelve solo a la cuenta de origen; el efectivo se entrega contra la… | Tooltip «i» (control antifraude: no se borra) | |
| 106 | conciliacion/verificacion.html | 26 | Yape · Familia Quispe Pago B001-00000001 Pago en línea: se devuelve solo por la pasarela, al mismo medio de origen (no se escribe ningún número). No lo hace quien pidi… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 107 | conciliacion/verificacion.html | 115 | «Tardío»: el depósito es de más de un día hábil después de la caja. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 108 | recaudacion/confirmar.html | 39 | Si alguno no está en el banco, no confirmes: puede ser un archivo alterado. Avisa a Promotoría y a Dirección. | Tooltip «i» (control antifraude: no se borra) | |
| 109 | recaudacion/linea.html | 43 | Si al llamar a la familia te dicen el código correcto de su hijo, escríbelo para ver las cuotas de esa familia. | Tooltip «i» (control antifraude: no se borra) | |
| 110 | recaudacion/linea.html | 58 | Promotoría o Dirección lo aprueban; el pago lo registra el sistema, no tú. | Tooltip «i» (control antifraude: no se borra) | |
| 111 | recaudacion/linea.html | 75 | Por ejemplo: «Llamé a la Sra. Rosa; se equivocó en un dígito del código». Quedará en la auditoría. | Tooltip «i» (control antifraude: no se borra) | |
| 112 | recaudacion/linea.html | 85 | Si el pago no corresponde a ninguna deuda, se devuelve por transferencia a quien lo hizo. Escribe la cuenta de quien pagó (la confirma la familia): quien aprueba la ve… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 113 | recaudacion/lista.html | 8 | Pagos que las familias hacen en el banco con el código del alumno. Nadie los digita: Administración sube el archivo del banco, otra persona lo confirma mirando el banc… | Tooltip «i» (control antifraude: no se borra) | |
| 114 | recaudacion/lista.html | 17 | Quien confirma entra al portal del banco y escribe el total recaudado que ve ahí. Nunca lo confirma quien subió el archivo. | Tooltip «i» (control antifraude: no se borra) | |
| 115 | recaudacion/lista.html | 40 | El dinero está en el banco pero no se pudo aplicar solo. Llama a la familia antes de pedir aplicarlo o devolverlo. | Tooltip «i» (control antifraude: no se borra) | |
| 116 | recaudacion/lista.html | 71 | Hasta 2 MB. Columnas: fecha_pago, codigo_alumno, referencia_deuda, monto, moneda, numero_operacion y, al final, el pie TOTAL;monto;cantidad. | Tooltip «i» (control antifraude: no se borra) | |
| 117 | recaudacion/revisar.html | 31 | El total no se muestra aquí a propósito: lo escribirá a ciegas quien confirme, mirando el portal del banco. | Tooltip «i» (control antifraude: no se borra) | |
| 118 | recaudacion/revisar.html | 52 | Así se aplicaría hoy. Si algo cambia antes de que se confirme (por ejemplo, la familia paga en caja), el sistema lo deja por revisar: nunca cobra dos veces. | Tooltip «i» (control antifraude: no se borra) | |
| 119 | recaudacion/revisar.html | 74 | Al registrarlo, el archivo queda guardado tal cual (con su huella) y espera la confirmación a ciegas de otra persona de Promotoría o Dirección. Recién entonces el sist… | Tooltip «i» (control antifraude: no se borra) | |
| 120 | familia/algo-no-cuadra.html | 9 | Tu aviso le llega solo a Promotoría y Dirección. Te responderemos aquí. | Tooltip «i» (control antifraude: no se borra) | |
| 121 | familia/algo-no-cuadra.html | 26 | Puedes ver ahora mismo los datos que guarda el colegio en Mis datos. Si quieres una respuesta por escrito, una corrección o que dejen de usarlos, elige aquí. Te respon… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 122 | familia/algo-no-cuadra.html | 53 | Hasta 500 caracteres. Por ejemplo: «Pagué S/ 380 en caja el 5 de octubre y no me llegó el mensaje». | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 123 | familia/algo-no-cuadra.html | 64 | Pagué y no aparece 05/10/2026 10:40 Pediste: Acceso. Te responderemos a más tardar el 20/10/2026. Pagué y no aparece Respuesta del colegio: Ya lo revisamos. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 124 | familia/asistencia.html | 35 | lun 20 08:12 (Salud) Motivo Motivo de la falta Salud Nota (opcional) Justificar | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 125 | familia/avisos.html | 11 | Elige cómo recibes el aviso de asistencia del día (faltas y tardanzas de tus hijos). Aunque no recibas avisos, siempre ves la asistencia en el portal. | Tooltip «i» (control antifraude: no se borra) | |
| 126 | familia/avisos.html | 21 | Avisos de asistencia por WhatsApp (contacto sin verificar: no llega hasta que lo confirmes) Desde: Portal Cambiar | Tooltip «i» (control antifraude: no se borra) | |
| 127 | familia/avisos.html | 39 | Los avisos de pago no se apagan: son el control de la familia sobre lo que se registra en el colegio. | Tooltip «i» (control antifraude: no se borra) | |
| 128 | familia/comprobantes.html | 9 | Abre una boleta y usa «Imprimir» o «Guardar como PDF» de tu navegador para guardarla. | Tooltip «i» (control antifraude: no se borra) | |
| 129 | familia/comprobantes.html | 17 | Ver o guardar B001-00000231 05/10/2026 · Yape · en caja Anulado | Tooltip «i» (control antifraude: no se borra) | |
| 130 | familia/comunicados.html | 10 | Del colegio y de los docentes de tus hijos. Si te piden confirmar, hazlo aquí. | Tooltip «i» (control antifraude: no se borra) | |
| 131 | familia/estado-de-cuenta.html | 9 | Todas las cuotas de tus hijos, con sus pagos, anulaciones y descuentos. Si ves algo que no reconoces, avísanos. | Tooltip «i» (control antifraude: no se borra) | |
| 132 | familia/estado-de-cuenta.html | 28 | Pensión marzo 2026 Vence 31/03/2026 0}" th:text="/Descuento: S/ ${#numbers.formatDecimal(c.descuento, 1, 'COMMA', 2, 'POINT')}/">Descuento: S/ 50.00 0}" th:text="/Falt… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 133 | familia/estado-de-cuenta.html | 53 | B001-00000231 05/10/2026 · Yape · en caja Anulado. Motivo: ... Aprobó: Dirección | Tooltip «i» (control antifraude: no se borra) | |
| 134 | familia/inicio.html | 9 | Aquí ves todo lo que debe tu familia y lo pagas desde el celular. Si ves un cobro que no reconoces, avisa al colegio. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 135 | familia/inicio.html | 41 | Confirmada. Paga la matrícula en «Lo que debes» y quedará activa. | Tooltip «i» (control antifraude: no se borra) | |
| 136 | familia/inicio.html | 58 | El pago en línea todavía no está disponible. Puedes pagar en caja o en el banco con el código de pago de tu hijo. | Tooltip «i» (control antifraude: no se borra) | |
| 137 | familia/inicio.html | 96 | En la ventanilla, agente o app de tu banco, paga con el código de pago de tu hijo. El pago aparece aquí al día hábil siguiente. | Tooltip «i» (control antifraude: no se borra) | |
| 138 | familia/inicio.html | 130 | ¿Pagaste y no lo ves, o ves algo que no reconoces? Avísanos: va directo a Promotoría. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 139 | familia/inicio.html | 136 | Los avisos de tus pagos, anulaciones y descuentos siempre llegan: así compruebas que todo cuadra. | Tooltip «i» (control antifraude: no se borra) | |
| 140 | familia/inicio.html | 145 | Mira qué datos tuyos y de tus hijos guarda el colegio, para qué los usa y a quién los envía. Si algo está mal o quieres pedir algo sobre ellos, avísanos. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 141 | familia/libretas.html | 12 | El informe de progreso de tus hijos al cierre de cada periodo. Si el colegio corrige una nota, verás la versión nueva con lo que cambió; las anteriores siguen aquí. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 142 | familia/libretas.html | 25 | Todavía no tiene libretas: aparecerán al cierre del periodo. | Tooltip «i» (control antifraude: no se borra) | |
| 143 | familia/libretas.html | 29 | Versión 1 · emitida el 08/05/2026 Al cierre del periodo Qué cambió Descargar PDF | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 144 | familia/libretas.html | 42 | Cada libreta tiene un código de verificación. Si tienes una duda sobre una nota, comunícate con el tutor o con Dirección. | Tooltip «i» (control antifraude: no se borra) | |
| 145 | familia/matricula.html | 28 | Listo. Ya puedes pagar la matrícula: aparece en «Lo que debes». Al pagarla, la matrícula queda activa. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 146 | familia/mensajes.html | 9 | Cada pago, anulación o descuento te llega por WhatsApp o correo. Si pagaste y no recibiste tu mensaje en 1 hora, o ves algo que no reconoces, avisa al colegio. | Tooltip «i» (control antifraude: no se borra) | |
| 147 | familia/mensajes.html | 17 | 05/10/2026 10:40 · Pago registrado · WhatsApp Registramos su pago | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 148 | familia/mis-datos.html | 9 | Estos son los datos tuyos y de tus hijos que guarda el colegio, para qué los usamos, a quién los enviamos y cuánto tiempo los guardamos. Puedes imprimir esta página. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 149 | familia/mis-datos.html | 18 | Rosa Huamán Quispe Madre Otro apoderado de tu familia. Sus datos personales (documento, celular y correo) solo los ve esa persona en su propia cuenta. Responsable de p… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 150 | familia/mis-datos.html | 49 | Mateo Quispe Huamán Activo DocumentoDNI 76543210 Fecha de nacimiento21/08/2014 Matrículas AñoGradoSecciónEstado 2026 6.° de primaria A Activa | Tooltip «i» (control antifraude: no se borra) | |
| 151 | familia/mis-datos.html | 107 | Tu celular y tu correo los cambias pidiéndolo al colegio: te enviamos un enlace para que los confirmes. Para corregir un nombre o un documento, pedir una respuesta por… | Dejar 1 línea visible con lo que se debe verificar + tooltip con el resto | |
| 152 | familia/orden.html | 31 | Esta página se actualiza sola. Solo decimos «pagado» cuando la pasarela nos lo confirma. | Tooltip «i» (control antifraude: no se borra) | |
| 153 | familia/pasarela-simulada.html | 10 | Esta página hace el papel de la pasarela real. Ninguna opción mueve dinero: solo prueba el flujo completo. | Quitar (o «Ver detalle» si es instrucción de un paso) | |
| 154 | familia/revisar.html | 28 | El total lo calcula el colegio con lo que debes: no se puede cambiar. Pagas el monto exacto, sin recargo. | Tooltip «i» (control antifraude: no se borra) | |
