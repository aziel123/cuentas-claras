# Sprint 9 · Tanda 3 · Comunicados con confirmación de lectura, PDF saneado y nómina para el SIAGIE (brief)

> Léase primero. Diseño completo: `docs/arquitectura/sprint-9-asistencia-comunicados.md` (secciones **3.8 a 3.11**, 3.13,
> 3.14, 6 «V34», 7.2 y 7.3 «Tanda 3», el bloque de `trg_mensaje_nace` de 7.3, 7.4, 7.5, 8, 9 «comunicados», 10, 11, 12,
> 13.2, E33, E34, E36, E42 a E45 y E48, 14 «Tanda 3» y 15). Rama `claude/sprint-9-asistencia-comunicados`, PR en borrador:
> **la tanda está terminada cuando el CI del PR está en verde.** Parte de `104021a` (tanda 2 cerrada: V33, 93 y 106).

## 1. Decisiones del usuario y contexto
1. **PDFBox aprobado** (decisión 157, 21-oct-2026), con los controles de la sección 3.9 y de A9, A10 y A14 (sección 6).
2. **La asistencia la toma el DOCENTE** (decisión 144); el auxiliar marca, pero **no envía comunicados** (decisión 140).
   Dirección no marca, tampoco en la demo: el paso guiado «Marca la asistencia de 3.° B» usa la cuenta `xxxx.docente`.
3. Decisiones 154 a 156 y 158 a 168: se aplican con su valor por defecto (sección 16) mientras el usuario no diga otra cosa.
   D12 (plantilla real del SIAGIE): si no llegó, sale el perfil `SIAGIE_MATRICULA_PROVISIONAL`.

## 2. Alcance
- V34 (sección 3); `02` y `03` de la tanda 3, con los ajustes de la sección 4.
- **Comunicados** (3.8): Promotoría y Dirección a colegio, nivel, grado o sección; docente (tutor o docente, nunca auxiliar)
  solo a sus secciones, sin urgente. Emisor y etiqueta desde la sesión (`EtiquetaEmisor`), `FiltroContenido` sin excepciones
  en título, texto y texto del PDF, pie fijo, envío con firma, destinatarios congelados, fe de erratas, retiro con firma.
- **PDF adjunto** (3.9): `AdjuntosComunicado` con todos los controles de la sección 6; el PDF de un docente queda
  `POR_APROBAR` y lo aprueba Dirección o Promotoría por la bandeja (`PUBLICAR_COMUNICADO`).
- **Portal** (3.10): `/familia/comunicados`, abrir (marca `abierto_en`) y «Confirmo que lo leí» con su sesión; estadísticas
  para el emisor y Dirección (pendientes con el nombre del alumno, nunca el contacto).
- **Avisos** (`AvisosComunicados`, `sistema.comunicados`): resumen diario al contacto principal con tope mensual, correo a los
  demás, urgentes al momento (07:00–20:00), recordatorio a las 48 h; `cc_comunicados_v2` y `cc_comunicado_urgente_v2`;
  consumo en `/panel/sistema`; la familia elige sus 2 apoderados de asistencia y el contacto principal (desviación 7, T2).
- **SIAGIE** (3.11): `alumno.sexo` y `codigo_estudiante` (ficha e importación), códigos modulares, «Datos que faltan», perfil
  provisional y `.xlsx` por sección con `EscritorXlsxSeguro`; solo Dirección.
- Demo (3.14), plantillas de Meta (28 → 30), manuales y `estado-del-proyecto.md`. **Fuera de alcance:** antivirus,
  citaciones a una familia, llenar el `.xls` del SIAGIE, comparar con la nómina aprobada y editar un comunicado enviado.

## 3. Migración `V34__comunicados_y_nomina_siagie.sql`
- SQL en la sección 6 del diseño: `alumno.sexo` y `codigo_estudiante` con sus CHECK y `uk_alumno_codigo_estudiante`;
  `codigo_modular`, `comunicado_adjunto` (`MEDIUMBLOB`), `comunicado`, `comunicado_destinatario`, `confirmacion_lectura`,
  `exportacion_nomina`; `ck_mensaje_tipo` con `COMUNICADOS_RESUMEN` y `COMUNICADO_URGENTE`.
