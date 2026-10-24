# Sprint 10 · QA de notas, libretas y SIAGIE

> Rama `claude/sprint-10-qa`, creada desde `83928f0` (HEAD de `claude/sprint-10-notas-libretas-siagie`, PR #20 con el CI
> en verde). Agente `qa-tester`, 23 de octubre de 2026. Se leyeron `CLAUDE.md` («Método por sprint»), las skills
> `contexto-colegio` y `crear-modulo-spring`, los briefs `tanda-1.md`, `tanda-2.md` y `tanda-3.md`, la sección 3.13 del
> diseño (auditoría en papel) y sus tres secciones «Implementación».

## 1. Resumen

- **6 errores corregidos**, cada uno con su prueba que falla sin la corrección (QA-S10-1 alto; QA-S10-2 a QA-S10-4
  medios; QA-S10-5 y QA-S10-6 bajos). Las 8 pruebas que los demuestran se corrieron contra el código de `83928f0` y
  fallaron las 8 (sección 5).
- **11 hallazgos pendientes** (2 medios, 5 bajos y 4 informativos), sin corregir porque necesitan una decisión del usuario
  o tocan reglas que no son evidentes (sección 4).
- **Pruebas nuevas** en seis clases: `QaSprint10NotasTest`, `QaSprint10PeriodosTest`, `QaSprint10LibretasTest`,
  `QaSprint10LibretaPdfTest`, `QaSprint10DemoGuiaTest` y un caso nuevo en `AuditoriaSprint10NotasMySqlTest` (MySQL real).
- Resultados de `./mvnw -B verify` y de MySQL real: sección 5.

## 2. Escenarios (Dado / Cuando / Entonces)

Los marcados con **(nueva)** son pruebas de este QA; los demás ya estaban cubiertos y se revisaron.

### 2.1 Escalas por nivel y conclusión descriptiva

| Escenario | Prueba |
|---|---|
| Dados los 12 valores de la escala, cuando se consulta si exigen conclusión, entonces Inicial A, B y C; Primaria B y C; Secundaria C (RVM 048-2024) | `debeExigirLaConclusionSegunNivelYValor` (12 casos) **(nueva)** |
| Dada una B en Primaria, cuando la conclusión tiene 9 o 501 caracteres, entonces se rechaza; con 10 y 500, pasa | `debeRespetarLosLimitesDeLargoDeLaConclusion` **(nueva)** |
| Dada una B, cuando la conclusión son solo espacios, entonces «la norma exige la conclusión»; «   corta   » sigue siendo corta | `debeRespetarLosLimitesDeLargoDeLaConclusion` **(nueva)** |
| Dada una conclusión con tildes y «ñ», cuando se guarda, entonces su huella es el SHA-256 de su UTF-8 (el mismo de `SHA2` en MySQL) | `debeCalcularLaHuellaDeLaConclusionConTildesEnUtf8` **(nueva)** |
| E18 por nivel, `conclusion_siempre`, 75 nombres del catálogo, `solo_grados` de Inicial | `NotasTest`, `CatalogoCurriculoTest` |

### 2.2 Periodos, cierre, nota tardía, feriados y hora de Lima

| Escenario | Prueba |
|---|---|
| Dado un colegio con trimestres, cuando programa 3, entonces se llaman «I/II/III trimestre» y el cuarto se rechaza | `debeProgramarTresTrimestresConSuNombreYRechazarElCuarto` **(nueva)** |
| Dado un colegio con semestres, cuando programa 2, entonces el tercero se rechaza | `debeProgramarDosSemestresYRechazarElTercero` **(nueva)** |
| Dado el I trimestre abierto, cuando Dirección cambia a bimestres, entonces se rechaza (E11) | `debeRechazarCambiarDeTrimestreABimestreConUnPeriodoAbierto` **(nueva)** |
| Dado el fin el 8 de mayo y 7 días de cierre, cuando Dirección cierra el 30 de abril a las 23:30 de Lima (ya 1 de mayo en UTC), entonces se rechaza; el 1 de mayo a las 00:05 de Lima, cierra | `debeDecidirElCierreConLaFechaDeLimaYNoConLaDeUtc` **(nueva)** |
| Dado un periodo PROGRAMADO, cuando el docente guarda notas, entonces «todavía no abre» y no queda nada | `debeRechazarNotasDeUnPeriodoQueTodaviaNoAbre` **(nueva)** |
| Dada una nota tardía aprobada, cuando se registra a las 47 h 59 min, entonces entra; a las 49 h, vence | `debeAceptarLaNotaTardiaAntesDeCumplirseLasCuarentaYOchoHoras` **(nueva)**, `CorreccionesNotaTest` |
| Dada una libreta en revisión detectada el lunes 27 de julio, cuando la familia la pide, entonces «a más tardar el 31/07» (salta el 28 y el 29, Fiestas Patrias) | `debeContarElPlazoDeLaLibretaEnRevisionSinLosFeriados` **(nueva)** |
| Programar en orden, solapes, fuera del año, abrir antes con firma, apertura del sistema en hora de Lima, cierre desde fin − 7, no se reabre, el fin solo se extiende | `PeriodosTest`, `AuditoriaSprint10CurriculoMySqlTest` |

