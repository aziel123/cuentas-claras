# Sprint 4 · Correcciones tras la auditoría antifraude y QA

Decisiones de la ronda de correcciones del sprint 4 «Cero digitación». Cada corrección viene con una prueba que reproduce el ataque y que falla si se quita la corrección. Los 7 ataques de la auditoría están en `fraude/AtaquesSprint4Test` y ahora **fallan** (cada prueba en verde significa que el ataque ya no funciona). El ataque 6 en MySQL real está en `PermisosMySqlTest.ataque6EmparejarAManoConOtroMontoYaNoVerificaElYapeInventado`.

Todo cambio de esquema va en **V16** (V13–V15 no se editan). También se actualizan `02-permisos-tablas.sql`, `03-triggers.sql` (41 triggers), `VerificadorPermisosBaseDatos`, el job `mysql` del CI y `docs/operacion/mysql-usuarios.md`. Se respeta la sección 7.3 del diseño: ningún script nombra una tabla que todavía no existe en esa fase.

## Crítico
- **S4-C1. Emparejar a mano con cualquier monto borraba la alerta crítica y fabricaba una verificación ENCONTRADO falsa.**
  - **Mismo monto:** una pareja SUGERIDA o MANUAL es por el MISMO monto. Solo la liquidación de la pasarela admite su tolerancia configurable. Lo exigen el servicio (`ReglasEmparejamiento.montoAdmitido`), la pantalla (solo ofrece objetos del mismo monto) y la base (`ck_partida_conciliacion_monto_exacto` en V16).
  - **Sin agrupar (N:1):** no se agrupan varios pagos en un abono. Si el banco agrupa, el abono se explica o se avisa a Promotoría. Agrupar con suma exacta queda para cuando un banco lo necesite.
  - **La MANUAL pasa por la bandeja:** Administración la pide (queda PROPUESTA, con nota). Otra persona de Promotoría o Dirección, que no cobró, registró, depositó ni subió lo emparejado, la aprueba con comentario obligatorio (`ManejadorPartidaManual`). Si se rechaza, la pareja se descarta y todo sigue en rojo. `trg_partida_conciliacion_estado` exige la solicitud `PARTIDA_MANUAL` APROBADA por quien confirma.
  - **La verificación guarda el monto del banco:** la verificación AUTOMATICA guarda el monto del MOVIMIENTO, y `trg_verificacion_bancaria_registro` exige `m.monto = NEW.banco_monto`.
  - **Toda partida con diferencia es CRÍTICA:** cualquier partida vigente con diferencia distinta de 0 (solo puede ser una liquidación) es alerta CRÍTICA diaria.
  - **Pruebas:** `AtaquesSprint4Test.ataque6EmparejarAManoConOtroMontoYaNoBorraLaAlertaCritica`, `parejaManualDelMismoMontoEsperaLaBandejaYSiSeRechazaSigueEnRojo`; `ServicioPartidasTest.descartarYEmparejarAMano`; `PermisosMySqlTest.ataque6EmparejarAManoConOtroMontoYaNoVerificaElYapeInventado`.
  - **App dev por HTTP:** Administración pide emparejar los intereses (S/ 1.23) con el Yape inventado (S/ 450.00) y recibe «El movimiento del banco es por S/ 1.23 y lo elegido por S/ 450.00: a mano solo se empareja el MISMO monto». La alerta CRÍTICA del Yape sigue en el inicio de Promotoría.

## Alto
- **S4-A1. La confirmación a ciegas no era ciega.**
  - **Sin montos ni tipos:** mientras un extracto o un lote está CARGADO, nadie ve sus montos ni sus tipos. Ni el detalle, ni la lista, ni la pantalla de confirmación (`DetalleExtracto.montosOcultos`, `ConfirmacionExtractoVista`).
  - **Muestra fija:** la muestra que ve quien confirma se elige UNA vez al registrar (`MuestraAlAzar`, SecureRandom) y se guarda (`extracto_bancario.muestra`, `lote_recaudacion.muestra`, sin GRANT UPDATE: 1143). Nunca cubre todo el archivo y se muestra sin montos: fecha, glosa y operación.
  - **Nada descargable:** no se descarga el archivo de un lote o extracto descartado o rechazado de los mismos días mientras otro espera confirmación.
  - **Saldo propio:** cada extracto pendiente se confirma con SU saldo de cierre. El saldo de un extracto anterior ya no permite calcular el del siguiente. Esto cambia la decisión 22: antes se confirmaba la cadena con el saldo del último. `trg_extracto_bancario_estado` exige confirmar solo con el saldo propio.
  - **Pruebas:** `AtaquesSprint4Test.ataque1ElSaldoCiegoYaNoSeCalculaDesdeLaPantalla`, `ataque3ElTotalCiegoDeRecaudacionYaNoSeReconstruyeConLaMuestra`, `ataque7ElArchivoDeUnLoteDescartadoNoSeDescargaMientrasOtroDeEsosDiasEsperaConfirmacion`, `unExtractoDescartadoDeLosMismosDiasNoMuestraSusMontosMientrasOtroEsperaConfirmacion`; `ServicioExtractosTest.cadaExtractoPendienteSeConfirmaConSuPropioSaldo`; `MuestraAlAzarTest`.