- **`ck_mensaje_tipo`: copiar la lista de V33 tal cual y agregar los 2 tipos** (no la de la sección 6 a mano). No duplicar
  `ix_mensaje_tipo_fecha`.
- **Lección de V32 (un CHECK que evalúa NULL pasa), ajustes obligatorios al SQL del diseño:**
  `ck_comunicado_alcance` con `nivel IS NOT NULL AND nivel IN (…)` en NIVEL y, en GRADO, la lista de `ck_seccion_grado` (V5);
  `ck_comunicado_estado` con `motivo_retiro IS NOT NULL` en RETIRADO; una prueba con el NULL de cada uno (`nivel`, `grado`,
  `motivo_retiro`, `corrige_id`, `enviado_en`).
- **Comprobar en H2 2.4.240 MODE=MySQL y en MySQL 8.4:** varios `NULL` en `uk_alumno_codigo_estudiante`; `MEDIUMBLOB` de
  2 MB (con `max_allowed_packet` del servidor); la FK compuesta de `confirmacion_lectura` a `(comunicado_id, familia_id)`
  (la sostiene `uk_comunicado_destinatario`); la FK de `comunicado` consigo misma; `REGEXP_LIKE(…, 'c')`; `ADD COLUMN` y
  `ADD CONSTRAINT` sobre `alumno` con filas existentes.
- Entidades: `ComunicadoAdjunto`, `ConfirmacionLectura` y `ExportacionNomina` de solo inserción (`updatable = false`,
  `@PreUpdate` y `@PreRemove` que fallan); en `Comunicado`, solo las columnas del GRANT son actualizables; `@TenantId` en todas.

## 4. MySQL: permisos, triggers y huellas
- `01` sin cambios, sin `04-una-vez`. `02`: bloque «Sprint 9 · tanda 3» (7.2); **título, texto, alcance, emisor, urgente,
  `requiere_confirmacion`, `corrige_id`, `adjunto_id`, `sesion_id` y `huella_contenido` fuera del UPDATE** (1143); `alumno`
  sin cambios. Antes de tocar un GRANT, buscar todo lugar que escriba esa tabla (sprint 7).
- `03-triggers.sql` (sección 7.3 «Tanda 3»), con **los ajustes de esta tanda** (no cambian el conteo):
  - `trg_comunicado_nace`: **la firma vale también para `POR_APROBAR`** (el docente envía su PDF con su sesión, no solo
    Dirección); **el adjunto lo subió quien envía** (`comunicado_adjunto.creado_por = NEW.creado_por`, del mismo colegio) y
    **título y texto pasan por la expresión de cobranza** de `trg_mensaje_nace` (segunda capa; `FiltroContenido` es más
    estricto y sigue siendo la primera);
  - `trg_comunicado_estado`: además, **un retiro no se reescribe** (`retirado_por`, `retirado_en` y `motivo_retiro` no cambian
    si ya tenían valor); el resto, como en el diseño;
  - `trg_comunicado_destinatario_nace` y `trg_confirmacion_lectura_nace`, como en el diseño.
