# Sprint 10 · Tanda 1 · Currículo, periodos y escalas (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-10-notas-libretas-siagie.md` (secciones **2.3**, **3.13** (auditoría en papel), 3.1 a 3.4,
> 3.11, 3.12, 4, 5 «V35», 6.2 y 6.3 «Tanda 1», 6.4, 6.5, 7, 8 y 9). Rama `claude/sprint-10-notas-libretas-siagie`, PR en
> borrador: **la tanda está terminada cuando el CI del PR está en verde.** Antes de codear, la auditoría del diseño en papel
> (`auditor-seguridad-antifraude`); sus hallazgos críticos y altos se corrigen en el diseño y en este brief.

## 1. Decisiones (pendientes del usuario: se implementan los valores por defecto)
1. **La D12 sigue sin confirmar.** Todo lo de la sección 2.3 del diseño (S1 a S11) va con su valor por defecto,
   configurable por colegio o versionado en el catálogo. Nada de la norma va escrito a mano en Java: sale del catálogo.
2. **Catálogo global** (decisión 184): `curriculo_version`, `area_curricular`, `competencia` y `escala_logro` sin
   `colegio_id`, solo los inserta la migración (`cc_migrador`); `cc_negocio` no tiene GRANT de escritura.
3. **Periodo por defecto BIMESTRE** (decisión 171); TRIMESTRE o SEMESTRE por colegio y año. Versión y tipo de periodo
   **fijos desde que abre el primer periodo**. **CERRADO no se reabre** (decisión 172).
4. **Las áreas se aprueban con la asignación** (sección 3.4): `asignacion_area` solo se inserta en una asignación PROPUESTA y
   por quien la propuso, **en la misma transacción de la propuesta** (A2). Transversales: el TUTOR (decisión 175).
5. **Auditoría en papel sobre 0b30c57 incorporada** (diseño, sección 3.13, que prevalece). En esta tanda: **A2** (áreas en la
   transacción de la propuesta y comparadas al aprobar), **M7** (`fin` solo se extiende; días de cierre fijos), **M10**
   (catálogo verificado en las dos direcciones y a diario), **B13** (sin barra vertical ni saltos de línea en el catálogo) y
   **B15** (casos nuevos del verificador). C1, A3 a A6, M8, M9, M11, M12 y B14 son de las tandas 2 y 3.

## 2. Alcance
- V35 (diseño, sección 5): catálogo `CNEB-2016.1` completo (**29 áreas, 75 competencias, 12 valores de escala**, listas de la
  sección 3.2), `configuracion_evaluacion`, `periodo_evaluacion`, `asignacion_area` y los CHECK de `acceso_dato_personal`
  con `NOTAS_SECCION`, `LIBRETA` y `CALIFICACIONES_SIAGIE` (H7: una sola vez).
- `VerificadorCatalogoCurriculo` con `src/main/resources/curriculo/CNEB-2016.1.sha256` (en prod, si no coincide, no arranca),
  **en las dos direcciones** (cada versión de la base con su `.sha256` y cada `.sha256` con su versión), y `RevisionCatalogo`
  diaria a las 05:00 con la alerta `INTEGRIDAD_CATALOGO` a Promotoría (M10).
- Pantallas (skill `sistema-diseno`): «Evaluación del año» (Dirección: versión, tipo de periodo, conclusión máxima, «exigir
  siempre la conclusión», días de cierre anticipado, lengua materna, DRE y UGEL, con firma); «Periodos» (programar, mover
  fechas, abrir antes de tiempo y cerrar, cada acción con firma); catálogo de solo lectura (`/academico/curriculo`); la
  propuesta de asignación con áreas del catálogo y «Todas las áreas del grado» para el tutor de Inicial y Primaria;
  «Asignaciones sin áreas del catálogo» (las vigentes del sprint 9).