- **S4-A2 y QA-S4-5. Un abono inventado y un cargo inventado del mismo monto se compensaban y el saldo cuadraba.**
  - **Los cargos se revisan:** esto cambia la decisión 30. Un cargo sin pareja se explica (categoría y nota), y lo explica alguien que NO subió el extracto (Promotoría o Dirección también pueden). Sin explicar: ATENCIÓN; pasados 2 días hábiles, CRÍTICA.
  - **Alerta de compensación:** es CRÍTICA cuando un cargo del extracto tiene el mismo monto que un abono de esos días (±2 días hábiles): «¿Abono inventado y un cargo para cuadrar el saldo?».
  - **Semilla secreta:** el muestreo diario usa la semilla secreta del extracto (SecureRandom, guardada en `semilla_muestreo`) combinada con la fecha. Antes la semilla era la fecha y quien subía podía calcular la muestra.
  - **Pruebas:** `AtaquesSprint4Test.ataque2ElCargoQueCompensaUnAbonoInventadoEsCritico`; `CadenaExtractosQaTest.debeAlertarUnAbonoYUnCargoInventadosQueSeCompensanEnElExtracto` (sin `@Disabled`); `AlertasConciliacionTest.elMuestreoEsElMismoTodoElDiaYSoloLoVePromotoria`.
  - **App dev por HTTP:** se sube el extracto forjado de la auditoría (`extracto-forjado-dev.csv`: Yape inventado de S/ 450.00 más «COMISION MANTENIMIENTO POS» de S/ 450.00). Promotoría lo confirma con el saldo real, porque cuadra. Su inicio muestra la CRÍTICA «1 cargo(s) del extracto del mismo monto que un abono de esos días: «COMISION MANTENIMIENTO POS» del 05/10/2026 por S/ 450.00» y la ATENCIÓN de cargos sin explicar.
- **S4-A3 y QA-S4-1/2/3. Contracargo: el sistema pedía una devolución y Administración sacaba un segundo reembolso.**
  - **Un solo componente:** `ContracargosPasarela` atiende el contracargo, llegue por el aviso o por la línea CONTRACARGO de la liquidación, y esté la orden PAGADA, APLICADA, POR_REVISAR o DEVUELTA.
  - **Una vez:** el contracargo se registra una vez en la orden (`contracargo_en`, `contracargo_origen`; V16) y siempre deja alerta CRÍTICA.
  - **Anulación sin reembolso:** si hay un pago vigente, pide una anulación de tipo nuevo **CONTRACARGO**, sin reembolso, porque el banco ya devolvió el dinero. Esto cambia la decisión 32.
  - **Ingreso por revisar:** con contracargo ya no se aplica ni se devuelve (`trg_orden_pago_estado`).
  - **Devolución de un pago en línea:** solo sale por la API de la pasarela (`PasarelaPagos.reembolsar`, tabla de solo inserción `reembolso_pasarela`). `trg_reembolso_registro` rechaza un pago con origen PASARELA, y `trg_reembolso_pasarela_registro` exige una anulación DEVOLUCION aprobada y sin contracargo.
  - **Pruebas:** `AtaquesSprint4Test.ataque5ElContracargoAnulaSinReembolsoYNoSeDevuelveDosVeces`, `laDevolucionDeUnPagoEnLineaSoloSalePorLaPasarela`; `PagosEnLineaQaTest.debeAlertarElContracargoDeUnaOrdenAplicadaTrasRevision`, `debeAlertarElContracargoDeUnIngresoPorRevisar`, `debeAlertarElContracargoQueLlegaEnLaLiquidacion` (sin `@Disabled`); `PermisosMySqlTest.flujoPagoEnLineaConPermisosMinimos`.
