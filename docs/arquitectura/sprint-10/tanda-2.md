# Sprint 10 · Tanda 2 · Registro de notas, cierre del periodo y corrección aprobada (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-10-notas-libretas-siagie.md` (secciones **3.13** (auditoría en
> papel, prevalece), 3.5, 3.6, 4, 5 «V36», 6.2 y 6.3 «Tanda 2», 6.4, 6.5, 7, 8, 9.1 y **«Tanda 1 · Implementación»**). Rama
> `claude/sprint-10-notas-libretas-siagie`, PR en borrador #20: **la tanda está terminada cuando el CI del PR está en verde.**
> Parte de ac63f1a (tanda 1 en verde: V35, 104 triggers, 15 funciones, 119 huellas).

## 1. Decisiones y desviaciones que ya rigen
1. **Desviaciones de la tanda 1 que esta tanda respeta:** el nivel es el enum `colegio.model.Nivel` (no hay
   `NivelEducativo`); Caja no ve nada académico; el trigger de periodo no deja cambiar fechas y estado en el mismo UPDATE;
   `SeccionesDocente.areasDe(seccion)` **no** devuelve las transversales al tutor: **esta tanda las agrega** (desviación 8),
   con la misma regla que `cc_docente_de_area`.
2. **La nota es de solo inserción, en cadena lineal** (sección 3.5): `REGISTRO`, `CAMBIO_ABIERTO`, `REGISTRO_TARDIO` y
   `CORRECCION_APROBADA`. Con el periodo CERRADO, nada cambia sin una solicitud aprobada por otra persona (decisión 173).
3. **C1 (crítico):** la matrícula vale si está **ACTIVA o RESERVADA**. La RESERVADA (impaga) recibe nota igual que las demás,
   en el trigger y en la aplicación (hoja de notas, avance, cierre). Ningún filtro de la tanda mira el estado de pago.
4. **A5:** toda corrección aprobada que **suba el nivel** (C a B, B a A, A a AD, o desde «sin nota» a cualquier nivel) o que
   **deje la competencia sin nota** alerta a Promotoría, sin umbral (decisión 186 revisada). Sigue también la de más de 5
   correcciones aprobadas por una persona en un periodo.
5. **M8:** la conclusión propuesta en una corrección va en **`borrador_conclusion`**, nunca en `solicitud_cambio.datos` ni en
   la bitácora. `datos` lleva `nivel`, `borrador_id` y `conclusion_sha256`.
6. **A3 (la parte de esta tanda):** una exoneración rige **desde el periodo ABIERTO** en que se registra
   (`exoneracion_area.desde_periodo_id`); las notas de periodos anteriores quedan. Decisión 190.
7. **M7 (ya en la base desde la tanda 1):** la pantalla de cierre no ofrece adelantar el fin ni cambiar los días de cierre.

## 2. Alcance
- V36 (sección 3 de este brief). Hoja de notas del docente por sección, área y periodo (celular y escritorio): alumnos en
  filas (solo nombres, decisión 142), competencias en columnas, un toque por nivel, conclusión cuando la escala o el colegio
  la exigen, «sin nota en este periodo» para quitar una nota con el periodo ABIERTO; **un POST, un lote, una firma**.
- Vista de la sección (tutor y Dirección), avance de registro (Dirección y Promotoría sin alumnos), alerta
  `NOTAS_PENDIENTES` a Dirección 3 días antes del fin.
- **Cierre con resumen:** la pantalla de cierre de la tanda 1 gana, por sección y área, las competencias asignadas sin nota y
  las **asignaciones sin áreas del catálogo** de ese año (sección 4), y pide confirmar.
- Corrección después del cierre (`CORRECCION_CALIFICACION`) con su borrador, y nota tardía (`CALIFICACION_TARDIA`, 48 h),
  por la bandeja de siempre; manejadores que insertan la fila en la transacción de la aprobación.