- `AperturaPeriodos` (`sistema.academico`, cc_sistema, 06:00): abre los periodos cuyo inicio llegó.
- `Asignaciones.proponer`: inserta la asignación, sus áreas y **después** la solicitud, cuyo `datos` lleva `"areas"` con los
  ids ordenados y separados por coma (A2). El manejador de `ASIGNACION_DOCENTE`: al aprobar una con las mismas claves
  (sección, docente, tipo) que una VIGENTE, termina la anterior con la firma de quien aprueba y deja VIGENTE la nueva.
- Bitácora: `CONFIGURACION_EVALUACION_GUARDADA`, `PERIODO_PROGRAMADO`, `PERIODO_FECHAS_CAMBIADAS`, `PERIODO_ABIERTO`,
  `PERIODO_CERRADO` (sin el resumen de pendientes: tanda 2).
- `respaldar.sh` y `tablas-vigiladas.txt` con `asignacion_area` (solo inserción, por colegio). El catálogo no se cuenta por
  colegio.
- **Fuera de alcance:** notas, lotes, exoneraciones, correcciones, el resumen de pendientes al cerrar (tanda 2); libretas,
  PDF, SIAGIE, consentimiento `LIBRETAS` y mensajes (tanda 3); competencias propias del colegio (decisión 182).

## 3. Migración `V35__curriculo_periodos_y_escalas.sql`
- SQL en la sección 5 del diseño. La semilla va como un `INSERT … SELECT` por fila con el id de la versión por su código
  (H2 y MySQL). **Nada de `||` para concatenar** (en MySQL es OR): cada texto en un solo literal.
- Textos: los nombres de las competencias **exactamente** como la tabla de la sección 3.2 (con tildes; «Interactúa a través
  de sus habilidades sociomotrices», «Se desenvuelve en entornos virtuales generados por las TIC»). Descripciones de la escala:
  las de la RVM 094-2020, 5.1.1.3.6 (CNEB, p. 181). `exige_conclusion`: INICIAL en A, B y C; PRIMARIA en B y C; SECUNDARIA
  en C.
- `solo_grados = 'INICIAL_5'` en INI-C2L-1, INI-TRV-1 e INI-TRV-2. Exonerables: EF y REL de Primaria y Secundaria.
  Transversal: TRV de los tres niveles.
- Copiar las listas de `ck_acceso_dato_tipo` y `ck_acceso_dato_objeto` de V32 (la última que las cambió) y agregar los 3
  tipos; `LIBRETA` exige `alumno_id`.
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4:** `REGEXP_LIKE(…, 'c')` en los CHECK nuevos; la FK compuesta
  `competencia (area_id, version_id)`; `NOT (transversal AND exonerable)` con BOOLEAN; `DROP CONSTRAINT` y `ADD CONSTRAINT`
  sobre `acceso_dato_personal` con filas existentes; los 117 `INSERT … SELECT` (1 + 29 + 75 + 12) en las dos bases.
- **B13:** un CHECK por cada texto del catálogo (`area_curricular.nombre`, `competencia.nombre`, `escala_logro.nombre` y
  `descripcion`): `INSTR(x, CHAR(124)) = 0 AND INSTR(x, CHAR(10)) = 0 AND INSTR(x, CHAR(13)) = 0` (CHAR(124) es la barra
  vertical, el separador de la huella).
- Lección de V32 a V34: un CHECK que evalúa NULL pasa.
 Un caso NULL por CHECK: `abierto_por`, `cerrado_por`, `solo_grados`,
  `reemplaza_id`.
- Entidades del catálogo con `@Immutable` y repositorios `Repository` (no `CrudRepository`); `AsignacionArea` con
  `updatable = false`; `PeriodoEvaluacion.numero` y `anioEscolar` con `updatable = false`.