- **S4-A4. La devolución de una línea de recaudación no tenía destino y la podía ejecutar quien la pidió.**
  - **Destino obligatorio:** se pide con banco, cuenta y titular de destino (`DevolucionLineaRequest`, columnas `devolucion_*` de V16, con CHECK en DEVUELTA).
  - **Quien la pide no la ejecuta:** la ejecuta alguien que no la pidió ni la aprobó. `trg_linea_recaudacion_estado` exige `devuelto_por <> solicitado_por` y que el destino no cambie.
  - **Se concilia:** la línea DEVUELTA es un objeto de la conciliación (`LINEA_RECAUDACION`): un cargo con esa operación y ese monto. Si no aparece, es alerta CRÍTICA.
  - **Pruebas:** `AtaquesSprint4Test.ataque4LaDevolucionDeRecaudacionTieneDestinoOtraPersonaLaEjecutaYSeConcilia`; `EscenariosFraudeRecaudacionTest.devolucionLaRegistraQuienNoLaPidioNiLaAprobo`; `PermisosMySqlTest.flujoRecaudacionConPermisosMinimos`.

## Medio
- **S4-M1. La pasarela simulada en el perfil `piloto` podía dejar cuotas reales pagadas sin dinero.**
  - **Marca obligatoria:** el piloto con la simulada exige la marca `cuentasclaras.entorno.nombre: PILOTO` (`VerificadorConfiguracion`; ya viene en `application-piloto.yaml`).
  - **Franja global:** todas las páginas, con o sin sesión, muestran la franja «PILOTO · Entorno de prueba: los pagos en línea son SIMULADOS, no son dinero real y no cambian las cuotas».
  - **Pagos simulados por revisar:** en el piloto, un pago simulado queda POR REVISAR (`SIMULADA_EN_PILOTO`). No registra pago ni comprobante, no marca cuotas pagadas y no se puede aplicar.
  - **Pruebas:** `PilotoPasarelaSimuladaTest`; `VerificadorConfiguracionTest.enElPilotoLaSimuladaExigeLaMarcaPilotoYUnSecretoPropio`; `LoginTest.sinMarcaDeEntornoNoHayFranja`.
- **S4-M2. Quien creaba la cuenta en línea del apoderado veía la clave temporal.**
  - **Enlace de un solo uso:** ya no se muestra ninguna clave. Quien da el acceso recibe un enlace que vence en 48 horas. La base guarda solo el SHA-256 del token, en `enlace_activacion` (V16), con UPDATE solo de `usado_en`, `usado_ip`, `anulado_en`, `actualizado_en` y `version`.
  - **Clave desconocida:** la cuenta nace con una clave al azar ya vencida, que nadie conoce.
  - **Activación:** el apoderado abre el enlace (`/activar/{colegio}/{token}`, público), confirma su número de documento y elige su clave.
  - **Auditoría:** la activación queda en la bitácora (`ACCESO_APODERADO_ACTIVADO`) con la IP de quien usó el enlace y la de quien lo creó. El primer ingreso de cualquier cuenta queda señalado con su IP.
  - **Alerta:** si la activación vino de la misma IP de quien creó la cuenta, Promotoría recibe una alerta ATENCIÓN (`AlertasActivacion`).
  - **Restablecer:** solo Promotoría restablece el acceso, desde la familia del apoderado. Los enlaces anteriores se anulan, sus sesiones se cierran y se genera otro enlace.
  - **WhatsApp y correo:** la pantalla explica que el envío automático llega en el sprint 5. Mientras tanto, el enlace se entrega en persona o por un canal del titular.
  - **Pruebas:** `AccesoApoderadosTest` (3 pruebas); `PermisosMySqlTest.elEnlaceDeActivacionDelApoderadoFuncionaYNoSeReescribe`; `VerificadorPermisosBaseDatosTest.fallaSiSePuedenTocarLasCorreccionesDelSprint4`.

## Bajo
- **S4-B1. `objeto_vigente` de una partida CONFIRMADA se podía reescribir.** `trg_partida_conciliacion_estado` impide cambiar el objeto vigente fuera de PROPUESTA y exige que valga `CONCAT(tipo, ':', id)`. Prueba: `PermisosMySqlTest.flujoExtractoYConciliacionConPermisosMinimos`.
- **S4-B2. La base no impedía que quien subió el lote confirmara su pareja.** El trigger lo exige para el lote y también para los pagos de recaudación pago por pago. Prueba: `PermisosMySqlTest.flujoExtractoYConciliacionConPermisosMinimos`.
- **S4-B3. El simulador aceptaba cualquier acción desde la URL.** Ahora tiene una lista blanca: YAPE, TARJETA, RECHAZAR y MONTO_MENOR. Un CONTRACARGO o cualquier otra acción responde 404. Prueba: `PagoEnLineaWebTest.desdeLaPaginaDelSimuladorNoSeProvocaUnContracargo`.
- **S4-B4. El webhook permitía enumerar colegios y no tenía límite.**
  - Responde 401 «aviso no autentico» también para un colegio o un proveedor inexistente.
  - Limita los avisos por IP (`cuentasclaras.pasarela.avisos-por-minuto-por-ip`, 60 por defecto; responde 429).
  - Pruebas: `PagoEnLineaWebTest.elWebhookSoloAceptaAvisosFirmados`, `LimiteAvisosPorIpTest`.