- **Versión nueva de `trg_mensaje_nace`** (revisar la regla, no copiarla del diseño): partir del bloque REAL de la tanda 2
  (`IF NEW.tipo IN ('ASISTENCIA_DIA', 'ASISTENCIA_CORREGIDA')`, con el respaldo de `sistema.mensajeria` y `respaldo_de_id`) y:
  - sumar `COMUNICADOS_RESUMEN` y `COMUNICADO_URGENTE`: solo `sistema.comunicados` (o el respaldo de `sistema.mensajeria`),
    a un APODERADO, con plantilla `cc\_comunicado%`; consentimiento con finalidad `COMUNICADOS`
    (`IF(NEW.tipo LIKE 'ASISTENCIA%', 'ASISTENCIA', 'COMUNICADOS')`);
  - **la expresión de cobranza corre igual sobre los parámetros de los comunicados**; copiarla del `03` actual, con `\\b`
    (desviación 5 de la tanda 2: MySQL guarda una barra; la huella procesa las dos);
  - el `ELSEIF` cubre `cc\_asistencia%` **y** `cc\_comunicado%` (una plantilla académica no sirve para un aviso de cobranza);
    mensaje: «un aviso de asistencia o de comunicados no habla de cobranza».
  - **El pie fijo NO va en los parámetros:** dice «pagos», y la regla rechazaría todos los correos. Va en la plantilla
    (`PlantillaMensaje` y `messages.properties`). Los títulos solo van en el urgente y en el correo del resumen, y ya
    pasaron por `FiltroContenido`.
- **Conteo esperado:** 93 → **97 triggers** (4 nuevos; `trg_mensaje_nace` cambia de huella, no de número); funciones
  **13** sin cambios (10 de `03` + 3 de `02`); huellas 106 → **110**. Prod: «Huellas de los 97 triggers y las 13 funciones
  verificadas». Encabezado de `03`: «Versión de la migración V34 (sprint 9, tanda 3): 97 triggers y 10 funciones (13 con
  huella; 110 huellas)» y «Versión anterior (V33): 93 triggers y 10 funciones».
- `VerificadorPermisosBaseDatos` (7.4): `TRIGGERS_ESPERADOS` con los 4; 1142 (`UPDATE` de `comunicado_adjunto`,
  `confirmacion_lectura` y `exportacion_nomina`; `DELETE FROM comunicado`); 1143 (`comunicado` `titulo`, `cuerpo`,
  `adjunto_id` y `huella_contenido`; `comunicado_destinatario.familia_id`); 1644 (`comunicado` `DIRECCION` del colegio 0;
  con `cc_sistema`, un `COMUNICADOS_RESUMEN` con «S/ 350.00»). `VerificadorConfiguracion`: `adjunto.max-bytes` ≤
  `spring.servlet.multipart.max-file-size` (5 MB).

## 5. Segregación de funciones
- **Quien escribe no aprueba:** el PDF de un docente lo aprueba otra persona de Dirección o Promotoría con su firma
  (`cc_solicitud_firmada` ya exige `resuelto_por <> solicitado_por`; vale también para un director que envía como docente).
- **Nadie se hace pasar por Dirección:** emisor, rol y etiqueta salen de la sesión y de `usuario_rol` (E16); con la clave de
  `cc_app` no se envía sin la sesión y la firma del emisor (E17).
- **Quien envía no confirma:** solo un apoderado de la familia destinataria, con su sesión (E23, E24); el personal no carga
  confirmaciones ni hay enlace que confirme (E25).
- **Retirar no borra:** solo Dirección o Promotoría, con firma y motivo; el docente no retira (E28).
- **Nómina solo Dirección** (E34); Administración edita sexo y código con el antes y el después en la bitácora.
- **INDECOPI:** `comunicados` no depende de cobranza; una familia con deuda ve y confirma igual (E39); ningún comunicado va a
  una sola familia (E29). DOCENTE sigue sin combinarse con CAJA ni ADMINISTRACION (tanda 1).

## 6. PDFBox: controles obligatorios (decisión 157, A9, A10 y A14)
`comunicados.service.AdjuntosComunicado` es la única clase que usa `org.apache.pdfbox` (ArchUnit).
1. **Versión 3.0.8** (`pdfbox.version`; **nunca anterior a la 3.0.7**, CVE-2026-23907), en el SBOM y aprobada por el job
   **Dependencias** (OSV-Scanner sin CRÍTICA ni ALTA con arreglo); Dependabot la sigue; `VersionPdfBoxTest` exige ≥ 3.0.7.
2. **Antes de abrir:** tamaño ≤ 2 MB (`cuentasclaras.comunicados.adjunto.max-bytes`); **bytes mágicos** `%PDF-` en el
   byte 0; rechazo de **políglotos** (cabecera ZIP «PK» 03 04, `<html`, `<script` o `<svg` en el primer kilobyte).
