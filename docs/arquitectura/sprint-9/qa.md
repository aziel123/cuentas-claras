# Sprint 9 · QA de asistencia y comunicados

> Rama `claude/sprint-9-qa`, creada desde `5df49f4` (HEAD de `claude/sprint-9-asistencia-comunicados`, PR #18 con el CI
> en verde). Agente `qa-tester`, 22 de octubre de 2026. Se leyeron `CLAUDE.md` («Método por sprint»), las skills
> `contexto-colegio` y `crear-modulo-spring`, los briefs `tanda-2.md` y `tanda-3.md` y las tres secciones
> «Implementación» del diseño. **El brief `docs/arquitectura/sprint-9/tanda-1.md` no existe** en la rama (la tanda 1 se
> hizo antes del método por sprint); para la tanda 1 se usó su sección «Implementación».

## 1. Resumen

- **3 errores corregidos**, cada uno con su prueba que falla sin la corrección (QA-S9-1 a QA-S9-3, severidad media).
- **8 hallazgos pendientes** (1 media, 5 bajas y 2 informativos), sin corregir porque necesitan una decisión del usuario o
  tocan reglas que no son evidentes.
- **38 pruebas nuevas** (15 + 8 + 15 ejecuciones) en tres clases (`QaSprint9AsistenciaTest`, `QaSprint9ComunicadosTest` y
  `QaSprint9FiltroContenidoTest`), una por escenario.
- Resultados de `./mvnw -B verify` y de MySQL real: sección 5.

## 2. Escenarios cubiertos (Dado / Cuando / Entonces)

Los marcados con **(nueva)** son pruebas de este QA; los demás ya estaban cubiertos y se revisaron.

### 2.1 Asistencia: docente asignado, corrección del día, justificación

| Escenario | Prueba |
|---|---|
| Dado un docente de 5.° B, cuando corrige (directo o por solicitud) una marca de 5.° A, entonces 404 y la marca no cambia | `debeResponder404AlCorregirUnaMarcaDeOtraSeccion` **(nueva)** |
| Dado un docente de 5.° B, cuando pide el registro tardío de 5.° A, entonces 404 | `debeResponder404AlPedirElRegistroTardioDeOtraSeccion` **(nueva)** |
| Dada la hoja del día, cuando trae una FALTA_JUSTIFICADA, entonces se rechaza y no queda un registro a medias (A1) | `debeRechazarUnaHojaQueTraeUnaFaltaJustificada` **(nueva)** |
| Dada una falta de hoy, cuando la tutora la corrige directo a FALTA_JUSTIFICADA, entonces se rechaza (A1) | `debeRechazarLaCorreccionDelDiaHaciaFaltaJustificada` **(nueva)** |
| Dada una hoja que repite un alumno, cuando se guarda, entonces se rechaza | `debeRechazarUnaHojaQueRepiteUnAlumno` **(nueva)** |
| A las 15:59 de Lima la corrección del día pasa; a las 16:00 (segundo aviso) se rechaza (A2) | `debeCerrarLaCorreccionDelDiaALaHoraDelSegundoAvisoEnHoraDeLima` **(nueva)** |
| Dada una falta ya justificada, cuando la familia la justifica otra vez, entonces se rechaza | `debeRechazarJustificarUnaFaltaDeHoyDosVeces` **(nueva)** |
| Solo hoy, sección ajena 404, Dirección no registra (E1, E2, E49), 30 alumnos con una firma | `RegistroAsistenciaTest` |
| Corrección después del aviso, lista de Dirección, otro día por la bandeja, después del cierre, registro tardío | `CorreccionAsistenciaTest` |
| Otra familia 404, el docente no revierte, fuera de plazo, lo revertido no se justifica, la nota solo al tutor | `JustificacionTest` |

### 2.2 Fechas, feriados y la hora de Lima frente a UTC