- Exoneraciones (Dirección, con firma y el código del escrito del padre).
- «Correcciones de notas del periodo» para Dirección; alertas `CORRECCIONES_NOTAS` (A5) a Promotoría.
- `respaldar.sh` y `tablas-vigiladas.txt` con `lote_calificacion`, `calificacion`, `exoneracion_area` y `borrador_conclusion`.
- **Fuera de alcance:** libretas, PDF, avisos a las familias, entrega, verificación y SIAGIE (tanda 3). Una corrección
  aprobada en esta tanda llega a la familia en la libreta de la tanda 3 (sección 8, riesgo 1).

## 3. Migración `V36__calificaciones_y_cierre.sql`
- SQL de la sección 5 del diseño (`lote_calificacion`, `calificacion`, `exoneracion_area`) **con los cambios de la 3.13**:
  `exoneracion_area.desde_periodo_id BIGINT NOT NULL` con `fk_exoneracion_area_periodo (desde_periodo_id, colegio_id)` **en el
  CREATE TABLE**, y la tabla nueva `borrador_conclusion` (solo inserción: `calificacion_id`, `texto` hasta 1000, `sha256`,
  `sesion_id`, `creado_por`).
- CHECK de `calificacion` (sección 5): orígenes con su `reemplaza_id`, `inicial`, `lote_id` y `solicitud_id`; nivel NULL solo
  en `CAMBIO_ABIERTO` o `CORRECCION_APROBADA` y entonces sin conclusión; conclusión de al menos 10 caracteres con su
  `conclusion_sha256` de 64 hexadecimales.
- `TipoSolicitud` +2 (`CORRECCION_CALIFICACION`, `CALIFICACION_TARDIA`): sin migración (no hay CHECK de tipo).
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4:** la FK de `calificacion` consigo misma (`reemplaza_id, colegio_id`);
  el UNIQUE con `inicial` NULL (varias sucesoras de una inicial no chocan; dos iniciales sí); la FK de `borrador_conclusion`
  a `calificacion`; `VARCHAR(1000)` con `utf8mb4` dentro del límite de fila (calificacion tiene dos columnas largas).
- Lección de V32 a V35: un CHECK que evalúa NULL pasa. Un caso NULL por CHECK: `nivel_logro`, `inicial`, `reemplaza_id`,
  `lote_id`, `solicitud_id`, `conclusion`, `conclusion_sha256`.
- Entidades de solo inserción con `updatable = false` y `@PreUpdate`/`@PreRemove` que lanzan (como la tanda 1);
  `NotaPedida` **sin** `origen`, `creado_por`, `lote_id` ni `conclusion_sha256` (la huella la calcula el servicio y la base).

## 4. Docentes del sprint 9 sin áreas del catálogo (riesgo de la tanda 1)
Siguen sin calificar hasta que su asignación se vuelva a proponer con áreas **y la apruebe otra persona**: dar el poder de
poner notas lo deciden dos personas y no se abre un atajo. Para que no quede nadie fuera sin que Dirección lo sepa:
1. **«Completar áreas»** en «Asignaciones sin áreas del catálogo»: un botón por asignación (y «Completar todas las de esta
   sección») que **arma la propuesta de reemplazo ya llenada**: mismas claves (sección, docente, tipo) y las áreas del
   catálogo **sugeridas** desde el texto libre de V32 (coincidencia por nombre sin tildes: «Matemática, Ciencia y Tecnología»
   → MAT y CYT); quien propone las revisa y confirma. Es la misma `Asignaciones.proponer` de la tanda 1: áreas antes de la
   solicitud (A2) y reemplazo de la vigente al aprobar.
2. **La bandeja permite aprobarlas seguidas**, cada una con su firma (como en el sprint 9).
3. **Alerta `ASIGNACIONES_SIN_AREAS` a Dirección** (`TipoAviso` nuevo, sin plantilla nueva): al abrir un periodo y 7 días
   antes de su fin, con el número y los nombres de las secciones (sin alumnos); también en la lista de puesta en marcha.