3. **Al abrir:** `MemoryUsageSetting.setupMixed(50 MB, 100 MB)` (**los dos topes**, A9); temporales en `/tmp`, que en
   `compose.prod.yml` y `compose.demo.yml` es **tmpfs con `size=256m`** (subir a `384m` si la prueba lo pide);
   **≤ 10 páginas**; dañado, cifrado o con contraseña: rechazo.
4. **Tiempo y concurrencia (A10):** todo el trabajo con PDFBox (abrir, revisar, re-guardar, extraer texto) en un
   **executor dedicado** (`ThreadPoolExecutor` de 1 o 2 hilos, `ArrayBlockingQueue(4)`, `AbortPolicy`, hilos con nombre
   `pdf-*`), con `Future.get(10, SECONDS)` y `cancel(true)`. Cola llena: «Estamos revisando otros archivos; intente en unos
   minutos», sin abrir el PDF. **Límite de 10 subidas por usuario y hora** (`LimiteSubidasPdf`, en memoria por colegio y
   usuario, como `LimiteIngresos`; cuenta todo intento).
5. **Lista de rechazo completa** (rechazo, no limpieza): JavaScript en `/Names/JavaScript` y en cualquier acción;
   `OpenAction`; `AA` del catálogo, de las páginas y de las anotaciones; anotaciones con `/A` o `/AA`; acciones `Launch`,
   `SubmitForm`, `ImportData`, `ResetForm`, `Hide`, `Named`, `GoToR`, `GoToE`, `Rendition`, `Sound`, `Movie` y
   `RichMedia`; archivos embebidos (`EmbeddedFiles` y anotaciones `FileAttachment`); `AcroForm` y XFA; **`URI` solo https a
   una lista permitida** (host de `CC_URL_PUBLICA` y `dominios-permitidos`). Se recorre con el modelo de PDFBox, nunca
   buscando en los bytes (flujos de objetos y nombres escapados como `/J#61vaScript`).
6. **Re-guardado completo:** sin diccionario `Info` (trailer), sin XMP (`/Metadata` del catálogo y de cada página) y **sin
   revisiones incrementales** (`save` completo: un solo `%%EOF`); el saneado se vuelve a abrir y a revisar.
   **SHA-256** del original y del saneado; solo se guarda y se sirve el saneado. El texto (`PDFTextStripper`) pasa por
   `FiltroContenido`.
7. **Descarga** (personal, portal y bandeja): `Content-Type: application/pdf`, **`Content-Disposition: attachment;
   filename="comunicado-{id}.pdf"`** (nunca el nombre original), **`X-Content-Type-Options: nosniff`**,
   `Content-Security-Policy: sandbox; default-src 'none'`, `Cache-Control: private, no-store`. Demo: solo el PDF de ejemplo.

## 7. Archivos que toca
- **Base:** `V34__comunicados_y_nomina_siagie.sql`, `02`, `03` (encabezado) y `tablas-vigiladas.txt` (+`comunicado`,
  `comunicado_adjunto`, `comunicado_destinatario`, `confirmacion_lectura` y `exportacion_nomina`: ninguna tiene DELETE).
- **`pom.xml`** (`pdfbox` 3.0.8). **`comunicados`** (nuevo): `model`, repositorios sin `delete*`, `service` (`FiltroContenido`,
  `AdjuntosComunicado`, `LimiteSubidasPdf`, `EnvioComunicados`, `ManejadorPublicarComunicado`, `EtiquetaEmisor`,
  `LecturaFamilia`, `EstadisticasComunicado`, `IntegridadComunicados`), `api.ComunicadosPorAvisar`, `dto` y `web`.
- **`comunicacion`:** `TipoMensaje` (+2), `PlantillaMensaje` (+2, con el pie), `proceso.AvisosComunicados`, `ConsumoMensajes`.
  **`academico`:** `CodigoModular`, `ExportacionNomina`, `siagie.*` (`NominaSiagie`, `PerfilNomina`, `CampoNomina`, el JSON
  del perfil provisional) y `SiagieController`.
