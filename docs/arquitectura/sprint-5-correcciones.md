# Sprint 5 · Correcciones tras la auditoría antifraude y QA

Decisiones de la ronda de correcciones del sprint 5 «Familias y matrícula 2027». Cada corrección viene con una prueba que reproduce el ataque y que falla si se quita la corrección. Los ataques de la auditoría están en `fraude/AtaquesSprint5Test` (S5-A1, S5-M1, S5-M2 y S5-M3) y `fraude/AtaquesHuellaSprint5Test` (S5-M4). Sus aserciones «HUECO» se invirtieron y ahora dicen «CORREGIDO»: cada prueba en verde significa que el ataque ya no funciona.

Todo cambio de esquema va en **V20** (V17–V19 no se editan). También se actualizan `02-permisos-tablas.sql`, `03-triggers.sql` (**58 triggers** y la función `cc_contacto_normal`), `VerificadorPermisosBaseDatos`, el job `mysql` del CI y `docs/operacion/mysql-usuarios.md`. Ningún trigger nombra una tabla que no exista con V20.

**Probado (8 de octubre de 2026):** `./mvnw -B verify` en H2 (1696 pruebas, 0 fallos, 73 omitidas: las de MySQL) y lo mismo con `-DargLine=-Duser.timezone=America/Los_Angeles`. **NO probado en MySQL 8 real:** el Docker de la estación no pudo arrancar un contenedor nuevo (quedaron en «Created»). V20, `02`, `03` (58 triggers y `cc_contacto_normal`), las pruebas nuevas de `PermisosMySqlTest` y los pasos nuevos del job `mysql` deben correr en el CI (o en local con `mysql:8`) antes de fusionar.

## Crítico
- **S5-A1. Un alias de Gmail (o un segundo chip) de un empleado recibía los avisos de pago y el enlace del portal de una familia.**
  - **Verificación con un enlace de un solo uso:** todo contacto nuevo o cambiado (registro, importación, apoderado agregado o cambio aprobado) queda **pendiente**. La mensajería le envía a ESE contacto el mensaje `VERIFICACION_CONTACTO` (plantilla `cc_verificacion`). El token se genera en el envío (`alumnos.service.VerificacionesContacto`, solo desde `DespachoMensajes`, regla ArchUnit `verificacionesSoloDesdeDespachoMensajes`), vence en 71 h y en la base solo queda su SHA-256 (tabla `verificacion_contacto`, V20). El titular abre `/verificar/{colegio}/{token}` (página pública), confirma su número de documento y el contacto queda verificado (`apoderado.telefono_verificado` / `correo_verificado`); queda en la bitácora con su IP (`CONTACTO_VERIFICADO`).
  - **Mientras esté pendiente no recibe nada:** ni avisos financieros, ni recordatorios, ni el enlace del portal (`CreadorMensajes.apto`; en MySQL, `trg_mensaje_nace`). Cambiar el contacto lo vuelve a dejar pendiente (se verifica el VALOR, no un sí/no).
  - **Comparación normalizada:** `comun.texto.ContactoNormal` (y `cc_contacto_normal` en MySQL) quita lo que va desde el «+» en cualquier dominio, los puntos en Gmail, unifica `googlemail.com` y el 51 de los celulares. `UsuarioRepository.esContactoDelPersonal` y `quienTieneElContacto` comparan así.
  - **Registro con un contacto del personal pide aprobación:** `RegistroAlumnos.registrarApoderado` crea la solicitud `CAMBIO_CONTACTO_APODERADO` con los canales por aprobar (la prueba que citaba el diseño, `ServicioAlumnosTest.registrarApoderadoConCelularDelPersonalPideAprobacion`, ya existe).
  - **Aviso a los demás apoderados:** al agregar un apoderado (`ServicioFamilias.agregarApoderado` y el registro de un hermano), los apoderados activos de la familia reciben `APODERADO_AGREGADO`; al aprobarse un contacto nuevo, `CONTACTO_POR_VERIFICAR`. Los dos van por ambos canales.
  - **Alerta a las 48 h:** `comunicacion.service.AlertasContactos` avisa a Promotoría (CRÍTICA) los contactos sin verificar por más de 48 horas. La ficha de la familia muestra «Contacto sin confirmar» y el botón «Reenviar confirmación» (`ServicioFamilias.reenviarVerificacion`).
  - **Contactos anteriores a V20:** quedan verificados (ya recibían avisos). La alerta S5-M5 los revisa.
  - **Pruebas:** `AtaquesSprint5Test.s5A1_aliasDelCorreoDeLaCajeraRecibeElAvisoDePagoYElEnlaceDelPortal`; `VerificacionContactoTest` (3); `ServicioAlumnosTest.registrarApoderadoConCelularDelPersonalPideAprobacion`; `ContactoNormalTest` (4); `CreadorMensajesTest`; `PermisosMySqlTest.contactoSinVerificarNoRecibeNiSeVerificaPorSqlFallaCon1644`, `flujoVerificacionDeContactoConPermisosMinimos`, `aliasDelCorreoDelPersonalFallaCon1644`.

