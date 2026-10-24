# Sprint 10 · Correcciones de QA y de la auditoría del código

> 24 de octubre de 2026, rama `claude/sprint-10-notas-libretas-siagie` (PR #20), agente `backend-spring`. Parte del informe
> de QA (`docs/arquitectura/sprint-10/qa.md`, traído con el merge de `claude/sprint-10-qa`: 7ee4541 y dc1e2db, 6
> correcciones con sus pruebas) y de la auditoría del código sobre 83928f0. **Migración V38** (`lote_calificacion.conexion_id`,
> `libreta_emitida.niveles` y la tabla `matricula_historial`); **sin triggers nuevos**: cambian `trg_lote_calificacion_nace`,
> `trg_calificacion_nace`, `trg_libreta_emitida_nace` y `trg_matricula_estado`, así que siguen **112 triggers, 16 funciones
> con huella y 128 huellas**. Resultados de la verificación: al final.

Commits: **62dc39d** (V38, `02`, `03`, verificador, CI y conteos de las pruebas), **3fdf813** (integridad de las notas y
alerta A5), **9635b76** (libretas, SIAGIE, avisos y PDF), **8f81e9a** (pruebas en H2 y MySQL real) y el de este documento.

## Auditoría del código

| # | Severidad | Qué se hizo | Commit | Pruebas |
|---|---|---|---|---|
| 1 | Alta | `trg_lote_calificacion_nace` guarda `CONNECTION_ID()` en `lote_calificacion.conexion_id`; `trg_calificacion_nace` exige que cada nota de un lote (REGISTRO, CAMBIO_ABIERTO, REGISTRO_TARDIO) venga de esa conexión y que el lote no pase de `filas`. `IntegridadNotas` recalcula la huella de cada lote (matrícula, competencia, nivel y huella de la conclusión, igual que `RegistroNotas`) contra el valor de su `NOTAS_GUARDADAS` o `CALIFICACION_TARDIA_REGISTRADA`; si no coincide, sobran o faltan notas, o no hay evento: alerta CRÍTICA `INTEGRIDAD_NOTAS` a Promotoría y `EmisorLibretas` NO emite las libretas de esa sección (periodos hasta el de la libreta). | 62dc39d, 3fdf813, 9635b76 | MySQL `h1NotaDesdeOtraConexionConElLoteDeLaDocenteFallaYNoPasaDeSusFilas` (otra conexión con el lote de la docente: 1644; la segunda nota de un lote de 1 fila: 1644) y `h1LoteAlteradoDetieneLaEmisionDeLaSeccion` (un DBA cambia una nota: la corrección pendiente de la sección no sale y Promotoría recibe la alerta; restaurada, sale); H2 `unaNotaAlteradaDetieneLasLibretasDeLaSeccionYAlertaAPromotoria` y `unaNotaDeMenosEnUnLoteTambienDetieneLasLibretas` |
| 2 | Alta | V38: `libreta_emitida.niveles` (VARCHAR(6000)) con los niveles canónicos (`FotoLibreta.niveles`: `periodo\|competencia\|nivel` y la anual). `trg_libreta_emitida_nace` exige los niveles en toda libreta nueva y, en la REEMISION, los mismos de la versión anterior. En la bandeja, `ManejadorReemisionLibreta.detalle` muestra la diferencia entre lo emitido y lo de hoy («1 PRI-MAT-1: emitido A, hoy C»); con diferencias, aprobar se rechaza («corrige esas notas por su solicitud») y el emisor tampoco reemite. | 62dc39d, 9635b76 | H2 `laLibretaGuardaSusNiveles` y `laReemisionConNivelesDistintosMuestraLaDiferenciaYNoSeAprueba`; `FotoLibretaTest.huellaDistintaDaPlazoYReemision` (con la nota alterada no se aprueba; devuelta a lo emitido, se reemite); MySQL `h2ReemisionConOtrosNivelesFalla` (1644, «niveles emitidos») |
| 3 | Media | `AlertasNotas`: si quien aprobó la corrección que sube el nivel o deja sin nota es de Promotoría (que no recibe su propia alerta), sale también `CORRECCION_NOTA_POR_PROMOTORIA`, que va solo a Dirección (`TipoAviso.soloDireccion`) y sale al celular. | 3fdf813 | H2 `laCorreccionQueApruebaPromotoriaTambienVaADireccion` |
| 4 | Media (y P-2) | Una corrección o nota tardía de un periodo ANTERIOR emite la versión siguiente de las libretas vigentes de los periodos posteriores del año (que muestran esa columna; en el último, también la anual): `EmisorLibretas` mira las correcciones de los periodos hasta el de la libreta y `trg_libreta_emitida_nace` las acepta. «Qué cambió» dice el periodo («Matemática · … (I bimestre): A → AD») y la anual. La familia recibe UN aviso (ver P-5). | 62dc39d, 9635b76 | H2 `laCorreccionDeUnPeriodoAnteriorEmiteTambienLaLibretaDelSiguienteConUnSoloAviso` (versión 2 del I y del II, un solo aviso «I bimestre y II bimestre») |
| 5 | Media (INDECOPI) | `PortalLibretasController`: el aviso de «en revisión» lo calcula el servidor y viaja en el atributo flash al redirigir a `/familia/libretas`; el parámetro `?revision=` ya no existe ni se lee. | 9635b76 | H2 `unEnlaceManipuladoNoMuestraLaLibretaEnRevision`; `FotoLibretaTest` y `QaSprint10LibretasTest` (el aviso llega por el flash) |
| 6 | Baja | `trg_lote_calificacion_nace`: la `CALIFICACION_TARDIA` la resolvió alguien con rol activo de Dirección o Promotoría. | 62dc39d | MySQL `h6NotaTardiaAprobadaPorAdministracionFalla` (la base acepta la aprobación cruda por Administración con su firma, y el lote de la docente con esa solicitud da 1644) |
| 7 | Baja | `/academico/libretas/verificar`: si la huella no coincide, no se muestran los niveles (la vista y la plantilla). | 9635b76 | H2 `laVerificacionNoMuestraNivelesSiLaHuellaNoCoincide` |
| 8 | Baja | La libreta es de la matrícula que estaba ACTIVA o RESERVADA en el `cerrado_en` del periodo, aunque se retire antes de la pasada: `trg_matricula_estado` escribe `matricula_historial` (sección y estado anteriores, hora de Lima de la base) y `MatriculasAlCierre` y `trg_libreta_emitida_nace` usan la primera fila posterior al cierre (sin filas, lo de hoy). Nadie más escribe la historia (sin GRANT: 1142; en `tablas-vigiladas.txt`, en el verificador de prod y en `comprobar` del CI). | 62dc39d, 9635b76 | H2 `quienSeRetiraDespuesDelCierreRecibeSuLibreta`; MySQL `h8YP1TrasladoYRetiroDespuesDelCierre` y `historiaDeLaMatriculaSoloLaEscribeLaBase` |
| 9 | Baja | Conteos: `despliegue-gcp.md` (`huellas_objetos()` = 128), el acta del paso 0 (V1 a V38, 112 triggers y 13 funciones, 128 huellas), `mysql-usuarios.md` (párrafo de V38). V38 no agrega triggers: 112/16/128 en el verificador, el CI, `paso-0.sh` y `en-la-vm.sh` siguen igual. Migraciones: `MigradorBaseDatosTest` (38), `MigracionMySqlTest` y `VerificadorRespaldoTest` («38»), CI «V1–V38». | 62dc39d y este documento | `MigradorBaseDatosTest`, `VerificadorRespaldoTest`, `MigracionMySqlTest` |

## Pendientes del QA

| Hallazgo | Decisión y qué se hizo | Commit | Pruebas |
|---|---|---|---|
| P-1 | La libreta de un periodo es de la sección donde estaba la matrícula al CIERRE (historia de la matrícula). La versión siguiente queda en la sección de la anterior; la corrección de un trasladado va en la sección de la nota que reemplaza (`trg_calificacion_nace` lo admite solo para CORRECCION_APROBADA). | 62dc39d, 9635b76 | MySQL `h8YP1TrasladoYRetiroDespuesDelCierre` (corrección en la sección anterior y versión 2 allí); H2 `laLibretaDelTrasladadoEsDeLaSeccionDelCierre` |
| P-2 | Igual que el hallazgo 4. | 9635b76 | ídem |
| P-3 | El nombre y el tutor pasan a las líneas que hagan falta (y la cabecera de continuación), sin «…». | 9635b76 | `QaSprint10LibretaPdfTest.debeGenerarLaLibretaConUnNombreMuyLargo` (el nombre de 190 caracteres sale completo) |
| P-4 | Brief y diseño corregidos: el tope real es de 12 páginas (desde `max-paginas` = 6 se avisa en el log). | este documento | — |
| P-5 | UN aviso por familia por OLEADA de emisión: las libretas de la familia emitidas con menos de `ventana-avisos-minutos` (30) entre una y otra (hermanos de secciones emitidas en pasadas distintas, o las versiones de varios periodos por una misma corrección) van en un aviso, que sale cuando la oleada lleva la ventana sin libretas nuevas. En las pruebas la ventana es 0. | 9635b76 | `AvisosLibretasVentanaTest` (versión 1 a las 08:00 y corregida a las 08:20: nada a las 08:10 ni a las 08:40 para Rosa, un aviso a las 08:55; la otra familia, a las 08:40) |
| P-6 | Una matrícula creada después del cierre no tiene libreta de ese periodo: ni el emisor, ni la integridad (no falta), ni el trigger (1644), ni la exportación para el SIAGIE (no la espera). | 62dc39d, 9635b76 | H2 `unaMatriculaPosteriorAlCierreNoTieneLibretaDeEsePeriodo` (sin libreta, sin alerta y la exportación sale con 3); `IntegridadLibretasTest` actualizada |
| P-7 | «Libretas» cuenta como faltantes solo las matrículas de la sección al cierre: el trasladado después del cierre no falta en la nueva (su libreta está en la anterior, donde se imprime). | 9635b76 | H2 `laLibretaDelTrasladadoEsDeLaSeccionDelCierre` |
| P-8 | Sin cambio (informativo, muy raro): un traslado a otro grado con notas lo sigue permitiendo `ServicioMatriculas` (sin cuotas). Rechazarlo exige que `alumnos` lea las notas de `academico` (dependencia que hoy no existe). Queda como riesgo residual. | — | — |
| P-9 | Sin cambio: Java es más estricto que MySQL con los emojis (unidades UTF-16); sin riesgo. | — | — |
| P-10 | Resuelto con el hallazgo 2: la reemisión ya no toma «lo que hay hoy». | 9635b76 | ídem 2 |
| P-11 | Sin cambio: `hasta = desde + 15` (16 días calendario contando los dos extremos), igual en Java y en el trigger. Queda para confirmar con el colegio. | — | — |

## Decisiones tomadas en las correcciones
- **La historia de la matrícula la escribe la base** (no la aplicación): así nadie con la clave de cc_app mueve una libreta de
  sección fabricando un traslado. Con H2 no hay triggers: sin filas, vale lo de hoy (las pruebas de H2 insertan la historia).
- **Notas alteradas detienen toda la sección** (no solo la libreta del alumno): la huella es por lote, y un lote es de una
  sección y un área. Se reanuda cuando la base vuelve a lo guardado (lo revisa el responsable técnico con el respaldo).
- **`niveles` es VARCHAR(6000)** y no TEXT: la validación de Hibernate y H2 lo leen igual; cabe Secundaria (31 competencias,
  4 periodos y la anual) con holgura.
- La exportación para el SIAGIE no se frena por correcciones de periodos anteriores (exporta solo el periodo pedido).

## Resultados de la verificación
- `./mvnw -B verify` en local (una vez, sobre 8f81e9a): **3162 pruebas, 0 fallos, 0 errores, 285 omitidas, BUILD
  SUCCESS**. Antes, por clase: `CorreccionesSprint10LibretasTest` 11 de 11 y `AvisosLibretasVentanaTest` 1 de 1; las
  pruebas que cambiaron por decisión (`FotoLibretaTest`, `IntegridadLibretasTest`, las del QA del flash y del nombre
  largo, el mock del verificador y los conteos de migraciones) en verde.
- MySQL real en local (contenedor propio `cc-s10c-mysql`, `mysql:8`, puerto 3320): `01`, V1–V38 con `cc_migrador`, `02` y
  `03` sin error, 112 triggers y `huellas_objetos()` = 128; `AuditoriaSprint10LibretasMySqlTest` **27 de 27** (las 6 nuevas
  de las correcciones; la carga: 304 libretas en 6,0 s), `AuditoriaSprint10NotasMySqlTest` 25 de 25,
  `AuditoriaSprint10CurriculoMySqlTest` 29 de 29, `MigracionMySqlTest` 2 de 2 y `PermisosMySqlTest` 94 (8 omitidas, 0
  fallos; corrida sola: juntas con las demás clases MySQL agotan las conexiones del contenedor). El contenedor se borró al
  terminar.
- CI del PR #20, corrida 40098009643 sobre 8f81e9a: **todos los jobs en verde** (compilar y probar: 3162 pruebas, 0
  fallos, 285 omitidas, también con la zona de Los Ángeles; MySQL 8 con `AuditoriaSprint10LibretasMySqlTest` 27 de 27 y
  la carga de 304 libretas en 3,0 s, `AuditoriaSprint10NotasMySqlTest` 25 de 25 y prod «Huellas de los 112 triggers y las
  16 funciones verificadas»; respaldo; demo; dependencias; gitleaks).
- No se tocaron `cuentas-claras-mysql-1`, `cuentas-claras-app-1`, `smarthr-*`, `supabase_*`, `cc-mysql-s5*`,
  `.claude/worktrees/*` ni el puerto 8098.