4. **El docente lo ve:** en su hoja de notas, «Tu asignación en 3.° B todavía no tiene áreas del catálogo. Pide a Dirección
   que la complete»; sin botón de guardar.
5. **El cierre lo muestra** (sección 2) y no lo bloquea: lo que no se registró va por nota tardía.
No hay cambios de base: la tanda 1 ya exige las áreas en la propuesta y su comparación al aprobar.

## 5. MySQL: permisos, triggers y huellas
- `01-usuarios.sql`: sin cambios. Sin `04-una-vez`.
- `02-permisos-tablas.sql`, bloque «Sprint 10 · tanda 2»: solo `INSERT` en `lote_calificacion`, `calificacion`,
  `exoneracion_area` y `borrador_conclusion` para `cc_negocio`; ningún UPDATE ni DELETE.
- `03-triggers.sql` (sección 6.3 «Tanda 2» del diseño, con la 3.13). **Comparar siempre con `<=>`**:
  - función nueva **`cc_docente_de_area(nombre, seccion, area)`**: asignación VIGENTE de TUTOR o DOCENTE sin `hasta`, cuenta
    activa con rol DOCENTE y sin apoderado, y el área asignada, o una transversal si es el TUTOR;
  - **`trg_lote_calificacion_nace`**: sección del año del periodo; área de la versión y del nivel de la sección;
    `cc_docente_de_area`, `cc_sesion_abierta` y la firma `lote_calificacion:{periodo}:{seccion}:{area}:{secuencia}`;
    secuencia siguiente; periodo ABIERTO con inicio cumplido y sin solicitud, o CERRADO con su `CALIFICACION_TARDIA`
    firmada, pedida por el mismo docente, resuelta hace 48 h o menos y del mismo periodo y asignación de área;
    `registrado_bd` de la base;
  - **`trg_calificacion_nace`**: matrícula del alumno, **ACTIVA o RESERVADA** (C1) y en la sección; competencia de la versión,
    del nivel y del grado (`solo_grados`); sin exoneración del área **cuyo `desde_periodo` tenga número menor o igual** al de
    la nota (A3); conclusión exigida por `escala_logro` o `conclusion_siempre`, de hasta `conclusion_max`, y
    `conclusion_sha256 = SHA2(conclusion, 256)`; cadena (la reemplazada es la vigente del mismo periodo, matrícula y
    competencia, y algo cambia); por origen, las reglas de la sección 6.3; en `CORRECCION_APROBADA`, solicitud firmada,
    aprobador DIRECTOR o PROMOTOR activo **distinto de quien pidió y de quien registró la reemplazada**, nivel y huella iguales
    a `datos` y, con `borrador_id`, **`NEW.conclusion` igual al texto del borrador de esa misma calificación** (M8);
  - **`trg_exoneracion_area_nace`**: área exonerable, de la versión y del nivel; matrícula ACTIVA o RESERVADA en el año;
    `desde_periodo_id` ABIERTO; DIRECTOR con la firma `exoneracion_area:{matricula}:{area}:AUTORIZADA`;
  - **`trg_borrador_conclusion_nace`** (M8): `creado_por` es docente del área de esa calificación en su sección
    (`cc_docente_de_area`) o DIRECTOR (`cc_rol_activo`), con `cc_sesion_abierta`; el periodo de la calificación está CERRADO;
    `SET NEW.sha256 = SHA2(NEW.texto, 256)`.
  - Encabezado: «Versión anterior (V35, sprint 10, tanda 1): 104 triggers y 12 funciones (15 con huella; 119 huellas)».
- **Conteo esperado:** 104 → **108 triggers** (4 nuevos); funciones con huella 15 → **16** (13 de `03` + 3 de `02`);
  huellas 119 → **124**. Prod: «Huellas de los 108 triggers y las 16 funciones verificadas». Tanda 3: 112 y 128.