- **Transversales:** `TipoSolicitud.PUBLICAR_COMUNICADO`, `ActorSistema.COMUNICADOS`, `AccionAuditoria` (sección 12),
  `ClaveFirma` (`comunicado:{huella40}:ENVIAR` y `comunicado:{id}:RETIRADO`), `ModuloApp` (`SIAGIE` antes de `ACADEMICO`;
  `COMUNICADOS` disponible), `alumnos` (ficha e importación), `privacidad` (`NOMINA_SIAGIE`, alerta de más de 10
  secciones), los dos verificadores, `ContenidoSinCobranza` y `demo` (3.14).
- **Docs y CI:** plantillas Thymeleaf (skill `sistema-diseno`), `plantillas-whatsapp-meta.md` (30), manuales, despliegue con
  **97 y 13**, `estado-del-proyecto.md`, «Tanda 3 · Implementación» del diseño y `ci.yml` (paso «Sprint 9, tanda 3», V1–V34).

## 8. Pruebas obligatorias
**H2 (`./mvnw -B verify`, también con `-DargLine=-Duser.timezone=America/Los_Angeles`):**
- `FiltroContenidoTest` (30 casos: tildes, `p3nsión`, «d e u d a», «S/.», celulares, cuentas, CCI, enlaces). `ComunicadosTest`:
  `elDocenteNoEscribeComoDireccion`, `seccionAjenaResponde404`, `elAuxiliarNoEnvia`, `cobranzaSeRechazaYQuedaEnLaBitacora`,
  `pdfDeDocenteQuedaPorAprobar`, `todoComunicadoLlevaElPie`, `laFeDeErratasNoTocaElOriginal`, `retirarNoBorra`,
  `elMismoContenidoNoSaleDosVeces`. `IntegridadComunicadosTest.unTextoCambiadoPorElDbaSeDetecta` (E19).
- **`AdjuntoComunicadoTest`** (10 hostiles en `src/test/resources/hostiles/pdf/`; los de más de 2 MB y 5,000 páginas se
  generan en la prueba): **malformado**, **enorme** (bytes, páginas y bomba de objetos o de Flate), **con JavaScript**,
  `OpenAction`, `Launch`, embebido, XFA, cifrado, políglotos PDF+HTML y PDF+ZIP, extensión falsa; los de A14 (`/AA` en
  anotación, `Rendition`, `Sound`, `Movie`, `Named`, `ResetForm`, `Hide`, URI ajena); `elSaneadoNoTieneInfoNiXmpNiRevisiones`,
  `guardaLosDosSha256`, `elTextoDelPdfPasaPorElFiltro`.
- **`AdjuntoComunicadoCargaTest.diezBombasSeguidasNoDejanSinServicio`** (A10: 10 bombas seguidas; las páginas responden en
  menos de 2 s y ninguna subida pasa de 10 s), `colaLlenaPideIntentarMasTarde`, `laOnceavaSubidaDeLaHoraSeRechaza`.
  `AdjuntoComunicadoWebTest.cabecerasDeDescarga` (las 5, en las 3 rutas), `VersionPdfBoxTest`, `TmpfsConTamanoTest`.
- `PortalComunicadosTest` (`otraFamiliaResponde404`, `confirmarExigeSesionYCsrf`, `retiradoMuestraLaConstancia`);
  `AvisosComunicadosTest` (`diezComunicadosUnSoloResumen`, `pasadoElTopeVaPorCorreo`, `recordatorioALas48Horas`,
  `urgenteFueraDeHorarioSaleALas7`, `elPieNoVaEnLosParametros`); `AsistenciaSinDeudaTest` con comunicados (E39).
- `NominaSiagieTest` (`soloDireccionExporta`, `cadaDescargaQuedaRegistrada`, `elDniConservaSusCeros`, `sinFormulas`),
  `RegistroAccesosAcademicoTest` (`NOMINA_SIAGIE`), `DemoQaSprint9Test` (`otroPdfSeRechaza`, `laNominaDiceDemo`).