### 2.3 Correcciones después del cierre y alertas A5

| Escenario | Prueba |
|---|---|
| Dada una corrección que aprobó Promotoría (queda como autora de la vigente), cuando se pide otra sobre esa nota, entonces Promotoría no la aprueba y Dirección sí (E5 encadenado) | `debeRechazarQueQuienAproboLaCorreccionAnteriorApruebeLaSiguiente` **(nueva)** |
| Quien pide no aprueba (E4); el director que también enseña no aprueba la corrección de su nota (E5); lo aprobado es lo que entra (E6, M8) | `CorreccionesNotaTest` |
| Subir el nivel o dejar sin nota siempre alerta; bajar no; más de 5 por persona | `AlertasNotasTest` |
| Corrección de la tanda 2 da la versión 1 y la 2 con un solo aviso; corrección después de emitir da la siguiente con su aviso | `EmisionLibretasTest` |

### 2.4 Matrícula RESERVADA, retiros, traslados y exoneraciones

| Escenario | Prueba |
|---|---|
| Dado Mateo con A en Matemática de 5.° A, cuando se traslada a 5.° B a mitad del periodo, entonces la hoja de 5.° B muestra su A y su docente la cambia a AD (CAMBIO_ABIERTO en 5.° B que reemplaza la de 5.° A) | `debeConservarLaNotaDelTrasladadoYDejarQueSuNuevaSeccionLaCambie` **(nueva, QA-S10-1)** |
| Lo mismo en MySQL real: la base acepta el cambio en la sección nueva con los triggers y los permisos mínimos | `qaTrasladoAMitadDePeriodoContinuaLaCadenaEnLaSeccionNueva` **(nueva, MySQL)** |
| Dado un retirado con notas y un vigente sin nota, cuando Dirección mira el avance, entonces faltan las 4 del vigente | `debeContarComoPendienteAlVigenteSinNotaAunqueUnRetiradoTengaNota` **(nueva, QA-S10-2)** |
| Dado lo mismo con el periodo cerrado, cuando el docente pide la nota tardía, entonces se acepta («4 sin nota») | `debePermitirLaNotaTardiaAunqueUnRetiradoTengaNotas` **(nueva, QA-S10-2)** |
| Dado un alumno con notas de Religión y exonerado en el mismo periodo, cuando Dirección mira el avance, entonces sus notas no cuentan (A3) | `debeDescontarLasNotasDelExoneradoDelAvance` **(nueva, QA-S10-2)** |
| Dado un retirado antes del cierre, cuando se emiten las libretas, entonces no tiene libreta y la integridad no alerta | `noDebeEmitirLibretaAlRetiradoAntesDelCierreNiAlertarPorEl` **(nueva)** |
| La RESERVADA recibe nota y libreta (C1); exoneración solo Dirección, desde el periodo abierto, conserva las anteriores | `NotasTest`, `LibretaConDeudaTest`, `ExoneracionesTest`, MySQL `c1*`, `a3*` |

### 2.5 Libreta en PDF