## 4. MySQL: permisos, triggers y huellas
- `01-usuarios.sql`: sin cambios. Sin `04-una-vez`.
- `02-permisos-tablas.sql`, bloque «Sprint 10 · tanda 1» (sección 6.2): **ningún GRANT sobre las 4 tablas del catálogo**;
  `INSERT, UPDATE (columnas)` en `configuracion_evaluacion` (sin `colegio_id`, `anio_escolar_id` ni `creado_por`) y en
  `periodo_evaluacion` (sin `numero`, `anio_escolar_id`, `colegio_id` ni `creado_por`); `INSERT` en `asignacion_area`.
- `03-triggers.sql` (sección 6.3 «Tanda 1», SQL completo en el diseño):
  - función nueva **`cc_rol_activo(colegio, nombre, rol)`**;
  - triggers nuevos: `trg_configuracion_evaluacion_nace` y `_cambio` (DIRECTOR con firma
    `configuracion_evaluacion:{anio}:GUARDADA:{version}`, versión de a una; versión del currículo, tipo de periodo y **días de cierre
    anticipado** fijos con un periodo no PROGRAMADO, M7), `trg_periodo_evaluacion_nace` (nace PROGRAMADO, DIRECTOR con firma
    `periodo_evaluacion:{anio}:{numero}:PROGRAMADO`, dentro del año y de la configuración, en orden) y `_cambio` (fechas con
    firma `periodo_evaluacion:{id}:FECHAS:{version}`; **en ABIERTO el fin solo se extiende**, M7; ABIERTO por `sistema.academico` en su fecha o DIRECTOR con
    `…:ABIERTO`; CERRADO por DIRECTOR con `…:CERRADO` desde `fin - dias_cierre_anticipado` en hora de Lima de la base y con
    el anterior CERRADO; `abierto_en` y `cerrado_en` los pone la base; CERRADO no cambia), `trg_asignacion_area_nace`
    (asignación PROPUESTA **y todavía sin su solicitud enlazada** (A2), por quien la propuso, TUTOR o DOCENTE sin `hasta`, área
    del nivel y de la versión, no transversal);
  - **versión nueva de `trg_asignacion_docente_estado`** (A2): al pasar de PROPUESTA a VIGENTE, `$.areas` de la solicitud es
    igual a `GROUP_CONCAT(area_id ORDER BY area_id)` de `asignacion_area` (SQL en la sección 3.13 del diseño).
  - **Comparar siempre con `<=>`** sobre variables de `SELECT … INTO` (si no hay fila quedan NULL y un `<>` no dispara).
  - Encabezado: «Versión anterior (correcciones del sprint 9): 99 triggers y 11 funciones (14 con huella; 113 huellas)».
- **Conteo esperado:** 99 → **104 triggers** (5 nuevos y 1 con versión nueva); funciones con huella 14 → **15** (12 de `03` +
  3 de `02`); huellas 113 → **119**. Prod: «Huellas de los 104 triggers y las 15 funciones verificadas». Con la auditoría,
  tanda 2: **108 y 124**; tanda 3: **112 y 128**.
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 5; 1142 (`INSERT INTO competencia`, `UPDATE competencia`,
  `UPDATE escala_logro`, `DELETE FROM periodo_evaluacion`, `UPDATE asignacion_area`, y por B15 `DELETE FROM asignacion_area`,
  `DELETE FROM configuracion_evaluacion`, `INSERT` y `UPDATE` en `curriculo_version` y `area_curricular`); 1143 (`UPDATE periodo_evaluacion SET
  numero = numero`, `UPDATE configuracion_evaluacion SET anio_escolar_id = anio_escolar_id`); 1644 (un `periodo_evaluacion`
  del colegio 0 que nace CERRADO). `HuellasObjetosBd` = 119.
