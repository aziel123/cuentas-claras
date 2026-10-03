# Sprint 3 · Correcciones tras la auditoría antifraude y QA

Decisiones de la ronda de correcciones del sprint 3. Cada corrección viene con una prueba que reproduce el ataque y que falla si se quita la corrección. Las tablas y columnas nuevas van en **V12** (V9–V11 no se editan); también se actualizan `02-permisos-tablas.sql` y `03-triggers.sql`.

## Crítico
- **C1. Un mismo número de operación se registraba varias veces cambiando el formato.**
  - **Forma canónica:**
    - `NumeroOperacion.normalizar` deja solo letras y dígitos en mayúsculas, sin guiones, espacios ni ceros a la izquierda (`0012-345` → `12345`), con 4 a 30 caracteres;
    - el número se **guarda** ya canónico; lo que se muestra es lo guardado;
    - en la base, V12 agrega un CHECK de formato en `pago` y en `deposito_caja`.
  - **Unicidad en la base:**
    - `UNIQUE (colegio_id, operacion_vigente)` cubre todos los medios digitales juntos (`operacion_vigente` es NULL en efectivo y en los anulados);
    - para los depósitos, `UNIQUE (colegio_id, numero_operacion)`.
  - **Verificación a ciegas:**
    - Administración ya no ve el número ni el monto registrados; escribe la operación, la fecha y el monto que ve en el banco, y el sistema compara;
    - si coinciden, la verificación queda ENCONTRADO con esos datos guardados (V12: `banco_operacion`, `banco_fecha`, `banco_monto`, que un CHECK exige en ENCONTRADO);
    - si no coinciden, no se guarda nada, se audita resaltado (`VERIFICACION_NO_COINCIDE`) y se responde sin decir qué campo falló;
    - «No aparece» sigue disponible, con nota obligatoria;
    - en MySQL, el trigger exige que lo escrito coincida con el pago o el depósito.
  - **Números parecidos:** `/conciliacion` marca los pagos y los depósitos cuyo número tiene la misma forma canónica o está a 1 carácter de distancia (Levenshtein ≤ 1) de otro registrado en los últimos 90 días. Muestra el comprobante del otro, nunca el número.

## Alto
- **A1. Un Yape inventado se borraba de la conciliación pidiendo su devolución.**
  - Un pago digital solo se puede anular (devolución o corrección) si está verificado ENCONTRADO. Se valida al pedirlo y otra vez al aprobarlo.
  - Toda devolución aprobada exige un **reembolso**: tabla nueva `reembolso`, de solo inserción. Lo registra **Administración**, nunca la cajera del pago (CHECK y trigger):
    - en un pago digital, con el número de operación de la transferencia, confirmando que va a la cuenta de origen;
    - en efectivo, con el nombre y el documento de quien recibe, que firma la nota de crédito.
  - Mientras no se registre el reembolso hay una alerta CRÍTICA, también con la caja abierta.
  - La tarjeta de la bandeja muestra el estado de la verificación bancaria del pago.
- **A2. Una devolución en efectivo sacaba dinero del esperado con un clic.**
  - **Llamada al apoderado:** aprobarla exige marcar «Hablé con el apoderado» y escribir el número llamado.
    - El número debe ser un celular registrado de un apoderado de la familia; si no, se rechaza.
    - En la bitácora queda el número **enmascarado** junto con el apoderado al que corresponde. Así se sigue la decisión 18 (sin celulares completos en la bitácora) y aun así se puede auditar.
  - **Corrección hacia otra familia:** exige lo mismo con un número de **cada** familia («hablé con ambas familias»).
  - **Entrega:** la registra Administración (reembolso EFECTIVO).
  - **Firma:** la nota de crédito impresa trae espacio para la firma, el nombre y el DNI del apoderado.
  - **Alerta diaria:** Promotoría ve en «Para revisar» todas las devoluciones en efectivo del día.
  - **Pago duplicado:** al pedir la devolución se elige una causa. Si es «pago duplicado» (o el motivo lo dice), el sistema exige que exista **otro** pago vigente de alguna de esas cuotas; si no lo hay, la rechaza.