| Escenario | Prueba |
|---|---|
| Dado «Ñahui Peña, José Ángel Üriel», cuando se genera, entonces el texto sale igual (sin «?») | `debeEscribirTildesYEnieDelNombreSinCambiarlas` **(nueva)** |
| Dado un alumno real con tildes y Ñ, cuando se emite e imprime, entonces la cabecera congelada y el PDF dicen «Ñahui Peña, José Ángel» | `debeImprimirElNombreConTildesYEnieEnLaLibretaReal` **(nueva)** |
| Dado un nombre de 190 caracteres, cuando se genera, entonces sale (recortado con «…», hallazgo P-3) en 6 páginas o menos | `debeGenerarLaLibretaConUnNombreMuyLargo` **(nueva)** |
| Dadas conclusiones de 1000 caracteres en todas las competencias de Primaria, cuando se genera, entonces salta de página, cada conclusión termina, no dice «resumida» y el código está en cada página | `debeSaltarDePaginaConConclusionesLargasSinPerderTexto` **(nueva)** |
| Dada Secundaria con sus 31 competencias y conclusiones de 500, cuando se genera, entonces salen todas en 6 páginas o menos | `debeImprimirLas31CompetenciasDeSecundariaCompletas` **(nueva)** |
| Dado el logo ausente, vacío o dañado, cuando se genera, entonces sale con el monograma; con un PNG válido, lo dibuja | `debeGenerarLaLibretaConLogoAusenteDanadoOValido` **(nueva)** |
| El código `LIB-{id}-{16 hex}` con la versión en el pie de cada página | `debeImprimirElCodigoDeDieciseisCaracteresEnCadaPagina` **(nueva)**, `GeneradorLibretaPdfTest` |
| Texto hostil, sin cobranza, código antes que DNI, 45 en menos de 10 s, cola llena | `GeneradorLibretaPdfTest`, `ColaDeLibretasTest` |

### 2.6 Verificación, reemisión y multi-colegio

| Escenario | Prueba |
|---|---|
| Dado el código escrito a mano («  lib-12 - ABCD… »), cuando Dirección lo verifica, entonces lo reconoce | `debeReconocerElCodigoEscritoAManoConEspaciosYMayusculas` **(nueva)** |
| Dada la Dirección de otro colegio, cuando verifica un código real del colegio 1, entonces «no existe» | `noDebeVerificarDesdeOtroColegioUnaLibretaReal` **(nueva)** |
| 16 caracteres, niveles, versión no vigente, solo Dirección; huella distinta: plazo, alerta y reemisión que aprueba Promotoría | `VerificacionLibretaTest`, `FotoLibretaTest` |
| Rutas con id de libretas, notas, correcciones, periodos y exportación desde otro colegio y otra familia: 404 | `RutasIdorTest` (con `CatalogoRutasConId`) |

### 2.7 La libreta nunca se retiene por deuda

| Escenario | Prueba |
|---|---|
| Tres familias (deuda vencida, al día y RESERVADA) de la misma sección: libreta, aviso y descarga 200 iguales | `LibretaConDeudaTest` (H2 y MySQL) |
| Interceptores, filtros, `@ControllerAdvice`, `@ModelAttribute`, layout de familias y redirección del ingreso sin filtro de deuda (B14) | `PortalSinFiltroDeDeudaTest` |
| Revisión manual: `LibretasFamilia`, `PortalLibretasController`, `familia/inicio.html` y `familia/libretas.html` no tienen ninguna condición sobre deuda ni dependen de cobranza (ArchUnit) | revisión |

### 2.8 Exportación para el SIAGIE

| Escenario | Prueba |
|---|---|
| Dado un alumno retirado DESPUÉS del cierre con una corrección aprobada, cuando Dirección exporta, entonces sale con los 2 de hoy (antes: bloqueada para siempre) | `debeExportarAlSiagieAunqueUnRetiradoTengaUnaCorreccionSinLibretaNueva` **(nueva, QA-S10-3)** |
| Dado Mateo trasladado a 5.° B después del cierre, cuando se exporta 5.° B, entonces sale con su libreta (emitida en 5.° A); 5.° A sale sin él; ninguna huella falsa | `debeExportarLaSeccionNuevaDeUnTrasladadoDespuesDelCierre` **(nueva, QA-S10-4)** |
| Dada una sección sin alumnos, cuando se exporta, entonces un mensaje (antes: error 500) | `debeResponderConUnMensajeAlExportarUnaSeccionSinAlumnos` **(nueva, QA-S10-5)** |
| Dada una exportación con un retirado en la sección, cuando se mira la pantalla, entonces no figura «desactualizada»; sí cuando cambia la libreta de un alumno de hoy | `noDebeMarcarDesactualizadaLaExportacionPorLaLibretaDeUnRetirado` **(nueva, QA-S10-6)** |
| Dada una persona de Caja, cuando pide la exportación, entonces 403 y nada se registra | `cajaNoDebeExportarCalificacionesNiConUnaDelegacion` **(nueva)** |
| Sale de la libreta vigente, se rechaza con una corrección sin libreta, hoja «Control»; delegación solo a Administración, vencida, 15 días | `CalificacionesSiagieTest`, `DelegacionSiagieTest`, MySQL `m12*` |

### 2.9 Avisos y demo