## Medio
- **S5-M1. Cualquier cambio de contacto aprobado blanqueaba el celular del personal.**
  - La aprobación guarda el contacto CONCRETO aprobado por canal (`apoderado.contacto_aprobado_telefono` / `contacto_aprobado_correo`, V20): solo los canales que cambiaron o que la solicitud pidió aprobar.
  - `CreadorMensajes` y `trg_mensaje_nace` exigen que un contacto del personal sea EXACTAMENTE el aprobado para su canal. `trg_apoderado_facturacion` exige que el contacto aprobado cambie solo con su solicitud aprobada y sea el registrado.
  - La bandeja de aprobación advierte («ATENCIÓN: el celular es de … (personal)») con `ManejadorContactoApoderado.advertencia`.
  - **Pruebas:** `AtaquesSprint5Test.s5M1_cambiarSoloElCorreoBlanqueaElCelularDelPersonal`; `ServicioAlumnosTest.registrarApoderadoConCelularDelPersonalPideAprobacion`; `PermisosMySqlTest.aprobarElCorreoNoAprueboElCelularDelPersonalFallaCon1644`.
- **S5-M2. Dirección cerraba la queja de la familia sobre la anulación que ella misma aprobó.**
  - `ServicioAvisosFamilia.atender` rechaza a quien registró el pago, pidió o aprobó su anulación, o pidió o aprobó el descuento de la cuota referida (ampliado con `ControlParticipantes`). El intento queda como `AUTOAPROBACION_RECHAZADA`. En MySQL lo exige `trg_aviso_familia_estado`.
  - Un aviso grave que no cerró Promotoría sigue en el inicio de Promotoría (CRÍTICA) durante 7 días (`AlertasFamilias`).
  - **Pruebas:** `AtaquesSprint5Test.s5M2_direccionCierraLaQuejaSobreLaAnulacionQueElMismoAprobo`; `CorreccionesSprint5Test.unAvisoGraveQueNoCerroPromotoriaSigueVisibleSieteDias`; `PermisosMySqlTest.avisoAtendidoPorQuienCobroFallaCon1644`.
- **S5-M3. Dirección sola congelaba un mes de alertas con días no laborables.**
  - El día nace **propuesto** (`feriado.pendiente`, V20) y no cuenta en `CalendarioHabil` hasta que lo aprueba OTRA persona: si lo propuso Promotoría, Dirección; y al revés (`ServicioFeriados.aprobar`, botón «Aprobar este día»).
  - Como máximo **3 por mes** (propuestos o aprobados) y **no más de 2 días hábiles seguidos** (los fines de semana y los feriados nacionales no cortan la racha).
  - La propuesta se avisa por mensaje a Promotoría (`FERIADO_PROPUESTO`, plantilla `cc_feriado_propuesto`) y queda resaltada (`FERIADO_PROPUESTO`, `FERIADO_APROBADO`).
  - En MySQL, `trg_feriado_registro` (nace propuesto, 3 por mes, no 3 días seguidos de calendario) y `trg_feriado_anulacion` (lo aprueba otra persona, una vez y antes de su fecha).
  - **Pruebas:** `AtaquesSprint5Test.s5M3_direccionCongelaUnMesDeAlertasConFeriados`; `ServicioFeriadosTest` (8); `PermisosMySqlTest.feriadoAprobadoPorQuienLoPropusoOFueraDeTopeFallaCon1644`.