- `VerificadorPermisosBaseDatos`: `TRIGGERS_ESPERADOS` con los 4; 1142 en `UPDATE calificacion`, `DELETE FROM calificacion`,
  `UPDATE lote_calificacion`, `UPDATE exoneracion_area` y `UPDATE borrador_conclusion`; 1644 en una `calificacion` del colegio
  0 de origen `CORRECCION_APROBADA` sin solicitud firmada (responde el trigger antes que las FK). `HuellasObjetosBd` = 124.
- **Pruebas MySQL obligatorias (antes «comprobar»):** `JSON_EXTRACT` y `JSON_UNQUOTE` sobre `solicitud_cambio.datos`
  (`VARCHAR`) dentro del trigger, con `null` en el nivel; `FIND_IN_SET` con la colación de la base; `SHA2` de una conclusión
  con tildes y «ñ» igual al SHA-256 de Java (UTF-8); que `trg_calificacion_nace` lee su propia tabla (la vigente); las 48 h y
  los 10 minutos en hora de Lima de la base.

## 6. Segregación de funciones
- **Poner y cambiar notas con el periodo ABIERTO:** solo el docente del área en la sección (el tutor, en transversales).
  Dirección y Promotoría no ponen notas aunque las vean; un director que también enseña califica como docente, no como
  director. El AUXILIAR y una cobertura, nunca.
- **Cerrar:** solo Dirección con su firma (tanda 1).
- **Corregir después del cierre:** pide el docente del área o Dirección; aprueba **otra** persona de Dirección o Promotoría,
  que no pidió ni registró la nota reemplazada. **Nota tardía:** pide el docente del área; aprueba otra persona.
- **Exonerar:** solo Dirección con su firma. **Leer un borrador:** quien lo escribió y quien aprueba la solicitud.
- DOCENTE no se combina con CAJA ni ADMINISTRACION (sprint 9): quien pone notas no ve quién debe. Con menos de 2 personas de
  gobierno, vale el aviso A8 del sprint 9 (la corrección queda pendiente).

## 7. Archivos que toca
- **Base:** `V36__calificaciones_y_cierre.sql`; `scripts/mysql/02-permisos-tablas.sql`; `scripts/mysql/03-triggers.sql`;
  `scripts/respaldo/tablas-vigiladas.txt` y `respaldar.sh` (las 4 tablas, por colegio).
- **`academico.notas` (nuevo):** `model` (`LoteCalificacion`, `Calificacion`, `OrigenCalificacion`, `NivelLogro`,
  `ExoneracionArea`, `BorradorConclusion`); repositorios sin `delete*`; `service` (`RegistroNotas` con `hoja` y `guardar`,
  `CorreccionesNota` con `pedir` y `pedirTardia`, `ManejadorCorreccionCalificacion`, `ManejadorCalificacionTardia`,
  `Exoneraciones`, `AvanceRegistro`, `AlertasNotas`); `dto` (`NotaPedida`, `HojaNotas` sin documentos ni contactos); `web`
  (`NotasController`, `CorreccionesNotaController`, `ExoneracionesController`).
- **`academico.curriculo` y `academico` (existen):** `Periodos.cerrar` con el resumen de pendientes y de asignaciones sin
  áreas; `SeccionesDocente.areasDe` con las transversales del tutor; «Completar áreas» y su sugerencia desde el texto de V32.