| Escenario | Prueba |
|---|---|
| Dados hermanos en 5.° A y 5.° B, cuando se emiten en la misma corrida, entonces UN aviso a su familia (y otro a la otra); una pasada repetida no duplica | `debeAvisarUnaSolaVezALaFamiliaConHermanosEnSeccionesDistintas` **(nueva)** |
| Dada una demo creada en octubre, cuando se siguen los pasos 9 y 10 de `/demo/guia`, entonces el docente abre su hoja, la familia descarga su PDF, Dirección verifica un código vigente y descarga el Excel «DEMO-PROVISIONAL» | `debeRecorrerLosPasosNueveYDiezDeLaGuiaConLasCuentasDeLaDemo` **(nueva)** |
| La demo trae el I bimestre cerrado, la versión 2 y el II abierto; en marzo, sin libretas | `DemoLibretasTest` |

## 3. Errores corregidos

| # | Severidad | Error | Corrección | Prueba que falla sin ella |
|---|---|---|---|---|
| QA-S10-1 | Alta | **Traslado a mitad de periodo.** La hoja buscaba las notas vigentes por sección: el alumno trasladado aparecía «sin nota» en su sección nueva y, al ponerle una, la aplicación intentaba otra nota inicial que choca con `uk_calificacion_inicial`: **el guardado de toda la hoja fallaba con un error 500** (el docente no podía guardar a nadie de la sección mientras llenara esa celda). Además el tutor y Dirección no veían su nota. | La cadena es de la matrícula: `CalificacionRepository.vigentesDeMatriculas` en `RegistroNotas` (hoja y guardado) y `NotasDeSeccion`; `Calificacion.cambio(vigente, seccionId, …)` deja el cambio en la sección del lote y de la matrícula (lo que exige `trg_calificacion_nace`). Sin cambios de base. | `debeConservarLaNotaDelTrasladadoYDejarQueSuNuevaSeccionLaCambie`; MySQL `qaTrasladoAMitadDePeriodoContinuaLaCadenaEnLaSeccionNueva` |
| QA-S10-2 | Media | **El avance contaba notas que no son de nadie de la sección hoy.** `CalculoAvance` y `CorreccionesNota.pedirTardia` contaban las vigentes por sección: las de un retirado, las que dejó un trasladado y las de un exonerado (registradas antes de su exoneración) inflaban «con nota», y el tope `min(conNota, esperadas)` lo escondía. Con un alumno vigente sin nota el avance decía 100 %: **ni el resumen del cierre ni la alerta `NOTAS_PENDIENTES` lo mostraban**, y la nota tardía se rechazaba con «todas tienen nota». | Solo las vigentes de las matrículas que reciben nota hoy y, por área, sin los exonerados. | `debeContarComoPendienteAlVigenteSinNotaAunqueUnRetiradoTengaNota`, `debePermitirLaNotaTardiaAunqueUnRetiradoTengaNotas`, `debeDescontarLasNotasDelExoneradoDelAvance` |
| QA-S10-3 | Media | **Exportación al SIAGIE bloqueada para siempre por un retirado.** Si un alumno se retira después del cierre y tiene una corrección aprobada, `EmisorLibretas` (a propósito) no le emite versión nueva, pero la exportación revisaba las correcciones de todas las libretas de la sección (también la suya): «Espere a que se emitan las libretas corregidas» sin salida. `EmisorLibretas.pendientes` dejaba además esa sección pendiente en cada pasada de 5 minutos. | La exportación revisa solo las libretas de los alumnos de hoy; `pendientes` filtra igual que `emitir`. | `debeExportarAlSiagieAunqueUnRetiradoTengaUnaCorreccionSinLibretaNueva` |
| QA-S10-4 | Media | **Exportación al SIAGIE bloqueada para siempre tras un traslado.** El alumno trasladado después del cierre tiene su libreta en la sección anterior; la exportación de la sección nueva las buscaba por sección: «1 alumno(s) todavía no tienen su libreta… espere unos minutos», sin salida. | `LibretaEmitidaRepository.vigentesDeMatriculas` (la vigente de cada matrícula, donde se haya emitido) y la regeneración agrupada por la sección de cada libreta (mezclarlas daría una huella distinta y una alerta de integridad falsa). | `debeExportarLaSeccionNuevaDeUnTrasladadoDespuesDelCierre` |
| QA-S10-5 | Baja | Exportar una sección sin alumnos respondía con un error 500 (`enOrden.get(0)` sobre una lista vacía). | Mensaje «no tiene alumnos con matrícula activa o reservada». | `debeResponderConUnMensajeAlExportarUnaSeccionSinAlumnos` |
| QA-S10-6 | Baja | La pantalla de la exportación marcaba «desactualizada» toda exportación de una sección con un retirado (comparaba con todas las libretas de la sección; la exportación usa solo las de los alumnos de hoy). | La misma lista en los dos lados. | `noDebeMarcarDesactualizadaLaExportacionPorLaLibretaDeUnRetirado` |

