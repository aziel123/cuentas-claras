# Sprint 11 · QA del ciclo de vida del colegio

> Rama `claude/sprint-11-qa`, creada desde `0798691` (HEAD de `claude/sprint-11-ciclo-colegio`, PR #22 con el CI en verde).
> Agente `qa-tester`, 30 de octubre de 2026. Se leyeron `CLAUDE.md` («Método por sprint»), las skills `contexto-colegio` y
> `crear-modulo-spring`, los briefs `tanda-1.md`, `tanda-1b.md`, `tanda-1b-interfaz.md`, `tanda-1c.md`, `tanda-2.md` y
> `tanda-2-pendientes.md`, y las secciones «Implementación» del diseño (en especial las desviaciones de la tanda 2).

## 1. Resumen

- **1 error corregido (alto), QA-S11-01:** el enlace nuevo que manda el operador tras 5 intentos fallidos **nacía bloqueado**
  (0 intentos): un solo error de tipeo del titular lo volvía a anular. Corregido en la aplicación y en el trigger de MySQL,
  con 5 pruebas en H2 y 1 en MySQL real. Las pruebas de H2 se corrieron contra `0798691` y fallaron (sección 5).
- **Hallazgos pendientes:** sección 4 (sin corregir: necesitan una decisión o no son errores evidentes).
- **Pruebas nuevas:** `QaSprint11EntregaTest` (5), `QaSprint11CicloCompletoTest` (1, el ciclo completo con una familia real),
  `GuiaPreparacionWebTest.ningunIrPintadoEnLaPuestaEnMarchaDa403ANingunRol` y
  `AuditoriaSprint11FinColegioMySqlTest.entregaNuevaTrasCincoIntentosEmpiezaEnCeroYSinEnlaceNuevoNoBaja` (MySQL real).
- Resultados de `verify`, de MySQL real y de la demo persistente: sección 5.

## 2. Escenarios (Dado / Cuando / Entonces)

Los marcados con **(nueva)** son pruebas de este QA; los demás ya estaban cubiertos y se revisaron.

### 2.1 Alta de un colegio de punta a punta

| Escenario | Prueba |
|---|---|
| Dado un operador, cuando da de alta un colegio, entonces nace en preparación, sin año, con su titular (primera Promotoría) y el correo de control | `AltaACobroTest`, **`QaSprint11CicloCompletoTest` (nueva)** |
| Dado el titular activado, cuando crea el año con su firma y pide un código para la cuenta de Dirección, entonces el código queda atado a esa cuenta y no sirve para otra ni dos veces (A1c-5) | `AltaACobroTest` |
| Dada Administración, cuando crea secciones, registra un alumno y propone pensiones, entonces Dirección (o el titular, decisión 215) aprueba y se genera el cronograma | `AltaACobroTest`, **`QaSprint11CicloCompletoTest`** |
| Dados los 16 pasos (14 obligatorios), cuando falta la autorización de conservación (paso 16), entonces no se pasa a producción; solo el titular la firma | `AutorizacionConservacionTest`, `GuiaPuestaEnMarchaTest`, `PasoAProduccionTest` |
| Dado el colegio en producción, cuando Caja cobra en efectivo, entonces hay pago y comprobante; el cierre con faltante lo aprueba otra persona | `AltaACobroTest`, **`QaSprint11CicloCompletoTest`** |

### 2.2 Promotoría aplica directo (1c)

| Escenario | Prueba |
|---|---|
| Dada una acción sensible, cuando la Promotoría aplica directo, entonces su código va atado a esa acción | `AuditoriaSprint11PromotoriaMySqlTest`, `ConectorEsperaTest` |
| Dado un cambio de cuenta bancaria, cuando se aplica directo, entonces espera 24 h (con freno del operador y del socio con más de 30 días); el socio con menos de 30 días no cuenta | `ConectorEsperaTest`, `AuditoriaSprint11PromotoriaMySqlTest` |
| Dado el contacto de una cuenta directiva, cuando se pide cambiarlo, entonces nunca cambia directo (dos operadores) | `AuditoriaSprint11PromotoriaMySqlTest`, `ContactoPersonalTest` |
| Dado quien cambió la cuenta bancaria, cuando intenta confirmar la conciliación en 30 días, entonces se rechaza y avisa a los operadores (A1c-4) | `AuditoriaSprint11PromotoriaMySqlTest`, `ResumenDiarioTest` |

### 2.3 Guía y textos (1b)

| Escenario | Prueba |
|---|---|
| Dado cada rol del personal (Promotoría, Dirección, Administración, Caja y Docente), cuando abre cada «Ir» **pintado** en la Puesta en marcha o en su inicio, con y sin año escolar, entonces ninguno da 403, 404 ni 500 | **`ningunIrPintadoEnLaPuestaEnMarchaDa403ANingunRol` (nueva)**; antes solo se recorría la lista calculada (`nuncaUnBotonQueTermineEn403`) |
| Dada la bandeja de Aprobaciones, cuando hay aprobaciones que se dan en otra pantalla, entonces aparecen con su enlace (F-D5) y el resumen diario las cuenta | `BandejaPendientesExternosTest`, `ResumenDiarioTest` |
| Dado un tooltip, entonces tiene `aria-describedby`; dado un `th:if` con `th:replace` en el mismo elemento, entonces falla | `PlantillasIfReplaceTest`, `CambioConectorTest` (el ⚠ y «Ver detalle» de la cuenta bancaria), `GuiaPreparacionWebTest` (el `aria-describedby` del botón deshabilitado) |
| Capturas a 390 px | Revisadas en `docs/ux/` de la tanda 1b (no se volvieron a generar en este QA) |

### 2.4 Suspensión, objeción, baja, cierre, exportación y supresión

| Escenario | Prueba |
|---|---|
| Dada una suspensión, cuando la propone y la aprueba el mismo operador, entonces se rechaza; con dos, se aplica a los 30 días | `CambioEstadoColegioTest`, `AplicacionCambiosEstadoTest`, `f1SuspensionAntesDe30DiasFalla` (MySQL) |
| Dado el titular, cuando objeta la suspensión, entonces queda registrada y frena | `CambioEstadoColegioTest` |
| Dada la suspensión aplicada, entonces `sistema.exportacion` genera el ZIP AES-256 con manifiesto; ninguna fila de otro colegio (AP-12) | `ExportacionDosColegiosTest`, `EntregaExportacionTest` |
| Dado el enlace (solo por correo), cuando el titular escribe su DNI, entonces le llega el código (solo por WhatsApp, 10 min); ve la clave una vez y descarga hasta 3 veces | `EntregaExportacionTest`, `descargaCuartaFallaYConservarHastaManipuladoFalla` (MySQL) |
| Dados 5 intentos fallidos, entonces el enlace se anula y cada intento deja su evento | `EntregaIntentosTest` |
| **Dado un enlace anulado, cuando el operador manda uno nuevo, entonces el nuevo tiene 5 intentos (no 0), un error de tipeo deja 4 y el titular recibe su exportación** | **`QaSprint11EntregaTest` (4 nuevas)**, **`entregaNuevaTrasCincoIntentosEmpiezaEnCeroYSinEnlaceNuevoNoBaja` (MySQL, nueva)** |
| Dado el enlace nuevo, cuando se equivoca 5 veces, entonces también se anula; los intentos del enlace anterior siguen en la bitácora | **`QaSprint11EntregaTest` (nuevas)** |
| Dado cc_sistema sin enlace nuevo, cuando intenta bajar `intentos_fallidos`, entonces 1644 | **MySQL (nueva)** |
| Dado un operador, cuando reenvía la entrega con el id de la exportación de OTRO colegio, entonces se rechaza y el enlace del otro sigue igual (IDOR) | **`debeRechazarElReenvioDeLaExportacionDeOtroColegio` (nueva)** |
| Dado el cierre sin exportación, o con la de otro colegio o anterior a la suspensión, entonces se rechaza | `CierreTest`, `exportacionHuellaNoCambiaYEntregadaSinDescargaFalla` (MySQL) |
| Dado el cierre con autorización, entonces `conservar_hasta` = suspensión + 12 meses; sin ella, cierre + 30 días; una BAJA, + 30 días | `CierreTest`, `BajaConDatosTest`, `suprimirSqlDeUnColegioNoTocaAOtro` (MySQL), **`QaSprint11CicloCompletoTest`** |
| Dada el acta de supresión, cuando la aprueba quien la propuso, con retención legal, de otra conexión o vencida, entonces se rechaza; `suprimir.sql` de un colegio no toca a otro y la cadena sigue verificando | `RetencionLegalTest`, `VerificadorCadenaTachadaTest`, `elActaSeRechazaFueraDeSuCamino` y `suprimirSqlDeUnColegioNoTocaAOtro` (MySQL) |

### 2.5 Familias en modo consulta (INDECOPI)

| Escenario | Prueba |
|---|---|
| **Dada una familia REAL (su apoderado, su alumno y su comprobante) de un colegio CERRADO, cuando entra, entonces ve su estado de cuenta, la lista de comprobantes, el comprobante, sus libretas y sus comunicados (200); no escribe (403) y el personal ya no entra** | **`QaSprint11CicloCompletoTest` (nueva)**. Las pruebas que había (`ModoConsultaCerradoTest`, `CambioEstadoColegioTest`) usaban una familia inventada sin datos en la base |
| Dado el último día de `conservar_hasta` a las 23:30 de Lima (ya el día siguiente en UTC), entonces todavía consulta; una hora después, no | **`QaSprint11CicloCompletoTest` (nueva)**, `ModoConsultaCerradoTest` |

### 2.6 Zona horaria, multi-colegio y demo

| Escenario | Prueba |
|---|---|
| Dadas las ventanas del reloj real (48 h, 5 min, 12 h, etc.), un segundo antes y uno después | `RelojEnSuLimiteMySqlTest` |
| Dadas las pruebas nuevas, cuando la JVM corre en America/Los_Angeles, entonces pasan igual | Corrida con `-Duser.timezone=America/Los_Angeles` (sección 5) |
| Dadas las rutas con id (incluidas las del fin del colegio y la entrega pública), entonces otro colegio da 404 | `RutasIdorTest`, `CatalogoRutasConId` |
| Dada la demo `dev,persistente`, cuando arranca dos veces seguidas sobre la misma H2 en archivo, entonces el segundo arranque no vuelve a crear los datos | `PrimerArranqueBaseArchivoH2Test`, `SegundoArranqueBaseArchivoH2Test`, `DatosDemoDevTest` y el arranque real (sección 5) |

## 3. Error corregido

### QA-S11-01 (alto): el enlace nuevo tras 5 intentos fallidos nacía bloqueado

- **Dónde:** `ExportacionColegio.iniciarEntrega` no reiniciaba `intentos_fallidos`, y `trg_exportacion_colegio_cambio`
  (`scripts/mysql/03-triggers.sql`) rechazaba que el contador bajara. La desviación estaba anotada en la sección
  «Implementación» de la tanda 2 («cada intento fallido siguiente vuelve a anular el enlace»).
- **Efecto:** el «Nuevo enlace» del operador llegaba con «Intentos que quedan: 0». Con el DNI correcto funcionaba, pero un
  solo error de tipeo lo anulaba de nuevo, y así cada vez: el titular podía quedarse sin su exportación (y sin ella no hay
  cierre ni baja con datos). AP2-4 dice «a los 5 intentos se anula el token y hay que generar otra entrega»: cada entrega
  tiene sus 5 intentos.
- **Corrección:**
  - la aplicación pone `intentos_fallidos = 0` al iniciar una entrega (enlace nuevo);
  - el trigger deja bajar el contador **solo a 0 y solo junto con un `token_hash` nuevo, sin DNI confirmado ni código**; en
    cualquier otro caso sigue rechazando (1644). Cambia la huella de ese trigger, no el conteo (124 triggers, 146 huellas:
    las esperadas salen del mismo `03`).
- **Lo que no cambia:** cada intento fallido sigue en la bitácora (`EXPORTACION_INTENTO_FALLIDO`), cada enlace nuevo avisa a
  la plataforma y al correo de control, y el código sigue atado a la exportación. Para adivinar, un atacante necesita el
  correo del titular y un operador que mande enlaces nuevos (cada uno queda en la bitácora).
- **Pruebas:** `QaSprint11EntregaTest` (fallaban 3 de 5 contra `0798691`: «expected 5 but was 0» y «Este enlace no existe
  o ya no sirve» tras un solo error) y `AuditoriaSprint11FinColegioMySqlTest.entregaNuevaTrasCincoIntentosEmpiezaEnCeroYSinEnlaceNuevoNoBaja`.

## 4. Hallazgos pendientes

| Id | Severidad | Hallazgo | Propuesta |
|---|---|---|---|
| QA-S11-02 | Bajo | Con el DNI correcto, `confirmarDni` manda un código nuevo por WhatsApp cada vez, sin tope: quien tenga el enlace y el DNI puede gastar mensajes (costo) y reemplazar el código vigente | Un tope de códigos por hora por exportación (por ejemplo, 3) |
| QA-S11-03 | Bajo | `ModoConsultaCerradoTest` y `CambioEstadoColegioTest` prueban el modo consulta con una familia inventada (id 990/991, sin apoderado ni datos): un error en los servicios del portal en CERRADO no se habría visto | Cubierto ahora por `QaSprint11CicloCompletoTest`; conviene que las de la tanda usen familias reales |
| QA-S11-04 | Bajo | En CERRADO, la descarga de la libreta (GET) inserta `entrega_libreta`, y si la libreta regenerada no coincide se crean avisos. En CERRADO solo sale `ACTIVACION_CUENTA` (AP-5): si ese camino crea un mensaje, la base lo rechazaría y la familia vería un error en vez de «en revisión» | Probar en MySQL real la descarga de una libreta en CERRADO (con y sin coincidencia) |
| QA-S11-05 | Informativo | El reenvío de la entrega lo hace un solo operador. Con la corrección, cada enlace nuevo da 5 intentos más | Aceptable (queda en la bitácora y avisa al correo de control); si se quiere, contar los reenvíos en el resumen diario de la plataforma |
| QA-S11-06 | Informativo | La demo persistente: si el proceso se mata (no se detiene) mientras `DatosDemoDev` está creando los datos, el arranque siguiente choca con la llave primaria del colegio B y no arranca (H2 recuperó la fila pero no la secuencia) | Documentar en el README: detener con Ctrl+C y, si se cortó durante la primera carga, borrar `.demo-db/` |

## 5. Resultados

| Qué | Resultado |
|---|---|
| `QaSprint11EntregaTest` contra `0798691` (sin la corrección) | **Falla 3 de 5**: «expected: 5 but was: 0» (2) y «Este enlace no existe o ya no sirve» tras un solo error de tipeo (1). Las otras 2 (IDOR y bitácora) pasan |
| `QaSprint11CicloCompletoTest` y `GuiaPreparacionWebTest` contra `0798691` | Pasan (no encontraron errores: el modo consulta con familia real y los «Ir» pintados funcionan) |
| `./mvnw -B verify` (una vez, con la corrección, `8a615e1`) | **BUILD SUCCESS**: 3341 pruebas, 0 fallas, 0 errores, 306 omitidas (las de MySQL real, sin `CC_PRUEBA_MYSQL`); 19:23 min |
| Pruebas nuevas y `EntregaIntentosTest` con `-Duser.timezone=America/Los_Angeles` | 17 de 17 en verde |
| MySQL 8.4.11 real, contenedor propio (puerto 3317, borrado al terminar): 41 migraciones, 02, 03 (124 triggers) y `MigracionMySqlTest`, `PermisosMySqlTest` y `AuditoriaSprint11FinColegioMySqlTest` | En verde (2, 94 y 7 pruebas; la nueva incluida). Las huellas esperadas salen del `03` corregido |
| Demo `dev,persistente` desde un JAR, dos arranques seguidos sobre la misma `.demo-db/` (puerto 8131) | Arranque 1: crea los datos, `/login` 200, 0 errores. Arranque 2: «Ya hay usuarios», `/login` 200, 0 errores. Un primer intento que mató el proceso mientras cargaba los datos dejó la base inconsistente: QA-S11-06 |

No se tocaron el puerto 8098, `.claude/worktrees/demo` ni los contenedores existentes; solo se detuvieron los PID lanzados en este QA.