- **Transversales:** `TipoSolicitud` (+2), `TipoAviso` (`NOTAS_PENDIENTES` y `ASIGNACIONES_SIN_AREAS` a Dirección;
  `CORRECCIONES_NOTAS` a Promotoría), `AccionAuditoria` (`NOTAS_GUARDADAS`, `CORRECCION_NOTA_PEDIDA`,
  `CALIFICACION_CORREGIDA`, `CALIFICACION_TARDIA_REGISTRADA`, `AREA_EXONERADA`), `ClaveFirma` (+2: lote y exoneración),
  `TipoAcceso` (`NOTAS_SECCION`, también al leer un borrador), `VerificadorPermisosBaseDatos`, `HuellasObjetosBd`,
  `ModuloApp` (notas: DOCENTE; vista de la sección: tutor y DIRECTOR; avance: DIRECTOR y PROMOTOR; exoneraciones: DIRECTOR),
  `application.yaml` (`cuentasclaras.notas.*`).
- **Plantillas Thymeleaf** (skill `sistema-diseno`, hoja de notas pensada para el celular), `docs/manuales/docente.md` y
  `direccion.md`, sección «Tanda 2 · Implementación» del diseño, `.github/workflows/ci.yml` (paso «Sprint 10, tanda 2», V1–V36,
  124 y 108/16), `paso-0.sh`, `en-la-vm.sh`, `despliegue-gcp.md`, el acta del paso 0 y `mysql-usuarios.md`.

## 8. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `NotasTest`: el docente guarda 30 alumnos por 4 competencias en un POST con una firma; `areaAjenaResponde404` (E2);
  `direccionNoCalifica` (403); `auxiliarYCoberturaNoCalifican`; `elTutorCalificaLasTransversales`; conclusión exigida por
  nivel (E18: Inicial A, Primaria B, Secundaria C) y con `conclusion_siempre`; `sinNotaEnElPeriodo`;
  `despuesDelCierreVaPorSolicitud` (E1); `matriculaReservadaRecibeNota` (**C1**).
- `CorreccionesNotaTest`: quien pide no aprueba (E4); el autor de la nota no aprueba su corrección (E5); lo aprobado es lo que
  entra (E6); el borrador no aparece en `datos`, en la bitácora ni en los logs (**M8**, `BitacoraSinConclusionesTest`);
  `BorradorConclusionTest.soloLoVenQuienLoEscribeYQuienAprueba`; nota tardía en 48 h y solo en competencias sin nota (E23).
- `AlertasNotasTest`: `subirElNivelSiempreAlerta` y `dejarSinNotaAlerta` (**A5**); más de 5 por persona (E26);
  `NOTAS_PENDIENTES` 3 días antes; `ASIGNACIONES_SIN_AREAS` al abrir y 7 días antes, con los nombres de las secciones.
- `ExoneracionesTest`: solo Dirección con firma (E21); rige desde el periodo abierto y conserva las notas anteriores (**A3**);
  nota en área exonerada rechazada (E22).
- `CierrePeriodoTest`: el resumen lista competencias sin nota y asignaciones sin áreas; cerrar no se bloquea por ellas.
- `CompletarAreasTest`: la sugerencia desde el texto de V32; la propuesta lleva las áreas antes de la solicitud; la aprueba otra
  persona y reemplaza la vigente; sin aprobar, el docente sigue sin calificar.
- `DtoDocenteSinDatosTest` (con `HojaNotas`), `NotaPedidaSinOrigenTest`, `LogsSinDatosPersonalesTest` (conclusiones),
  `InmutabilidadAcademicoTest` (+4), `ReglasArquitecturaTest` (`academico.notas` sin cobranza; `notaPedidaSinOrigen`),
  `RutasIdorTest` y `CatalogoRutasConId` (rutas nuevas con `?periodo=`, un docente de otra área de la misma sección y los 3
  colegios), `MatrizPermisosTest`, `ClaveFirmaTest`; conteos: **36 migraciones, 108 triggers, 124 huellas**.