- **Pruebas MySQL obligatorias (antes «comprobar»):** que un trigger BEFORE UPDATE de `periodo_evaluacion` puede leer su
  propia tabla (solapes con el anterior y el siguiente); `NEW.fin - INTERVAL v_dias DAY`; que `cc_hoy_lima()` decide el cierre
  aunque la zona de la aplicación sea Los Ángeles; el reemplazo de una asignación VIGENTE al aprobar otra con las mismas
  claves (UNIQUE `uk_asignacion_docente_vigente` y `_tutor` en la misma transacción).

## 5. Segregación de funciones
- **Configurar la evaluación y los periodos:** solo DIRECTOR, con firma. Administración, Promotoría y el docente, no.
- **Cerrar un periodo:** solo DIRECTOR, con firma; ni el sistema ni Promotoría. Abrir: el sistema en su fecha o DIRECTOR.
- **Dar áreas (el poder de poner notas):** propone Administración o Dirección; aprueba otra persona de Dirección o Promotoría,
  nunca el docente asignado (sprint 9). Las áreas no se agregan después de proponer ni por otra persona.
- Una cobertura (`hasta`) y el AUXILIAR no llevan áreas: no califican.
- Con menos de 2 personas activas de PROMOTOR o DIRECTOR, el aviso A8 del sprint 9 sigue vigente.

## 6. Contrato del catálogo para las tandas 2 y 3 (lo que no debe cambiar después)
- Códigos de competencia `{INI|PRI|SEC}-{AREA}-{n}` y de área por nivel; la tanda 2 los usa en los DTO y la tanda 3 en el perfil
  del SIAGIE. **No se renombran**: una corrección es una versión nueva (`CNEB-2016.2`, con `reemplaza_id`).
- `Catalogo.competenciasDe(versionId, grado)` respeta `solo_grados` y el orden (`area.orden`, `competencia.orden`);
  `Catalogo.exigeConclusion(versionId, nivel, valor)` lee `escala_logro`; ninguna clase de Java repite la tabla de la escala.
- La huella del catálogo: SHA-256 de las filas de la versión, en este orden y con `CHAR(10)` entre filas y `|` entre
  columnas: áreas por `(nivel, orden)` con `nivel|codigo|nombre|orden|transversal|exonerable`; competencias por
  `(codigo)` con `codigo|area|nombre|orden|solo_grados`; escala por `(nivel, orden)` con
  `nivel|valor|nombre|descripcion|orden|exige_conclusion`. Booleanos como `0` o `1`; NULL como cadena vacía.

## 7. Archivos que toca
- **Base:** `V35__curriculo_periodos_y_escalas.sql`; `scripts/mysql/02-permisos-tablas.sql`; `scripts/mysql/03-triggers.sql`;
  `scripts/respaldo/tablas-vigiladas.txt` y `respaldar.sh` (`asignacion_area`).
- **`academico.curriculo` (nuevo):** `model` (`CurriculoVersion`, `AreaCurricular`, `Competencia`, `EscalaLogro`,
  `ConfiguracionEvaluacion`, `PeriodoEvaluacion`, `TipoPeriodo`, `EstadoPeriodo`, `NivelEducativo`); `repository` (catálogo
  de solo lectura, sin `delete*`); `service` (`Catalogo`, `ConfiguracionesEvaluacion`, `Periodos`); `proceso.AperturaPeriodos`;
  `config.VerificadorCatalogoCurriculo`; `web` (`CurriculoController`, `ConfiguracionEvaluacionController`,
  `PeriodosController`); `src/main/resources/curriculo/CNEB-2016.1.sha256`.
- **`academico` (existe):** `model.AsignacionArea` y su repositorio; `Asignaciones.proponer(…, areas)`;
  `ManejadorAsignacionDocente` (reemplazo de la vigente); `api.SeccionesDocente.areasDe`; la pantalla de asignaciones.
- **Transversales:** `ActorSistema` (`sistema.academico` abre periodos: comprobar que ya existe), `AccionAuditoria` (+5),
  `VerificadorPermisosBaseDatos`, `HuellasObjetosBd`, `ModuloApp` (evaluación y periodos: DIRECTOR; catálogo: personal),
  `privacidad` (tipos nuevos de `acceso_dato_personal` en el enum), `application.yml` (`cuentasclaras.curriculo.*`).