| Escenario | Prueba |
|---|---|
| Dado el jueves 8 de octubre (Combate de Angamos), cuando la tutora registra, entonces se rechaza «feriado» | `debeRechazarElRegistroEnUnFeriadoNacional` **(nueva)** |
| Dado el martes a las 21:00 de Lima (miércoles 02:00 UTC), cuando se registra, entonces la fecha es el martes | `debeRegistrarConLaFechaDeLimaAunqueEnUtcYaSeaElDiaSiguiente` **(nueva)** |
| Dada una falta del martes 6, cuando la familia justifica el martes 13, entonces se acepta (el feriado no cuenta: 4 días hábiles) | `debeAceptarLaJustificacionDelQuintoDiaHabilSinContarElFeriado` **(nueva)** |
| Dada la misma falta, cuando justifica el miércoles 14 (5 días hábiles después), entonces se rechaza | `debeRechazarLaJustificacionDelSextoDiaHabil` **(nueva)** |
| La suite corre también con `-Duser.timezone=America/Los_Angeles` (CI): las pruebas nuevas usan el `RelojAjustable` | todas |

### 2.3 Cobertura con `hasta`

| Escenario | Prueba |
|---|---|
| Dada una cobertura hasta el miércoles, cuando el docente abre la hoja el miércoles 23:59 de Lima, entonces la ve; el jueves 00:00, 404 | `debeDarAccesoLaCoberturaHastaElUltimoMinutoDeSuDiaEnHoraDeLima` **(nueva)** |
| Dada una cobertura vencida (sin que corra `FinAsignaciones`), cuando el docente guarda la hoja, entonces 404 | `debeImpedirQueUnaCoberturaVencidaRegistreLaAsistencia` **(nueva)** |
| Termina sola; más de 5 días hábiles, de tutor o en el pasado se rechaza (E56) | `CoberturaTest` |

### 2.4 Avisos con marca de agua

| Escenario | Prueba |
|---|---|
| Dado un colegio con asistencia del lunes y sin ningún envío previo, cuando corre su primer envío el martes, entonces sale solo lo de hoy, sin «correcciones» del lunes (QA-S9-3) | `debeAvisarSoloLaAsistenciaDeHoyEnElPrimerEnvioDeUnColegioSinMarcaDeAgua` **(nueva)** |
| Dado un envío previo, cuando la familia justifica después del último envío, entonces la corrección sale al día siguiente (control de QA-S9-3) | `debeAvisarAlDiaSiguienteLoCambiadoDespuesDelUltimoEnvioCuandoYaHuboUno` **(nueva)** |
| Uno por familia y día con hermanos, horario, WhatsApp con consentimiento, oposición al correo, A1, A2, segundo envío | `AvisosAsistenciaTest` |
| Familia con deuda recibe el mismo aviso (INDECOPI, E39) | `AsistenciaSinDeudaTest` |

### 2.5 Comunicados: aprobación, fe de erratas, filtro y portal