## 4. Hallazgos pendientes (necesitan una decisión)

| # | Severidad | Hallazgo | Propuesta |
|---|---|---|---|
| P-1 | Media | **Corrección después del cierre de un alumno trasladado.** La corrección se arma con la sección de la nota (la anterior). En MySQL `trg_calificacion_nace` exige que la matrícula esté en `NEW.seccion_id`: según la lectura del trigger la corrección falla con 1644 (no se probó en MySQL real). Y aunque pasara, `EmisorLibretas` no reemite la libreta del periodo cerrado (que quedó en la sección anterior) y la exportación de la sección nueva quedaría esperando. | Decidir de qué sección es la libreta de un periodo cerrado después de un traslado (propuesta: la corrección y la versión nueva van a la sección actual de la matrícula; cambia `trg_libreta_emitida_nace`, que exige `m.seccion_id = NEW.seccion_id`, y la corrección). Mientras tanto, pedir a Administración que no traslade con correcciones pendientes. |
| P-2 | Media | **La corrección de un periodo anterior no llega a la libreta del periodo siguiente** (desviación 5 de la tanda 3): la libreta del II bimestre muestra la columna del I con el valor viejo, y en el último periodo el «anual» puede quedar desactualizado. La exportación del II no se ve afectada (usa solo su periodo). | Emitir también la versión siguiente de las libretas de periodos posteriores ya emitidas de esa matrícula (motivo CORRECCION; el trigger lo permite si la corrección cae entre las marcas). |
| P-3 | Baja | Un nombre más ancho que la fila se recorta con «…» en la libreta (datos y cabecera de continuación), no se ajusta en dos líneas: un nombre oficial largo sale incompleto en el papel. | Ajustar el nombre con `envolver` en la fila «Apellidos y nombres». Revisión de `disenador-ui`. |
| P-4 | Baja | El tope real de páginas es 12 (`maxPaginas * 2`), no 6 como dice el brief: a partir de la 6 solo se escribe un aviso en el log. Con los casos reales probados (Primaria con 1000 caracteres por conclusión, Secundaria con 500) no se pasa de 6. | Dejar el tope en 6 con «Libreta resumida» o corregir el brief. |
| P-5 | Baja | Hermanos en secciones distintas: si la emisión de sus secciones cae en pasadas distintas de `AvisosLibretas` (la emisión corre cada 5 minutos por sección y los avisos en el segundo 45), la familia recibe dos avisos (la clave lleva la libreta más nueva). En la misma corrida, uno (probado). | Avisar solo cuando no queden secciones del colegio pendientes de emisión para ese periodo. |
| P-6 | Baja | Un alumno matriculado DESPUÉS del cierre recibe la versión 1 del periodo cerrado, todo «—», y la integridad la exige. | Decidir con el colegio si la libreta de un periodo anterior a la matrícula se emite. |
| P-7 | Baja | «Libretas» del colegio (pantalla e impresión de la sección) busca por sección: en la sección nueva el trasladado después del cierre figura en «faltan» para siempre y no sale en la impresión de la sección; su libreta se imprime desde la anterior. | Usar `vigentesDeMatriculas` también allí (con la regla de P-1). |
| P-8 | Informativo | Un traslado a otro grado (solo sin cuotas) con notas cambia las competencias: la exportación arma las columnas con la primera libreta; con libretas de grados distintos en la misma sección podría desalinearse. Caso muy raro. | Rechazar el cambio de grado de una matrícula con notas del año. |
| P-9 | Informativo | El largo de la conclusión en Java cuenta unidades UTF-16 (un emoji vale 2) y MySQL cuenta caracteres: Java es más estricto, sin riesgo. | Ninguna. |
| P-10 | Informativo | La reemisión toma lo que hay hoy en la base, también una nota alterada por un DBA: la controlan dos personas (Dirección pide, Promotoría aprueba) y la advertencia de la bandeja lo dice. Riesgo residual ya aceptado («la huella detecta, no impide»). | Ninguna; recordarlo en el manual de Promotoría. |
| P-11 | Informativo | La delegación del SIAGIE admite `hasta = desde + 15`: son 16 días calendario contando los dos extremos (igual en Java y en el trigger). | Confirmar si «15 días» incluye el día de inicio. |

## 5. Resultados

(Se completa al terminar las corridas.)