- **Plantillas Thymeleaf** (skill `sistema-diseno`), `docs/manuales/direccion.md` (evaluación y periodos), sección
  «Tanda 1 · Implementación» del diseño, `.github/workflows/ci.yml` (paso «Sprint 10, tanda 1» del job `mysql`, V1–V35, 119 y
  104/15), `paso-0.sh`, `en-la-vm.sh`, `despliegue-gcp.md` y `mysql-usuarios.md` con los conteos nuevos.

## 8. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `CatalogoCurriculoTest`: 29 áreas (7, 10 y 12 por nivel), 75 competencias (14, 30 y 31), 12 valores de escala;
  `exige_conclusion` por nivel como la RVM 048-2024 (Inicial A, B y C; Primaria B y C; Secundaria C);
  `competenciasDe(INICIAL_4)` sin transversales ni C2L y `competenciasDe(INICIAL_5)` con ellas; exonerables solo EF y REL.
- `VerificadorCatalogoCurriculoTest`: la huella coincide; una competencia cambiada en la prueba hace fallar el arranque en el
  perfil prod y solo avisa en dev (E13); `versionSinArchivoFalla` y `archivoSinVersionFalla` (M10); `RevisionCatalogoTest`
  (la revisión diaria alerta a Promotoría).
- `ConfiguracionEvaluacionTest`: solo Dirección con firma (403 para Administración, Promotoría y docente); BIMESTRE por
  defecto; no cambia el tipo con un periodo ABIERTO (E11); `ConfiguracionSinDeudaTest` (la entidad no tiene campos de deuda,
  pago ni bloqueo, por reflexión).
- `PeriodosTest`: programar 4 bimestres en orden; solape y fechas fuera del año rechazados (E41); abrir antes de tiempo con
  firma; `AperturaPeriodos` abre el de hoy en hora de Lima; cerrar antes de `fin - 7` rechazado (E9); cerrado no se reabre
  (E10); el II no cierra con el I abierto.
- `AsignacionAreaTest`: las áreas viajan en la propuesta y se ven en la bandeja; agregar áreas a una VIGENTE rechazado (E24);
  el AUXILIAR y una cobertura sin áreas (E25); aprobar una nueva con las mismas claves termina la anterior;
  «Asignaciones sin áreas del catálogo» lista las del sprint 9.
- `ReglasArquitecturaTest` (el catálogo sin `CrudRepository`; `academico.curriculo` sin cobranza), `RutasIdorTest` y
  `CatalogoRutasConId` con las rutas nuevas y `?anio=`; `MatrizPermisosTest`; `InmutabilidadAcademicoTest` (entidades del
  catálogo y `AsignacionArea`); pruebas que cuentan migraciones, triggers o huellas: **35 migraciones, 104 triggers, 119
  huellas**.

**MySQL real (`mysql/AuditoriaSprint10CurriculoMySqlTest`, job `mysql`; correr local lo tocado antes del push). Todas 1644
salvo que se diga otra cosa:**
- `e12CatalogoNoSeEscribe` (INSERT y UPDATE en `competencia` y `escala_logro`: 1142).
- `e11ConfiguracionFijaConPeriodoAbierto`; `configuracionSinDireccionNiFirmaFalla` (INSERT y UPDATE);
  `configuracionQueSaltaVersionesFalla`; `m7DiasDeCierreFijos`; `m7FinAdelantadoFalla` (en ABIERTO).
- `e41PeriodoQueNaceCerradoFalla`, `e41FechasFueraDelAnioFalla`, `periodosSolapadosFallan`, `periodoDeMasFalla` (número 5
  con BIMESTRE).