- **S5-M4. La huella diaria no detectaba un recorte del mismo día ni el borrado de la huella guardada.**
  - **No retrocede:** la huella nueva exige una secuencia mayor o igual a la mayor ya guardada (diaria o por hora) o ya enviada por mensaje (`EnviosHuella`, implementada por `comunicacion.service.HuellasEnviadas`). Si es menor: `HUELLA_RETROCEDIO`, no se guarda y Promotoría recibe «Bitácora verificada: NO». En MySQL, `trg_huella_bitacora_registro` y `trg_huella_hora_registro`.
  - **Faltan días:** si falta una huella entre la última y la de ayer, o si la huella que salió por mensaje ya no está guardada: `HUELLA_FALTAN_DIAS` (CRÍTICA).
  - **El mensaje de hoy repite la huella anterior** (`cc_huella` con 5 parámetros: «Huella anterior: evento …, código … del …»).
  - **Huellas por hora (factible):** tabla `huella_hora` (V20, solo inserción) y `HuellaDiaria.cadaHora` (lunes a sábado, de 08:00 a 19:00). La reverificación de la mañana también las compara.
  - **Pruebas:** `AtaquesHuellaSprint5Test` (2); `PermisosMySqlTest.huellaQueRetrocedeFallaCon1644`.
- **S5-M5. Faltaban las alertas de contactos.** `AlertasContactos` (Promotoría), con la normalización: CRÍTICA si el mismo celular o correo está en dos familias; CRÍTICA si un contacto del personal no está aprobado; ATENCIÓN si está aprobado (personal que también es padre o madre). Prueba: `VerificacionContactoTest.elMismoContactoEnDosFamiliasEsAlertaAunqueSeaUnAlias`.

## Bajo
- **S5-B1 (= QA-S5-1). Si el WhatsApp se omitía por G6, la familia no recibía nada.** Ahora el aviso cae al correo (si está verificado y no es del personal sin aprobar). Pruebas: `CreadorMensajesQaTest.debeAvisarPorCorreoCuandoElCelularRegistradoEsDelPersonalSinAprobacion` (sin `@Disabled`); `CorreccionesSprint5Test.sinWhatsappPorG6ElAvisoSalePorCorreo`; `AtaquesSprint5Test.s5M1_…` (control).
- **S5-B2. `pagosSinAvisoEnviado` se conformaba con un aviso a cualquiera.** Ahora exige uno que SALIÓ a cada responsable de pago activo de los alumnos pagados, y la ventana pasa de 7 a 35 días. Prueba: `CorreccionesSprint5Test.unPagoSinAvisoAUnoDeSusResponsablesEsCritico`.
- **S5-B3. `AlertasHuella` daba por buena una huella guardada aunque su mensaje no hubiera salido.** Ahora, después de las 07:00, la huella de ayer sin un mensaje `HUELLA_BITACORA` ENVIADO (o ENTREGADO o LEIDO) es CRÍTICA. Prueba: `CorreccionesSprint5Test.laHuellaGuardadaSinMensajeEnviadoEsCritica`.

## Hallazgos de QA
- **QA-S5-1:** ver S5-B1.
- **QA-S5-2. Un POST sin el campo decidía por la familia.** `PortalFamiliaController.responder` y `preferencias` ya no tienen `defaultValue = "false"`: sin el campo, no cambian nada y muestran «No recibimos tu respuesta». Pruebas: `RespuestasPortalQaTest.unPostSinRespuestaNoRegistraQueElAlumnoNoContinua`, `unPostSinValorNoApagaLosRecordatorios`.
- **QA-S5-3. El despacho enviaba un recordatorio de cuotas ya pagadas.** `DespachoMensajes` vuelve a mirar las cuotas de esa familia y fecha antes de enviar; si ya no hay saldo, el recordatorio queda FALLIDO con «No se envió: las cuotas de ese vencimiento ya se pagaron» (sin respaldo ni alerta: no es un aviso financiero). Prueba: `RecordatoriosQaTest.noDebeEnviarseElRecordatorioDeCuotasQueYaSePagaron`.
- **QA-S5-4. Un no laborable registrado tarde hacía perder el recordatorio.** `ServicioRecordatorios` se pone al día: el «3 días antes» sale el primer día de mensajes desde el que le tocaba y hasta el vencimiento; el «vencida», hasta 5 días después del día hábil siguiente. No se repite (clave por familia, tipo y fecha). Prueba: `RecordatoriosQaTest.unNoLaborableRegistradoTardeNoHacePerderElRecordatorio`.
- **QA-S5-5. `PlantillaMensaje.componer` reemplazaba en cadena.** Ahora es una sola pasada (`Matcher.appendReplacement`): un «{{5}}» escrito en el motivo queda tal cual. Prueba: `MensajeYPlantillasQaTest.unMarcadorDentroDelMotivoNoSeReemplaza`.
- **Pruebas `@Disabled`:** se activaron las 6. Ya no queda ninguna prueba de QA desactivada.