- `ReglasArquitecturaTest` (PDFBox solo en `AdjuntosComunicado`; `comunicados` sin cobranza; `AvisosComunicados` solo lee
  `comunicados.api`), `DtoDocenteSinDatosTest`, `ContenidoSinCobranzaTest` (bloque de comunicados), las 3 de plantillas (30),
  `LogsSinDatosPersonalesTest`, `RutasIdorTest` + `CatalogoRutasConId` (3 colegios, otra familia, docente de otra sección,
  `?seccion=` de la nómina). Conteos: **34 migraciones, 97 triggers, 110 huellas**.

**MySQL real (`mysql/AuditoriaSprint9ComunicadosMySqlTest`, job `mysql`; correr local lo tocado antes del push; 1644
salvo que se diga otra cosa):**
- E16 (rol de Dirección, al colegio 3819, urgente), E17, E18 (ajena y auxiliar), `e19ElComunicadoNoSeEdita` (1143, 3
  columnas), E20 (2), E23 (1452 y 1644), E24, E28 (sin firma y `e28RetiroNoSeReescribe`), E29 (3819), E43 (1452), E45.
- Ajustes: `e17PorAprobarSinFirmaFalla`, `e22AdjuntoDeOtraPersonaFalla`, `e21ComunicadoConDeudaFallaEnLaBase`,
  `docenteNoApruebaSuPdf`, `destinatarioNaceAbiertoFalla`, los CHECK con NULL (3819). `trg_mensaje_nace`:
  `e40ResumenConMontoFalla`, `e40RecordatorioConPlantillaDeComunicadoFalla`, `e37ComunicadosSinConsentimientoFalla` y un
  resumen legítimo que pasa.
- `PermisosMySqlTest.flujoComunicadosConPermisosMinimos` (Dirección envía, docente con PDF aprobado, fe de erratas, retiro,
  abrir y confirmar, resumen con `cc_sistema`, código modular, nómina). Paso `comprobar` (7.4); `huellas_objetos()` = 110;
  prod por TLS con 97 y 13; jobs `demo` (otro PDF se rechaza) y `dependencias` (PDFBox sin CRÍTICA ni ALTA).

## 9. Riesgos
- **PDF sin antivirus** (fallo de PDFBox o del visor: Dependabot y OSV-Scanner). **`cancel(true)` no detiene un análisis
  que ignora la interrupción:** el hilo sigue y la cola se llena antes; lo acotan los topes y lo mide la prueba de 10 bombas.
- **Límite de subidas en memoria:** se reinicia con la aplicación (una instancia). **El filtro se burla con creatividad** y
  un PDF de imagen de Dirección no se revisa: lo mitigan el pie fijo, la copia a Dirección y el retiro.
- **Meta puede clasificar `cc_comunicados_v2` como marketing** (decisión 158). **Un nombre de colegio con palabra de
  cobranza** deja sus avisos sin crear (`trg_mensaje_nace`).
- **`abierto_en`, `avisado_mensaje_id` y `recordado_mensaje_id` los vigila la aplicación** (residual aceptado, sección 15).
- **SIAGIE sin confirmar** (D12): perfil provisional. **H2 frente a MySQL:** cada control de la base, prueba en `mysql`.
  **Los ajustes de esta tanda** (secciones 3 y 4) los revisa `auditor-seguridad-antifraude` en papel antes de codear.

## 10. Terminado cuando
Dirección envía un comunicado y ve cuántas familias lo confirmaron; uno de un docente que menciona una deuda o un Yape no
sale, ni desde la aplicación ni con la clave de `cc_app`; un PDF con JavaScript se rechaza y diez bombas seguidas no dejan
la aplicación sin servicio; el PDF se descarga como adjunto con `nosniff`; la nómina de una sección sale en el perfil, con el
código impreso y solo para Dirección; la base tiene 97 triggers y 110 huellas; y el CI del PR en borrador está en verde.