| Escenario | Prueba |
|---|---|
| Dado un comunicado con «deuda» y «pensión» escritas con letras armenias, cuando Dirección lo envía, entonces se rechaza y queda en la bitácora (QA-S9-1) | `debeRechazarUnComunicadoDeCobranzaEscritoConLetrasArmeniasYDejarloEnLaBitacora` **(nueva)** |
| Letras parecidas que no se normalizaban: ı, ɩ, ɑ, ʋ, versalitas, palochka cirílica, օ y ս armenias, relleno hangul y blanco braille (QA-S9-1) | `QaSprint9FiltroContenidoTest` (10 casos) **(nueva)** |
| Lo académico sigue pasando con la tabla ampliada («padres/madres», «7.45 a.m.», «12:30 h») | `debeSeguirAceptandoLoAcademicoConLaTablaAmpliada` **(nueva)** |
| Dada Rosa con el «sí» a comunicados por WhatsApp sin marcar «principal», cuando sale un urgente, entonces le llega por WhatsApp; a Pedro (sin «sí»), por correo (QA-S9-2) | `debeEnviarElUrgentePorWhatsAppAQuienDioElSiAunqueNoLoMarcoComoPrincipal` **(nueva)** |
| Sin ningún «sí» a WhatsApp en la familia, el urgente va por correo | `debeEnviarPorCorreoElUrgenteSiNadieDeLaFamiliaAceptoWhatsApp` **(nueva)** |
| Con contacto principal marcado, va a él por WhatsApp | `debeRespetarAlContactoPrincipalMarcado` **(nueva)** |
| Dado un comunicado retirado, cuando la familia confirma la lectura, entonces se rechaza y no queda confirmación | `debeRechazarLaConfirmacionDeLecturaDeUnComunicadoRetirado` **(nueva)** |
| Dado un comunicado con PDF de docente POR_APROBAR, cuando Dirección intenta su fe de erratas, entonces se rechaza | `debeRechazarLaFeDeErratasDeUnComunicadoQueTodaviaNoSalio` **(nueva)** |
| Dado un comunicado POR_APROBAR, cuando la familia lo abre o baja su PDF, entonces 404 | `debeResponder404ALaFamiliaQueAbreUnComunicadoPorAprobar` **(nueva)** |
| Dado un comunicado de otro docente, cuando la tutora envía su fe de erratas, entonces 404 | `debeResponder404AlDocenteQueCorrigeElComunicadoDeOtroDocente` **(nueva)** |
| Emisor real, sección ajena, auxiliar, cobranza, PDF de docente y de imagen, pie, fe de erratas a todos, retirar no borra, huella | `ComunicadosTest` |
| Proceso hijo (PID, memoria, red, entorno, tmp), 25 PDF hostiles, cola y límites | `AnalizadorPdfTest`, `AnalizadorHostilesTest`, `ColaDelAnalizadorTest`, `AdjuntoComunicadoTest` |
| Portal: otra familia 404, CSRF, constancia, cabeceras, adjunto no enviado 404 | `PortalComunicadosTest` |
| Nómina SIAGIE: solo Dirección, registro de cada descarga, ceros del DNI, sin fórmulas, usuario y fecha | `NominaSiagieTest` |

### 2.6 Multi-colegio e IDOR

Revisado sin pruebas nuevas: todas las búsquedas de asistencia y comunicados pasan por repositorios JPA con `@TenantId`
(sin SQL nativo), la marca de agua tiene la clave del colegio y `RutasIdorTest` recorre las rutas nuevas de las tres
tandas con `?seccion=`, `?alumno=` y `?apoderado=`. Entre secciones y familias del mismo colegio: las pruebas nuevas de 2.1 y
2.5 y las ya existentes.

## 3. Hallazgos

| Id | Severidad | Estado | Hallazgo |
|---|---|---|---|
| QA-S9-1 | Media | Corregido | El filtro de contenido no llevaba a la latina varias letras parecidas |
| QA-S9-2 | Media | Corregido | Sin contacto principal marcado, nadie de la familia recibía comunicados por WhatsApp (ni los urgentes) |
| QA-S9-3 | Media | Corregido | El primer envío de avisos de un colegio mandaba como «corrección» toda la asistencia anterior |
| QA-S9-4 | Media | Pendiente | La situación de matrícula del SIAGIE no sigue el diseño (REENTRANTE, RETIRADA y RESERVADA) |
| QA-S9-5 | Baja | Pendiente | Un monto en letras después de «S/» pasa el filtro |
| QA-S9-6 | Baja | Pendiente | La alerta de registro incompleto cuenta marcas, no alumnos |
| QA-S9-7 | Baja | Pendiente | Los plazos «5 días hábiles» se cuentan de tres maneras |
| QA-S9-8 | Baja | Pendiente | Carrera en el primer envío: una marca corregida mientras se prepara sale como «corrección» de algo no avisado |
| QA-S9-9 | Baja | Pendiente | Un PDF de docente aprobado después de que el docente perdió la sección sale igual |
| QA-S9-10 | Info | Pendiente | Orden de la nómina y textos menores |
| QA-S9-11 | Info | Pendiente | Falta el brief de la tanda 1 |