## Desviaciones (cada una con su motivo)
1. **El «código de un solo uso» es un enlace.** Como el de activación: el token de 32 bytes se genera en el envío, va en el botón de la plantilla y solo queda su SHA-256. Quien lo usa confirma el número de documento. Un código numérico corto obligaba a escribirlo en otra pantalla y era más fácil de adivinar.
2. **Las pruebas de escenario confirman los contactos por SQL** (`EscenarioEscolar.contactosConfirmados`, solo en H2): los escenarios de caja y de renovación no tratan la verificación, y sin ella sus familias no reciben avisos. También quita los mensajes de verificación del registro para que cada prueba cuente los suyos. La verificación real se prueba en `VerificacionContactoTest` y en MySQL.
3. **Las pruebas de ataque cambian su guion donde la corrección corta el camino.** S5-M1: el registro con el celular de la cajera ya pide aprobación, así que la prueba la rechaza antes de pedir el cambio de correo. S5-M4 (a): la prueba toma la huella de la hora entre el fraude y el recorte (el recorte dentro de la MISMA hora sigue siendo residual). S5-M3: cuenta los días rechazados por los topes.
4. **`mensajePendiente` y `mensajeConTokenFallaCon3819` de `PermisosMySqlTest` usan un mensaje a una persona del personal** (`HUELLA_BITACORA`): desde V20 un mensaje a un apoderado sin verificar falla con 1644 antes de llegar al CHECK.
5. **El tope de días seguidos en MySQL cuenta días de calendario** (no 3 fechas seguidas); la aplicación es más estricta y cuenta días hábiles (un viernes y el lunes siguiente son seguidos).
6. **Un recordatorio de cuotas ya pagadas queda FALLIDO**: los estados del mensaje no tienen «cancelado» y agregarlo cambiaba V17 y `trg_mensaje_envio`. El texto del error lo explica.

## Decisiones que cambian
| # | Antes | Ahora |
|---|---|---|
| 48 | Un contacto del personal recibe avisos si otra persona aprobó algún cambio de contacto | Solo el contacto concreto aprobado, por canal; todo contacto nuevo se verifica con un enlace de un solo uso |
| 49 | Huella diaria a las 06:00 | Además, huella por hora en horario de caja; la secuencia no retrocede; el mensaje repite la huella anterior |
| 52 | «¿Algo no cuadra?» lo atienden Promotoría o Dirección | No lo atiende quien participó; uno grave que no cerró Promotoría sigue visible 7 días |
| 59 | Días no laborables: Promotoría o Dirección, solo a futuro | Los propone uno y los aprueba el otro; 3 por mes y 2 días hábiles seguidos como máximo; aviso por mensaje a Promotoría |
| Nueva | — | Contactos comparados normalizados (alias de correo y formato del celular) |

## Riesgos que quedan
- **Quien controla el contacto lo verifica.** Si un empleado registra un segundo chip propio (que no es de nadie del personal), recibe y usa el enlace de verificación. Lo mitigan: el aviso a los demás apoderados, el enlace que pide el documento del apoderado, la alerta de contacto repetido en dos familias y el portal (la familia real no puede entrar). Una familia con un solo apoderado y sin portal no tiene quién avise.
- **Contactos anteriores a V20:** quedan verificados por la migración. La alerta S5-M5 revisa los que coinciden con el personal o se repiten.
- **Recorte dentro de la misma hora:** un DBA que recorta la bitácora antes de la siguiente huella de la hora (o fuera del horario de caja, por ejemplo de noche) sigue sin ser detectado por la base; lo detecta Promotoría al comparar el mensaje de la mañana («Huella anterior») con el de ayer. Las huellas por hora no salen por mensaje (costo de WhatsApp): un DBA que las borre junto con la cola solo queda en evidencia si se envió una huella diaria posterior.
- **Borrado de los mensajes:** quien tiene acceso de DBA también puede borrar las filas de `mensaje`. Lo que protege es la copia fuera del sistema (el celular de Promotoría y el correo externo del contador).
- **El tope de feriados es por colegio y por mes:** con colusión entre Promotoría y Dirección se pueden aprobar 3 días al mes. Quedan resaltados en la bitácora.
- **Normalización de correos:** cubre «+» en todos los dominios y los puntos en Gmail; otros proveedores con reglas propias (por ejemplo, alias con «-» en Yahoo) no se normalizan.