## Hallazgos de QA
- **QA-S4-1, 2 y 3 (contracargos):** ver S4-A3.
- **QA-S4-4. Un pago de recaudación salía CRÍTICO cuando todavía estaba en su ventana.**
  - El límite del faltante depende de quién registró el pago.
  - Un pago de `sistema.recaudacion` espera al día hábil siguiente, como su lote.
  - Prueba: `LimiteFaltantesTest.debeEsperarAlDiaHabilSiguienteParaUnPagoDeRecaudacionPorPago`.
- **QA-S4-5:** ver S4-A2.
- **QA-S4-6. Con `abono-recaudacion=POR_PAGO`, quien subió el lote confirmaba la pareja de su propio pago.** `ResponsablesPartida` agrega a quien subió el lote. Prueba: `SegregacionRecaudacionPorPagoQaTest`.
- **Pruebas `@Disabled`:** se activaron las 6. Ya no queda ninguna prueba de QA desactivada.
- **Mutación O1 (tope `> 0` frente a `>= 0`):** `PagosEnLineaQaTest.debeAceptarUnTotalDeExactamenteElMaximoYRechazarUnCentimoMas`. S/ 5,000.01 se rechaza y S/ 5,000.00 exactos crean la orden. Con la mutación, la prueba falla.
- **Mutación X3 (la nota de crédito no esperaba):** `OutboxOseQaTest.debeEsperarQueSuComprobanteSeaValidoAntesDeEnviarLaNotaDeCredito`.
  - Mientras la boleta está ENVIADA sin respuesta, la nota no se envía ni suma intentos.
  - Cuando el OSE acepta la boleta, la nota sale.
  - Con la mutación, la prueba falla.
- **Trazabilidad:**
  - La columna «Prueba» de la sección 14 de `sprint-4-cero-digitacion.md` se alineó con los nombres reales: 94 referencias, todas existentes.
  - Donde un control de la base no tiene prueba MySQL propia, se dice: `uk_pago_orden`, la cuota de otra familia en la orden y `ck_usuario_nombre_reservado`.
  - `TrazabilidadFraudeSprint4Test` falla si la sección vuelve a nombrar una prueba que no existe.

## Decisiones que cambian
Numeración de la sección 17 de `sprint-4-cero-digitacion.md`, ya actualizada.

| # | Antes | Ahora |
|---|---|---|
| 22 | La cadena de extractos se confirmaba con el saldo del último | Cada extracto pendiente se confirma con su propio saldo de cierre |
| 27 | Clave temporal entregada en persona | Enlace de un solo uso que vence en 48 horas; el apoderado elige su clave |
| 30 | Los cargos del extracto no se revisaban (salvo reembolsos) | Todo cargo sin pareja se explica (no lo explica quien subió el extracto); el que compensa un abono es CRÍTICO |
| 32 | Contracargo: anulación con devolución | Anulación de tipo CONTRACARGO, sin reembolso; la devolución de un pago en línea solo sale por la API de la pasarela |
| Nueva | — | Una pareja manual es del mismo monto y la aprueba otra persona en la bandeja; no se agrupa N:1 |
| Nueva | — | En el piloto, los pagos simulados no se aplican a cuotas; franja «PILOTO» en todas las páginas |

## Riesgos que quedan
- **Extracto de varios días:** dentro de un extracto que cubre varios días, solo se confirma a ciegas el saldo de cierre. Un abono y un cargo inventados que se compensan dentro del mismo extracto no cambian ese saldo. Lo mitigan la alerta CRÍTICA de compensación, la revisión de cargos por otra persona y el muestreo con semilla secreta. Se recomienda subir el extracto a diario.
- **Abono y cargo de montos distintos:** un abono inventado tapado con **varios** cargos de montos distintos no dispara la alerta de compensación. Igual queda como cargos sin explicar, que debe revisar otra persona.
- **Activación desde otra IP:** la alerta de «misma IP» no detecta a quien activa la cuenta desde otra conexión, por ejemplo su celular. Lo cubren el apoderado, que no puede entrar y avisa; el restablecimiento por Promotoría; y, desde el sprint 5, el envío del enlace directo al titular.
- **Muestreo de caja:** el muestreo de verificaciones de caja del sprint 3 (`AlertasCaja`) todavía usa la fecha como semilla. No era un hallazgo de esta ronda; queda anotado.
- **Columnas de uso del enlace:** con sus credenciales, `cc_app` podría reescribir `usado_en` de un enlace, porque tiene GRANT para marcarlo usado. Aun así, necesitaría el token, que solo existe en el enlace.