### QA-S9-1 · Media · Corregido: letras parecidas que el filtro no normalizaba (B4)
`FiltroContenido` mapeaba cirílico y griego, pero no la ı sin punto ni la iota latina (ɩ), la alfa latina (ɑ), la ʋ, las
versalitas fonéticas (ᴘᴇɴsɪᴏɴ), la palochka cirílica (ӏ), la һ cirílica ni las armenias que parecen latinas (օ, ս, ո…).
Tampoco quitaba las «letras en blanco» (relleno hangul U+3164 y blanco braille U+2800), que no son `\p{Cf}`. Con ellas
pasaban «La pensiօn vence el viernes», «Su deսda pendiente», «Hagan su pɑgo», «La ᴘᴇɴsɪᴏɴ de octubre», «pӏin» y
«pago» partido con un U+3164 (comprobado contra las clases de `5df49f4`). La segunda capa de MySQL (`trg_comunicado_nace`) no normaliza, así
que tampoco los paraba. **Corrección:** la tabla de homoglifos suma esas letras (con un control de que origen y destino
tengan el mismo largo) y `normalizar` quita las letras en blanco. Lo guardado no cambia (solo se normaliza para revisar).
**Pruebas:** `QaSprint9FiltroContenidoTest` (9 de los 10 casos fallaban sin la corrección; el de la ı sin punto ya lo
paraba `UNICODE_CASE` y queda como regresión) y `debeRechazarUnComunicadoDeCobranzaEscritoConLetrasArmenias…`.
**Riesgo residual:** sigue siendo una lista; un alfabeto no listado (cheroqui, lisu) o un monto en una imagen solo lo ve el
otro órgano.

### QA-S9-2 · Media · Corregido: sin contacto principal, nadie recibía por WhatsApp
La desviación 6 de la tanda 3 dice: «el contacto principal es el último consentimiento de COMUNICADOS marcado como
principal; **sin él, el primer apoderado (por id) que recibe**». `AvisosComunicadosFamilias.enviar` no tenía ese respaldo:
si nadie marcó «principal» (la casilla es opcional en `/familia/avisos`), `principal` era falso para todos y el resumen y
**los urgentes** («Suspensión de clases mañana») iban solo por correo, aunque el apoderado hubiera dado el «sí» a WhatsApp.
Lo mismo si el principal marcado ya no estaba activo. **Corrección:** el principal marcado solo vale si está entre los
apoderados activos de la familia; si no hay, es el primer apoderado activo (por id) con el «sí» a comunicados por WhatsApp.
`trg_mensaje_nace` ya lo permitía (exige el consentimiento, no la marca de principal). **Pruebas:**
`debeEnviarElUrgentePorWhatsAppAQuienDioElSiAunqueNoLoMarcoComoPrincipal` (fallaba: «CORREO»), más dos de control.

### QA-S9-3 · Media · Corregido: el primer envío mandaba la historia como «corrección»
`AvisosAsistenciaFamilias.pasada` toma los cambios después de la marca de agua del colegio; sin fila en
`aviso_asistencia_corte` la marca es 0. Si el colegio registró asistencia antes de su primer envío (en preparación, que no
recibe avisos, o con `avisos-activos: false`), la primera pasada en producción mandaba un `ASISTENCIA_CORREGIDA` **por cada
falta o tardanza de cada día anterior** («antes: sin registro»): cientos de WhatsApp pagados y familias confundidas.
**Corrección:** mientras el colegio no tenga ningún envío (`ultimoEnvio` nulo), la pasada no manda como corrección los
cambios de días anteriores; sí sube la marca de agua. Con un envío previo, nada cambia (A2). **Pruebas:**
`debeAvisarSoloLaAsistenciaDeHoyEnElPrimerEnvio…` (fallaba: 1 corrección del lunes) y su control
`debeAvisarAlDiaSiguienteLoCambiadoDespuesDelUltimoEnvioCuandoYaHuboUno`.