- `e9CierreAntesDeLaFechaFalla` (con la zona de Los Ángeles en la aplicación), `cierreSinFirmaFalla`,
  `cierreConElAnteriorAbiertoFalla`, `e10PeriodoCerradoNoCambia` (fechas, estado y nombre), `aperturaEscritaDosVecesFalla`,
  `aperturaPorSistemaAntesDeSuFechaFalla`, `periodoNumeroNoCambia` (1143).
- `e24AreaEnAsignacionVigenteFalla`, `e24AreaPorOtroQueQuienProponeFalla`, `areaDeOtroNivelFalla`, `areaTransversalFalla`,
  `e25CoberturaNoCalifica` (área en asignación con `hasta`), `areaAuxiliarFalla`, `asignacionArea` sin UPDATE (1142); `a2AreaDespuesDeLaSolicitudFalla`,
  `a2AreasDistintasALasAprobadasFallan`; `b13SeparadorEnElCatalogoFalla` (como `cc_migrador`: 3819).

- `checksConNull` (los casos de la sección 3); `reemplazoDeAsignacionVigenteEnUnaTransaccion` (pasa).
- `PermisosMySqlTest`: flujo con permisos mínimos (configurar con firma, programar 4 bimestres, abrir con `cc_sistema` en su
  fecha, mover el fin con firma, cerrar el I con firma, proponer una asignación con áreas y aprobarla con la firma de otra
  persona).
- Paso `comprobar` con los casos nuevos; `huellas_objetos()` = 119; arranque prod por TLS con 104 y 15.
- Job `respaldo`: una `asignacion_area` borrada como DBA en el colegio A marca solo a A.

## 9. Riesgos
- **D12 sin confirmar:** si el colegio usa trimestres o la plantilla del SIAGIE pide otra cosa, cambia la configuración o el
  perfil, no el modelo. Pedir a la Dirección del Virgen María, antes del 11 de enero, su calendarización 2027 y una
  plantilla de calificaciones descargada del SIAGIE.
- **Textos del catálogo:** salen de los anexos de la RVM 094-2020 (que transcriben el CNEB 2016). Un error de tipeo publicado
  en V35 solo se corrige con una versión nueva para el año siguiente: revisar la semilla contra la sección 3.2 **antes del
  merge**, con una prueba que compare los 75 nombres.
- **Actualización del CNEB** (RM 393-2026, comisión hasta dic-2027): la versión nueva llegará como otra migración de solo
  inserción; nada de esta tanda la impide.
- **Reemplazo de la asignación vigente** en una transacción: el orden importa (terminar la anterior antes de dejar VIGENTE la
  nueva) por los UNIQUE de vigente y de tutor; si el orden de Hibernate no es el esperado, `flush()` explícito entre los dos.
- **H2 frente a MySQL:** los triggers solo existen en MySQL; cada control de la base necesita su prueba en el job `mysql`. La
  huella del catálogo se calcula en Java sobre las filas leídas: debe dar lo mismo en las dos bases (prueba en ambas).
- **Un solo director:** «otra persona» que aprueba las áreas puede no existir; vale el aviso A8 del sprint 9.
- **Docentes del sprint 9 sin áreas:** hasta que Dirección re-proponga sus asignaciones, no califican (la pantalla lo dice).

## 10. Terminado cuando
El catálogo `CNEB-2016.1` está cargado con 29 áreas, 75 competencias y 12 valores de escala, y su huella detiene el arranque
de prod si alguien lo altera; nadie lo puede escribir con la clave de `cc_app` ni de `cc_sistema`; Dirección configura el año
y programa sus bimestres con su firma; un periodo abre solo en su fecha y se cierra solo por Dirección desde 7 días antes del
fin, sin reabrirse; una asignación aprobada por otra persona dice qué áreas califica ese docente y nadie le agrega áreas
después; y el CI del PR en borrador está en verde (104 triggers, 15 funciones, 119 huellas).