- **A3. Reemplazo en efectivo desde una devolución.** `trg_pago_registro` exige que el pago reemplazado tenga una `anulacion_pago` de tipo CORRECCION. La aplicación ya lo cumplía; ahora también lo exige la base.
- **A4. Verificación sin evidencia y alerta tardía.**
  - Evidencia: los datos escritos a ciegas de C1.
  - Alerta CRÍTICA cuando un pago digital sigue sin verificar después de la hora límite (19:00) del **día hábil siguiente** al cobro.
  - La alerta ATENCIÓN de «sin verificar» se cuenta en horas desde el registro (24 h × días configurados), no en fechas de calendario (hallazgo 7 de QA).
  - Muestreo: «Para revisar» muestra 3 verificaciones ENCONTRADO del día hábil anterior, elegidas al azar con una semilla que es la fecha (estables durante el día). Cada una dice quién verificó y qué escribió, para que Promotoría la compare con el banco.
- **B3.** `verificarPago` rechaza los pagos que no están VIGENTES.

## Medio
- **M1. Lo que `cc_app` podía hacer sin la aplicación.**
  - **Reapertura y anulación de cuota:**
    - la bandeja ahora **aprueba la solicitud antes** de aplicarla;
    - `caja_diaria.reapertura_solicitud_id` y `cuota.anulacion_solicitud_id` (V12) enlazan la solicitud por id;
    - el trigger exige que sea APROBADA, del tipo y la entidad correctos y resuelta el mismo día (de la caja o de la anulación);
    - una reapertura se usa una sola vez (UNIQUE). Una cuota no lleva UNIQUE: el ingreso tardío aprobado anula varias cuotas de su matrícula con la misma solicitud.
    - Se corrigió lo que afirmaban `mysql-usuarios.md` y `ManejadorReaperturaCaja`.
    - Límite: `cc_app` escribe nombres de usuario como texto, así que la base no puede probar *quién* aprobó. Ese riesgo lo cubren la bitácora con HMAC y el aislamiento de credenciales.
  - **Resoluciones inmutables:** triggers impiden cambiar la revisión de `cierre_caja` y la resolución de `descuento` y de `solicitud_cambio` una vez resueltas.
  - **Series:** alerta CRÍTICA si existe una serie que no es una de las configuradas (B001, F001, BC01 y FC01).
  - **Consistencia:** alerta CRÍTICA si una BOLETA o FACTURA no tiene su pago, o si una NOTA_CREDITO no tiene su `anulacion_pago`.
  - **Reemplazo (A3), verificación (C1), reembolso (A1) y RUC (B2):** también con trigger (ver arriba).
  - **Envío al OSE:**
    - `trg_comprobante_envio` impide cambiar el estado, el hash o la respuesta de un comprobante que ya no está PENDIENTE;
    - para pasar a ACEPTADO exige el hash, la respuesta y la fecha de envío.
    - Con el OSE real se usará un usuario de proceso aparte, con su propio GRANT, para registrar los envíos.
- **M2. Faltaba comprobar los triggers BEFORE UPDATE al arrancar.**
  - `02-permisos-tablas.sql` crea la vista `cuentasclaras.trigger_instalado`, con `SQL SECURITY DEFINER` sobre `information_schema.TRIGGERS` del esquema, y da a `cc_app` SELECT solo sobre ella.
  - Al arrancar en prod, `VerificadorPermisosBaseDatos` compara esa vista con la lista completa de triggers esperados. Una prueba asegura que la lista coincide con `03-triggers.sql`. Si falta uno, la aplicación no arranca.
  - El CI borra un trigger BEFORE UPDATE, comprueba que prod no arranca y lo restaura.
