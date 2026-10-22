# Sprint 9 · Correcciones de QA y de la auditoría del código

> 22 de octubre de 2026, rama `claude/sprint-9-asistencia-comunicados` (PR #18), agente `backend-spring`. Parte del
> informe de QA (`docs/arquitectura/sprint-9/qa.md`, traído con el merge de `claude/sprint-9-qa`: 27b6349 y 6fd1114) y de
> la auditoría del código sobre 5df49f4. **Sin migración nueva**: cambian `03-triggers.sql` (99 triggers, 13 funciones
> con huella, 112 huellas) y la aplicación. Resultados de la verificación: al final.

## Auditoría del código

| # | Severidad | Qué se hizo | Commit | Pruebas |
|---|---|---|---|---|
| 1 | Alta (decisión del usuario) | **Todo comunicado con PDF nace POR_APROBAR**, sea quien sea el emisor, y lo aprueba el otro órgano. `trg_comunicado_nace` lo exige con `adjunto_id` (ya no lee `requiere_revision`, que calcula la aplicación). `EnvioComunicados` y las pantallas lo dicen así; la demo hace que Promotoría apruebe el PDF de Dirección. `AnalizadorPdf` detecta las imágenes **en línea** y las de XObject con un `PDFGraphicsStreamEngine` (`drawImage`) y no cuenta el texto en modo 3 (invisible) para «tiene texto» (`ExtractorVisible`). | e7f9be2 | `CorreccionesSprint9ComunicadosTest.debeDejarPorAprobarTodoPdfAunqueNoPidaRevisionSeaQuienSeaElEmisor`; `AnalizadorHostilesTest.imagenEnLineaYTextoInvisibleRequierenRevision` y `toUnicodeFalsoNoSeLeeYQuedaParaLaPersonaQueAprueba`; MySQL `b3PdfConRevisionEnviadoDirectoFalla` (un PDF «sin revisión» de Dirección o Promotoría no sale directo: 1644) |
| 2 | Alta (INDECOPI) | La regla de cobranza (una sola expresión en `ContenidoSinCobranza` y en `trg_mensaje_nace` y `trg_comunicado_nace`) suma: `mensualidad`, «al d[ií]a con», «obligaciones/compromisos econ[oó]mic», `saldo(s)`, los abonos (`abono`, `abonar`, `abonen`…, con límite de palabra para no tocar apellidos como Abondano), `regulari[zc]` (34e8eb2), «cancel(ar\|en) (la\|su)» y «no podrá(n) rendir/dar (el) examen». | e7f9be2 | `CobranzaIndecopiTest` (28 casos, con la frase «los alumnos que no estén al día con la mensualidad no podrán rendir el examen bimestral» y los falsos positivos aceptados); `CorreccionesSprint9ComunicadosTest.debeRechazarLaFraseQueCondicionaElExamenALaMensualidad`; `ContenidoSinCobranzaTest` (Java = `03`); MySQL `c2FraseIndecopiYMontoEnLetrasFallan` |
| 3 | Media | `trg_configuracion_academica_cambio` exige `NEW.version = OLD.version + 1`. | e7f9be2 | MySQL `c3ConfiguracionQueSaltaVersionesFalla` (saltar versiones o no subirla: 1644; de a una con su firma, pasa) |
| 4 | Media | `trg_comunicado_destinatario_cambio` (nuevo): `avisado_mensaje_id` y `recordado_mensaje_id` pasan de NULL a un valor **una vez**, solo con `cc_sistema` y con un mensaje `COMUNICADO%` a un apoderado de ESA familia; `abierto_en`, una vez y a ±5 minutos del reloj de la base. Conteos: 99 triggers y 112 huellas en `VerificadorPermisosBaseDatos`, `HuellasObjetosBdTest`, el CI (`huellas_objetos()` y el log de prod), `paso-0.sh`, `en-la-vm.sh`, el acta del paso 0, `despliegue-gcp.md` y `mysql-usuarios.md`. | e7f9be2 | MySQL `c4DestinatarioSoloCambiaComoDebe`; `HuellasObjetosBdTest`, `VerificadorPermisosBaseDatosTest` |
| 5 | Baja | El proceso hijo arranca un hilo daemon que hace `Runtime.halt` a los 12 s (el padre lo mata por PID a los 10). | e7f9be2 | Sin prueba propia (el hilo no cambia ningún resultado dentro de los 10 s); las del analizador siguen en verde |
| 6 | Baja | El consentimiento desde la ficha exige el **código de una ficha de matrícula registrada de esa familia** (`FM-{año}-{id}`, que ahora muestra la ficha del alumno). Más de 30 por persona y por día: alerta `CONSENTIMIENTOS_DESDE_FICHA` que va solo a Dirección. | e7f9be2 | `ConsentimientosTest.desdeFichaExigeLaFichaRegistradaDeLaFamilia`; `AlertaConsentimientosFichaTest` |

## Pendientes del QA

| Hallazgo | Qué se hizo | Commit | Pruebas |
|---|---|---|---|
| QA-S9-4 | La situación de matrícula sigue la sección 3.11: solo cuenta como cursado el año anterior con matrícula ACTIVA (PROMOVIDO si el grado sube, REPITENTE si no); una RESERVADA no es haber estudiado; una RETIRADA, o una matrícula de años más antiguos, es REENTRANTE; nunca antes, INGRESANTE. La nómina sigue saliendo como PROVISIONAL. | e7f9be2 | `NominaSituacionYOrdenTest` (3 casos de situación) |
| QA-S9-5 (decisión) | «S/» seguido de un número en letras (uno… novecientos, mil, cien, dieci…, veinti…) cuenta como monto; «s/n» y «padres/madres» pasan. | e7f9be2 | `CobranzaIndecopiTest`; MySQL `c2FraseIndecopiYMontoEnLetrasFallan` |
| QA-S9-6 | La alerta de registro incompleto compara los ALUMNOS con matrícula activa contra los que tienen marca inicial, no las cantidades. | e7f9be2 | `AlertasAcademicasTest.debeAvisarCuandoCuadraElNumeroPeroFaltaElAlumnoNuevo` |
| QA-S9-7 | Una sola regla de «N días hábiles» (`DiasHabiles.dentroDeHabiles`: el día de inicio cuenta como el primero, con los feriados del colegio) para la justificación, el registro tardío y la cobertura. El registro tardío y la cobertura quedan un día más cortos (5 días de clases, no 6), como dice la pantalla. | e7f9be2 | `JustificacionTest`, `CoberturaTest`, `RegistroAsistenciaTest`, `QaSprint9AsistenciaTest` |
| QA-S9-8 | El primer envío usa el estado de cada alumno **en la marca de agua** (`InasistenciasDelDia.delDiaHasta`: la última marca de su cadena que no la supera). Una corrección que llega mientras se prepara sale en el envío siguiente como corrección de lo que la familia recibió. | e7f9be2 | `PrimerEnvioConCorreccionTest` |
| QA-S9-9 | Al aprobar el PDF de un docente, `ManejadorPublicarComunicado` exige que el docente siga con su asignación VIGENTE (no auxiliar) en la sección (`SeccionesDocente.sigueAsignado`); si no, no sale y la bandeja pide rechazarlo. `trg_comunicado_estado` lo exige también. | e7f9be2 | `CorreccionesSprint9ComunicadosTest.noDebeSalirElPdfDeUnDocenteQueYaNoTieneLaSeccion` |
| QA-S9-10 | La nómina se ordena por apellido paterno, materno y nombres con la intercalación del español del Perú (`Collator` es-PE: la Ñ después de la N, las tildes no van al final). El mensaje de fin de semana dice la fecha («El 24/10/2026 es fin de semana»). | e7f9be2 | `NominaSituacionYOrdenTest.debeOrdenarPorLosDosApellidosYLosNombresConLaEnheYLasTildes` |
| QA-S9-11 | `docs/arquitectura/sprint-9/tanda-1.md`: resumen breve que apunta a «Tanda 1 · Implementación». | e7f9be2 | — |

## Decisiones tomadas en las correcciones (para confirmar con el usuario)
- **Falsos positivos aceptados de la regla de INDECOPI**: «abono» para el biohuerto, «estén al día con las tareas» y
  «cancelar la reunión» no salen. El comunicado se rechaza con su motivo y queda en la bitácora; quien lo escribe lo
  redacta de otra forma. No hay un camino «a aprobación» para el texto: la base lo rechaza siempre (el filtro de texto no
  tiene excepciones).
- **El código de la ficha** es el de la matrícula registrada (`FM-{año}-{id}`), porque el sistema no guardaba un número
  de ficha en papel. Si el colegio numera sus fichas de otra forma, se agrega ese número a la matrícula en una migración.
- **Situación de matrícula** de quien saltó o bajó de grado: PROMOVIDO si sube y REPITENTE si no (provisional hasta tener
  la plantilla real del SIAGIE, D12).
- **ToUnicode falso**: el analizador no lo puede detectar (el texto extraído no es el que se ve); lo cubre que todo PDF lo
  apruebe otra persona leyéndolo.

## Resultados de la verificación
- `./mvnw -B verify` en local (una vez, sobre e7f9be2): **2845 pruebas, 0 fallos, 0 errores, 202 omitidas** (las de MySQL
  real); BUILD SUCCESS. Antes, las clases tocadas en H2 (416 pruebas) dieron 2 errores de las pruebas nuevas (la
  referencia del aviso no admite puntos y una prueba sin transacción), corregidos antes del commit.
- MySQL real en local (contenedor propio `cc-s9t3-mysql`, `mysql:8`, base limpia y en el orden del job `mysql`): `03` sin
  error con **99 triggers y 13 funciones**; `MigracionMySqlTest` 2, `PermisosMySqlTest` 94 (7 omitidas por la fecha),
  `AuditoriaSprint8MySqlTest` 18, `AuditoriaSprint9MySqlTest` 5, `AuditoriaSprint9AsistenciaMySqlTest` 13,
  `AuditoriaSprint6MySqlTest` 3, `AuditoriaSprint7MySqlTest` 12, `QaSprint7MySqlTest` 5, `AuditoriaAnulacionPagoMySqlTest` 1
  y `CorreccionesSprint7MySqlTest` 4, todas sin fallos. `AuditoriaSprint9ComunicadosMySqlTest`: 39 de 41 a la primera
  («regularicen» no calzaba con `regulariz` y la prueba de la configuración usaba los valores por defecto, que no escriben
  la fila); con 34e8eb2, **41 de 41**. El contenedor se borró al terminar.
- CI del PR #18, corrida 39869977993 sobre 34e8eb2: **todos los jobs en verde** (compilar y probar, también con la zona de
  Los Ángeles: 2846 pruebas, 0 fallos, 202 omitidas; MySQL 8 con `AuditoriaSprint9ComunicadosMySqlTest` 41 de 41,
  `huellas_objetos()` = 112 y prod «Huellas de los 99 triggers y las 13 funciones verificadas»; respaldo; demo, con el PDF
  de ejemplo aprobado por Promotoría y las diez bombas; dependencias; gitleaks).
- No se tocaron `cuentas-claras-mysql-1`, `cuentas-claras-app-1`, `smarthr-*`, `supabase_*`, `cc-mysql-s5*`,
  `.claude/worktrees/*` ni el puerto 8098.