### QA-S9-4 · Media · Pendiente: la situación de matrícula del SIAGIE no sigue la sección 3.11
`NominaSiagie.situacion` decide con la matrícula del año anterior **en cualquier estado** (`delAnioParaAlumnos` no filtra):
- una matrícula **RESERVADA** del año anterior (nunca estudió) cuenta como si hubiera cursado: sale PROMOVIDO o REPITENTE;
- una **RETIRADA** a mitad de año sale PROMOVIDO si ahora está en el grado siguiente;
- **REENTRANTE** («tuvo matrícula antes, no el año anterior», sección 3.11) nunca se calcula así: un alumno de 2024 que
  vuelve en 2026 sale INGRESANTE; en cambio sale REENTRANTE quien saltó un grado o bajó;
- si hay dos matrículas del año anterior, se queda con la primera (`(x, y) -> x`), sin criterio.

No se corrige: los códigos y la definición exacta de cada situación dependen de la plantilla real del SIAGIE (D12, sin
confirmar) y la nómina sale como PROVISIONAL. **Propuesta:** contar solo matrículas ACTIVAS del año anterior, tratar una
RETIRADA como interrupción (REENTRANTE) y buscar matrículas de años más antiguos para REENTRANTE; con pruebas de los cuatro
casos (hoy `situacion` no tiene ninguna).

### QA-S9-5 · Baja · Pendiente: «S/ trescientos» pasa
La regla de soles exige una cifra después de «S/» (B7, para que «padres/madres» pase). Un monto en letras («Abonen S/
trescientos») no lo para ninguna regla de Java ni de la base. Ampliar a `s/` + letra rompería «Av. Lima s/n», muy común en
direcciones. **Propuesta:** decidir con el usuario si se agregan los números en letras («cien», «doscientos»…) junto a «S/»
o «soles».

### QA-S9-6 · Baja · Pendiente: la alerta de registro incompleto cuenta, no compara
`AlertasAcademicas.seccionesSinAsistencia` compara cuántas marcas iniciales tiene el registro con cuántas matrículas ACTIVAS
hay ahora (A4). Si el mismo día se retira un alumno ya marcado y se matricula otro, los números coinciden y el alumno nuevo
queda sin marca y sin alerta. **Propuesta:** comparar los conjuntos de alumnos (matrículas activas sin marca inicial).

### QA-S9-7 · Baja · Pendiente: tres maneras de contar «5 días hábiles»
- Justificación: «los últimos 5 contando el de la falta» (`habilesEntre(falta, hoy) < 5`: el 5.° día hábil después ya no).
- Registro tardío: `habilesEntre(fecha, hoy) > 5` se rechaza: un día de hace 5 días hábiles **sí** se acepta.
- Cobertura: `habilesEntre(hoy, hasta) > 5` se rechaza: da acceso hoy y 5 días hábiles más (6 días de clases).

Cada una es defendible, pero la pantalla dice lo mismo («5 días hábiles») en las tres. **Propuesta:** fijar una sola regla
con el usuario y alinear los textos; las pruebas nuevas de 2.2 fijan hoy la de la justificación.

### QA-S9-8 · Baja · Pendiente: carrera dentro de la pasada del primer envío
La marca de agua se lee antes que las marcas (desviación 6 de la tanda 2). Si un docente corrige una marca **entre** esas
dos lecturas, la vigente tiene un id mayor que la marca de agua y se descarta del primer envío, y la anterior ya no es
vigente: el alumno no sale en el aviso del día. En el segundo envío sale como «corrección» de un estado que la familia nunca
recibió. Ventana de milisegundos; la familia igual recibe el estado correcto. **Propuesta:** en el primer envío, para un
alumno cuya vigente supera la marca de agua, usar la última marca de su cadena que no la supera.

### QA-S9-9 · Baja · Pendiente: PDF de docente aprobado tras perder la sección
`ManejadorPublicarComunicado.aplicar` vuelve a comprobar el órgano que aprueba, pero no que el docente que lo escribió siga
con su asignación VIGENTE en la sección (sí lo hacen las solicitudes de asistencia, A13). Un comunicado que quedó por
aprobar sale a la sección aunque el docente ya no enseñe allí. **Propuesta:** al aprobar un comunicado de DOCENTE, exigir
la asignación vigente o avisarlo en la tarjeta de la bandeja.