**MySQL real (`mysql/AuditoriaSprint10NotasMySqlTest`, job `mysql`; correr local lo tocado antes del push). 1644 salvo que se
diga otra cosa:**
- E1 `e1CambioDespuesDelCierreFalla`, E2 `e2LoteDeAreaAjenaFalla`, E3 `e3LoteSinSesionOFirmaFalla` y
  `e3NotaConOtroAutorQueElLoteFalla`, E4, E5, E6, E7 `e7NotasNoSeEditanNiSeBorran` (1142), E8 `e8DosSucesorasFallan` (1062),
  E18 (3 niveles), E19 `e19MatriculaRetiradaFalla`, E20 `e20SecuenciaSaltadaFalla`, E21, E22, E23 (3), E25, E36 (1452).
- Auditoría en papel: **`c1NotaAMatriculaReservadaPasa`**, `a3ExoneracionEnPeriodoCerradoFalla`,
  `a3NotaAnteriorALaExoneracionSeConserva`, `m8ConclusionDistintaAlBorradorFalla`, `m8BorradorDeOtroDocenteFalla`,
  `m8BorradorConPeriodoAbiertoFalla`, `checksConNull` (los casos de la sección 3).
- `flujoNotasConPermisosMinimos` (en la clase nueva, con su colegio): configurar, abrir, guardar un lote, cambiar con el
  periodo abierto, cerrar, pedir una corrección con borrador y aprobarla con otra persona, pedir y registrar una tardía,
  exonerar.
- Paso `comprobar` con los casos de la sección 5; `huellas_objetos()` = 124; arranque prod por TLS con 108 y 16.
- Job `respaldo`: una `calificacion` borrada como DBA en el colegio A marca solo a A.
- **Carga:** 40 docentes guardan a la vez un lote de 30 alumnos por 4 competencias (120 filas con su trigger): cada guardado
  en menos de 2 s en MySQL real.

## 9. Riesgos
1. **Corrección sin libreta hasta la tanda 3:** en esta tanda una corrección aprobada queda en la bitácora y en la alerta (A5),
   pero la familia recién la ve en la libreta (tanda 3). Como la emisión de la tanda 3 toma la foto al emitir, la versión 1
   ya incluye lo corregido; la bitácora conserva el «de qué a qué».
2. **Docentes del sprint 9 sin áreas** (sección 4): si Dirección no completa a tiempo, esas competencias quedan sin nota y van
   por nota tardía con aprobación (más carga; medir en el piloto).
3. **Rendimiento de `trg_calificacion_nace`** (varias subconsultas por fila y 120 filas por guardado): índices de V36 y la
   prueba de carga; si no alcanza, se agrupa el lote sin quitar controles.
4. **Un solo director:** «otra persona» puede ser solo Promotoría o nadie; la corrección queda pendiente (aviso A8).
5. **Datos sensibles en las conclusiones** (apoyos y ajustes por discapacidad): fuera de logs, bitácora y `datos`; el
   borrador se lee con registro en `acceso_dato_personal`.
6. **H2 frente a MySQL:** los triggers solo existen en MySQL; cada control de la base necesita su prueba en el job `mysql`.
   Las pruebas con fechas relativas a hoy se omiten fuera del año de clases, como en la tanda 1.
7. **Sugerencia de áreas desde el texto libre:** puede equivocarse («Comunicación» en Secundaria también podría ser Inglés);
   por eso es solo una sugerencia que la persona revisa y otra aprueba.

## 10. Terminado cuando
Un docente registra desde el celular las notas de su área por competencias, con la conclusión que exige la norma, en un
guardado firmado; un alumno con matrícula RESERVADA recibe nota igual que los demás; Dirección ve qué falta y cierra el
periodo; después del cierre ninguna nota cambia sin una solicitud aprobada por otra persona, con el texto en
`borrador_conclusion` y nunca en la bitácora; toda corrección que suba el nivel o deje sin nota alerta a Promotoría; los
docentes del sprint 9 sin áreas aparecen en la alerta y en «Completar áreas»; y el CI del PR en borrador está en verde (108
triggers, 16 funciones, 124 huellas).