- **M3. Efectivo sin depositar (lapping).**
  - Alerta CRÍTICA cuando pasa un día hábil completo sin depositar una caja cerrada con efectivo.
  - En rojo y como alerta: un depósito cuya fecha (la declarada o la del banco, verificada a ciegas) es posterior en más de un día hábil a la fecha de la caja.
  - Los días hábiles son de lunes a viernes; los feriados no se consideran y queda anotado como riesgo.

## Bajo
- **B1.** *Pagos de hoy* no muestra el monto de los pagos en efectivo mientras la caja está abierta: solo el comprobante y la cantidad de pagos. (El comprobante de cada pago sí muestra su total, porque se imprime para el apoderado.)
- **B2. Factura solo con el RUC registrado de la familia.**
  - El apoderado tiene `ruc` y `razon_social` (V12).
  - Se registran o cambian con una solicitud `DATOS_FACTURACION` que aprueba otra persona, como los contactos del sprint 2.
  - En caja, la factura solo se emite con el RUC registrado de un apoderado de la familia; si no hay, solo boleta.

## Hallazgos de QA (mutaciones)
- **1. Concurrencia entre cobro y cierre.**
  - El cobro, el conteo y el cierre, y la aprobación de una anulación bloquean la caja con `SELECT ... FOR UPDATE` como **primera** lectura y en el mismo orden: solicitud → caja → pago → cuotas.
  - En la anulación, el bloqueo es la primera lectura de la caja en la transacción, así su estado es el confirmado (si se cerró mientras tanto, la anulación queda «posterior al cierre»).
  - Pruebas con latch: el cobro en efectivo durante el cierre y la aprobación de una anulación durante el cierre.
- **2, 3, 5 y 6.** Pruebas nuevas:
  - el esperado real del cierre con una anulación;
  - el fondo fijo dentro del esperado y restado en el depósito;
  - la segunda capa en el modelo (`Pago.enCaja`);
  - la cuenta de caja preparada por Promotoría.
  - `PagoRepository.efectivoVigente` se borra porque ya no lo usa ningún código de producción.
- **4. Cierre después de una reapertura.**
  - La vista ya no trae ningún cierre mientras haya caja por cerrar.
  - Como la cajera ya vio el esperado del cierre anterior, el siguiente cierre de esa caja **no es ciego**:
    - se marca «Cierre tras reapertura»;
    - la bandeja exige comentario y lo pone primero;
    - Promotoría recibe una alerta ATENCIÓN.
- **7, 8 y 9. Fechas.**
  - La alerta de digitales se cuenta en horas.
  - La hora límite incluye las 19:00:00 exactas.
  - Queda fijado el comportamiento de un cobro a las 23:59:59.
  - Se rechazan los depósitos con fecha anterior a la caja o futura.
- **10. Bordes.** Pruebas de:
  - un descuento seguido de una anulación;
  - la beca del 100 %;
  - un descuento de 99.99 % (en una cuota de S/ 450.00 el saldo queda en S/ 0.00, no en S/ 0.40 como decía el hallazgo; en una de S/ 4,500.00 sí queda en S/ 0.40);
  - los montos máximos en el formulario (responden con un mensaje, no con un error 500);
  - Promotoría ve la conciliación pero no verifica.
- **11.** Los `UPDATE cuota SET monto_pagado` de las pruebas se reemplazan por un pago a cuenta real.
- **12.** Las pruebas comparan campos concretos, no `toString()`.
- **13.** En el perfil de pruebas, el envío al OSE usa un ejecutor síncrono (`cuentasclaras.comprobantes.envio-sincrono: true`), así no hay hilos vivos entre pruebas.
- **Ruc** pasó de `comprobantes.model` a `comun.texto`: alumnos lo usa para validar el RUC del apoderado (B2) y no puede depender de comprobantes.
- **14.** Los tiempos de espera de las pruebas concurrentes se amplían y el solapamiento se fuerza con latch.