### QA-S9-10 · Info · Pendiente: orden de la nómina y textos menores
- La nómina ordena por apellido paterno y nombres con `String.compareTo`: ignora el apellido materno y deja «Ávila» o
  «Ñique» después de la «Z». El número de orden que va al SIAGIE sale de ese orden. **Propuesta:** `Collator` es-PE con
  los dos apellidos.
- `DiaDeClases.sinClases` dice «Hoy no hay clases: es fin de semana» también para un registro tardío de un sábado pasado.
- `GET /familia/comunicados/{id}` escribe `abierto_en` (un GET con efecto); aceptable porque es idempotente y solo lo hace
  la familia destinataria.

### QA-S9-11 · Info · Pendiente: falta el brief de la tanda 1
El encargo pedía leer `docs/arquitectura/sprint-9/tanda-1.md`; no existe (la tanda 1 se implementó el 14 y 15 de octubre,
antes del «Método por sprint»). Su alcance está en la sección «Tanda 1 · Implementación» del diseño.

## 4. Qué quedó pendiente

- Los hallazgos QA-S9-4 a QA-S9-11 (sección 3), para decidir con el usuario.
- **Sin probar en este QA:** la carga de 40 secciones en el mismo minuto (pendiente desde la tanda 2), las 10 bombas
  seguidas en la imagen real (las cubre el job `demo`), E57 en MySQL (pendiente de la tanda 2) y un recorrido de
  usabilidad del portal en el celular.
- **Fuera del alcance de QA:** la plantilla real del SIAGIE (D12) y el reemplazo del `SecurityManager` al subir de Java.

## 5. Resultados de la verificación

- **Las pruebas nuevas contra el código de `5df49f4`** (las correcciones quitadas a mano): 38 pruebas, **13 fallos**, que
  son exactamente las de las tres correcciones (10 de `QaSprint9FiltroContenidoTest`, 2 de `QaSprint9ComunicadosTest` y 1
  de `QaSprint9AsistenciaTest`); las otras 25 pasan también sin ellas (cubren reglas que ya estaban bien).
- **Con las correcciones**, las clases nuevas y las vecinas (`FiltroContenidoTest`, `ContenidoSinCobranzaTest`,
  `AvisosAsistenciaTest`, `CorreccionAsistenciaTest`, `AvisosComunicadosTest`): 143 pruebas, 0 fallos.
- **`./mvnw -B verify`** (una vez, en local, sobre `27b6349`): **2801 pruebas, 0 fallos, 0 errores, 199 omitidas** (las de
  MySQL real); BUILD SUCCESS. La tanda 3 cerró con 2763: las 38 nuevas.
- **MySQL real** en un contenedor propio (`cc-qa-s9-mysql`, `mysql:8`, puerto 3321), como el job `mysql`: usuarios con
  `01`, V1–V34 con `cc_migrador` (`java -jar … migrar`), `02`, `03` (**98 triggers**) y el segundo colegio; después, en el
  orden del job: `MigracionMySqlTest` 2, `PermisosMySqlTest` 94 (7 omitidas por la fecha), `AuditoriaSprint8MySqlTest` 18,
  `AuditoriaSprint9MySqlTest` 5, `AuditoriaSprint9AsistenciaMySqlTest` 13 y `AuditoriaSprint9ComunicadosMySqlTest` 38:
  **0 fallos**. Las correcciones no tocan la base: QA-S9-2 crea WhatsApp que `trg_mensaje_nace` ya aceptaba (exige el
  consentimiento vigente, que es lo que lee el respaldo nuevo). El contenedor se borró al terminar. No se tocaron
  `cuentas-claras-mysql-1`, `cuentas-claras-app-1`, `smarthr-*`, `supabase_*`, `cc-mysql-s5*` ni el puerto 8098.
- La suite con `-Duser.timezone=America/Los_Angeles` no se corrió en local: la corre el CI del PR en cada push.
